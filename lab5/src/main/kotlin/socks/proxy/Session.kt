package socks.proxy

import socks.config.ProxyConfig
import socks.dns.ResolveResult
import socks.dns.Resolver
import socks.eventloop.Cancellable
import socks.eventloop.ChannelHandler
import socks.eventloop.EventLoop
import socks.eventloop.Timer
import socks.net.ConnectResult
import socks.net.Connector
import socks.protocol.Destination
import socks.protocol.ReplyCode
import socks.util.Log
import socks.util.closeQuietly
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

internal class SessionContext(
    val loop: EventLoop,
    val config: ProxyConfig,
    val resolver: Resolver,
    val connector: Connector,
)


internal class Session(
    private val ctx: SessionContext,
    private val client: SocketChannel,
    private val onClosed: (Session) -> Unit,
) : ClientHandshake.Listener {

    private lateinit var clientKey: SelectionKey
    private lateinit var handshake: ClientHandshake
    private var remote: SocketChannel? = null
    private var timer: Timer? = null
    private var pendingOp: Cancellable? = null // текущий DNS-запрос или подключение
    private var closed = false

    // для журнала доступа
    private var clientAddr = "?"
    private var target = "?"
    private var relay: Relay? = null
    private var relayStartedNs = 0L

    @Throws(IOException::class)
    fun start() {
        client.setOption(StandardSocketOptions.TCP_NODELAY, true)
        clientAddr = (client.remoteAddress as? InetSocketAddress)
            ?.let { "${it.address.hostAddress}:${it.port}" } ?: "?"
        clientKey = ctx.loop.register(client, 0, IdleHandler)
        handshake = ClientHandshake(client, clientKey, this)
        timer = ctx.loop.schedule(ctx.config.handshakeTimeoutMs) { close() }
        handshake.start()
    }

    // ---- ClientHandshake.Listener ----

    override fun onRequest(destination: Destination) {
        timer?.cancel()
        timer = null
        target = when (destination) {
            is Destination.Ip -> "${destination.address.hostAddress}:${destination.port}"
            is Destination.Domain -> "${destination.host}:${destination.port}"
        }
        when (destination) {
            is Destination.Ip -> connect(InetSocketAddress(destination.address, destination.port))
            is Destination.Domain -> pendingOp = ctx.resolver.resolve(destination.host) { result ->
                pendingOp = null
                if (closed) return@resolve
                when (result) {
                    is ResolveResult.Resolved -> connect(InetSocketAddress(result.address, destination.port))
                    is ResolveResult.Failed -> reject(ReplyCode.HOST_UNREACHABLE, "DNS: ${result.reason}")
                }
            }
        }
    }

    override fun onFinished() = close()

    // ---- Фазы ----

    private fun connect(address: InetSocketAddress) {
        pendingOp = ctx.connector.connect(address) { result ->
            pendingOp = null
            if (closed) return@connect
            when (result) {
                is ConnectResult.Connected -> startRelay(result)
                is ConnectResult.Failed -> reject(result.code, result.reason)
            }
        }
    }

    private fun startRelay(connected: ConnectResult.Connected) {
        remote = connected.channel
        try {
            val bound = connected.channel.localAddress as? InetSocketAddress
            val peerIp = (connected.channel.remoteAddress as? InetSocketAddress)?.address?.hostAddress
            val handover = handshake.succeeded(bound)
            val r = Relay(
                client, clientKey, connected.channel, connected.key,
                ctx.config.bufferSize, handover.leftover, handover.toClient,
            ) { close() }
            relay = r
            relayStartedNs = System.nanoTime()
            Log.access("$clientAddr -> $target${peerIp?.let { " [$it]" } ?: ""} OK")
            r.start()
        } catch (_: IOException) {
            close()
        }
    }

    private fun reject(code: ReplyCode, reason: String) {
        if (closed) return
        Log.access("$clientAddr -> $target FAIL $code ($reason)")
        handshake.failed(code)
        timer = ctx.loop.schedule(ctx.config.errorReplyTimeoutMs) { close() }
    }

    fun close() {
        if (closed) return
        closed = true
        relay?.let { r ->
            val seconds = (System.nanoTime() - relayStartedNs) / 1_000_000_000
            Log.access("$clientAddr -> $target CLOSED up=${r.bytesToRemote}B down=${r.bytesToClient}B time=${seconds}s")
        }
        timer?.cancel()
        pendingOp?.cancel()
        closeQuietly(client)
        closeQuietly(remote)
        onClosed(this)
    }

    private object IdleHandler : ChannelHandler {
        override fun onReady(key: SelectionKey) {}
    }
}
