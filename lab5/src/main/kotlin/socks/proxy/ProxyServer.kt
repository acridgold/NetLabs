package socks.proxy

import socks.config.ProxyConfig
import socks.dns.UdpDnsResolver
import socks.eventloop.EventLoop
import socks.net.Connector
import socks.util.closeQuietly
import java.io.IOException
import java.nio.channels.SocketChannel


class ProxyServer(private val config: ProxyConfig) {

    private val loop = EventLoop()
    private val resolver = UdpDnsResolver(
        loop, config.dnsServer, config.dnsAttemptTimeoutMs, config.dnsMaxAttempts,
    )
    private val context = SessionContext(loop, config, resolver, Connector(loop, config.connectTimeoutMs))
    private val sessions = HashSet<Session>()


    fun run() {
        val acceptor = Acceptor(loop, config.listenPort, ::accept)
        try {
            loop.run()
        } finally {
            acceptor.close()
            sessions.toList().forEach { it.close() }
            loop.close()
        }
    }

    fun stop() = loop.stop()

    private fun accept(channel: SocketChannel) {
        val session = Session(context, channel) { sessions.remove(it) }
        sessions.add(session)
        try {
            session.start()
        } catch (_: IOException) {
            session.close()
            closeQuietly(channel)
        }
    }
}
