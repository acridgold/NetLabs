package socks.dns

import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import socks.eventloop.Cancellable
import socks.eventloop.ChannelHandler
import socks.eventloop.EventLoop
import socks.eventloop.Timer
import socks.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import kotlin.random.Random


class UdpDnsResolver(
    private val loop: EventLoop,
    private val server: InetSocketAddress,
    private val attemptTimeoutMs: Long,
    private val maxAttempts: Int,
) : Resolver, ChannelHandler {

    private val channel: DatagramChannel = DatagramChannel.open()
    private val recvBuf: ByteBuffer = ByteBuffer.allocate(4096)
    private val pending = HashMap<Int, Query>()
    private val rnd = Random.Default

    init {
        loop.register(channel, SelectionKey.OP_READ, this)
    }

    override fun resolve(host: String, onResult: (ResolveResult) -> Unit): Cancellable {
        val name = try {
            Name.fromString(host, Name.root)
        } catch (_: IOException) {
            return failLater("некорректное имя: $host", onResult)
        }
        if (pending.size >= MAX_PENDING) return failLater("слишком много DNS-запросов", onResult)

        var id: Int
        do {
            id = rnd.nextInt(65536)
        } while (pending.containsKey(id))

        val query = Query(id, name, onResult)
        pending[id] = query
        attempt(query)
        return query
    }

    private fun failLater(reason: String, cb: (ResolveResult) -> Unit): Cancellable =
        loop.schedule(0) { cb(ResolveResult.Failed(reason)) }

    private fun attempt(q: Query) {
        q.attempts++
        try {
            channel.send(ByteBuffer.wrap(DnsCodec.buildQuery(q.name, q.id)), server)
        } catch (e: IOException) {
            Log.warn("DNS: ошибка отправки: $e")
        }
        q.timer = loop.schedule(attemptTimeoutMs) { onTimeout(q) }
    }

    private fun onTimeout(q: Query) {
        if (pending[q.id] !== q) return
        if (q.attempts >= maxAttempts) {
            pending.remove(q.id)
            q.callback(ResolveResult.Failed("нет ответа после ${q.attempts} попыток"))
        } else {
            attempt(q)
        }
    }

    override fun onReady(key: SelectionKey) {
        for (i in 0 until MAX_DATAGRAMS_PER_TICK) {
            recvBuf.clear()
            channel.receive(recvBuf) ?: return
            recvBuf.flip()
            val bytes = ByteArray(recvBuf.remaining())
            recvBuf.get(bytes)

            val answer = DnsCodec.parseResponse(bytes) ?: continue
            val q = pending[answer.id] ?: continue
            if (answer.question != q.name) continue

            pending.remove(q.id)
            q.timer?.cancel()
            val address = answer.address
            if (answer.rcode == Rcode.NOERROR && address != null) {
                q.callback(ResolveResult.Resolved(address))
            } else {
                q.callback(ResolveResult.Failed("rcode=${Rcode.string(answer.rcode)}, A-записей нет"))
            }
        }
    }

    override fun onError(cause: Exception) {
        Log.warn("DNS: ошибка чтения: $cause")
    }

    private inner class Query(
        val id: Int,
        val name: Name,
        val callback: (ResolveResult) -> Unit,
    ) : Cancellable {
        var attempts = 0
        var timer: Timer? = null

        override fun cancel() {
            timer?.cancel()
            pending.remove(id)
        }
    }

    private companion object {
        const val MAX_PENDING = 60_000
        const val MAX_DATAGRAMS_PER_TICK = 64
    }
}
