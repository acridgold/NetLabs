package socks.proxy

import socks.admin.AdminServer
import socks.clients.ClientRegistry
import socks.config.ProxyConfig
import socks.dns.UdpDnsResolver
import socks.eventloop.EventLoop
import socks.net.Connector
import socks.util.Log
import socks.util.closeQuietly
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel


class ProxyServer(private val config: ProxyConfig) {

    private val loop = EventLoop()
    private val registry = ClientRegistry()
    private val resolver = UdpDnsResolver(
        loop, config.dnsServer, config.dnsAttemptTimeoutMs, config.dnsMaxAttempts,
    )
    private val context = SessionContext(
        loop, config, resolver, Connector(loop, config.connectTimeoutMs), registry,
    )
    private val sessions = HashSet<Session>()


    fun run() {
        val acceptor = Acceptor(loop, config.listenPort, ::accept)
        val admin = startAdmin()
        try {
            loop.run()
        } finally {
            acceptor.close()
            admin?.close()
            sessions.toList().forEach { it.close() }
            loop.close()
        }
    }

    fun stop() = loop.stop()

    private fun startAdmin(): AdminServer? {
        val port = config.uiPort ?: return null
        return try {
            AdminServer(loop, port, config.listenPort, registry).also { it.start() }
        } catch (e: IOException) {
            Log.warn("Админ-панель не запущена (порт $port): $e")
            null
        }
    }

    private fun accept(channel: SocketChannel) {
        val uid = try {
            (channel.remoteAddress as? InetSocketAddress)?.address?.hostAddress
        } catch (_: IOException) {
            null
        }
        if (uid == null) {
            closeQuietly(channel)
            return
        }
        if (registry.isBanned(uid)) {
            registry.onBlocked(uid)
            closeQuietly(channel)
            return
        }

        val session = Session(context, channel, uid) { sessions.remove(it) }
        sessions.add(session)
        try {
            session.start()
        } catch (_: IOException) {
            session.close()
            closeQuietly(channel)
        }
    }
}
