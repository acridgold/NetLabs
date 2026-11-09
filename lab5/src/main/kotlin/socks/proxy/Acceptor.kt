package socks.proxy

import socks.eventloop.ChannelHandler
import socks.eventloop.EventLoop
import socks.util.Log
import socks.util.closeQuietly
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel


internal class Acceptor(
    loop: EventLoop,
    port: Int,
    private val onAccept: (SocketChannel) -> Unit,
) : ChannelHandler {

    private val server: ServerSocketChannel = ServerSocketChannel.open()

    init {
        server.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        server.bind(InetSocketAddress(port), 256)
        loop.register(server, SelectionKey.OP_ACCEPT, this)
    }

    override fun onReady(key: SelectionKey) {
        while (true) {
            val ch = try {
                server.accept()
            } catch (e: IOException) {
                Log.warn("accept: $e")
                null
            } ?: return
            onAccept(ch)
        }
    }

    fun close() = closeQuietly(server)
}
