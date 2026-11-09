package socks.net

import socks.eventloop.Cancellable
import socks.eventloop.ChannelHandler
import socks.eventloop.EventLoop
import socks.eventloop.Timer
import socks.protocol.ReplyCode
import socks.util.closeQuietly
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.StandardSocketOptions
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel

sealed interface ConnectResult {

    class Connected(val channel: SocketChannel, val key: SelectionKey) : ConnectResult

    class Failed(val code: ReplyCode, val reason: String) : ConnectResult
}


class Connector(private val loop: EventLoop, private val timeoutMs: Long) {

    fun connect(address: InetSocketAddress, onResult: (ConnectResult) -> Unit): Cancellable =
        Attempt(address, onResult).also { it.start() }

    private inner class Attempt(
        private val address: InetSocketAddress,
        private val onResult: (ConnectResult) -> Unit,
    ) : ChannelHandler, Cancellable {

        private val channel: SocketChannel = SocketChannel.open()
        private var key: SelectionKey? = null
        private var timer: Timer? = null
        private var done = false

        fun start() {
            try {
                channel.configureBlocking(false)
                channel.setOption(StandardSocketOptions.TCP_NODELAY, true)
                key = loop.register(channel, SelectionKey.OP_CONNECT, this)
                timer = loop.schedule(timeoutMs) { fail(ReplyCode.HOST_UNREACHABLE, "таймаут подключения") }
                if (channel.connect(address)) loop.schedule(0) { complete() }
            } catch (e: IOException) {
                loop.schedule(0) { fail(mapError(e), e.toString()) }
            }
        }

        override fun onReady(key: SelectionKey) {
            if (!key.isConnectable) return
            try {
                if (!channel.finishConnect()) return
            } catch (e: IOException) {
                fail(mapError(e), e.toString())
                return
            }
            complete()
        }

        override fun onError(cause: Exception) = fail(ReplyCode.GENERAL_FAILURE, cause.toString())

        override fun cancel() {
            if (done) return
            done = true
            timer?.cancel()
            closeQuietly(channel)
        }

        private fun complete() {
            if (done) return
            done = true
            timer?.cancel()
            val k = key!!
            k.interestOps(0)
            onResult(ConnectResult.Connected(channel, k))
        }

        private fun fail(code: ReplyCode, reason: String) {
            if (done) return
            done = true
            timer?.cancel()
            closeQuietly(channel)
            onResult(ConnectResult.Failed(code, reason))
        }
    }

    private fun mapError(e: IOException): ReplyCode {
        val m = e.message?.lowercase() ?: ""
        return when {
            e is NoRouteToHostException -> ReplyCode.HOST_UNREACHABLE
            e is ConnectException && "refused" in m -> ReplyCode.CONNECTION_REFUSED
            "network is unreachable" in m -> ReplyCode.NET_UNREACHABLE
            "timed out" in m -> ReplyCode.HOST_UNREACHABLE
            else -> ReplyCode.GENERAL_FAILURE
        }
    }
}
