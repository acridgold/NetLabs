package socks.admin

import socks.clients.ClientRegistry
import socks.eventloop.ChannelHandler
import socks.eventloop.EventLoop
import socks.eventloop.Timer
import socks.util.Log
import socks.util.closeQuietly
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

internal class AdminServer(
    private val loop: EventLoop,
    private val port: Int,
    private val proxyPort: Int,
    private val registry: ClientRegistry,
) {
    private class Response(val status: Int, val contentType: String, val body: ByteArray)

    private val server: ServerSocketChannel = ServerSocketChannel.open()
    private val page: ByteArray = loadPage()
    private val startedMs = System.currentTimeMillis()
    private val allowedHosts = setOf("127.0.0.1:$port", "localhost:$port")

    fun start() {
        try {
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            server.bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port), 64)
            loop.register(server, SelectionKey.OP_ACCEPT, AcceptHandler())
        } catch (e: IOException) {
            closeQuietly(server)
            throw e
        }
        Log.info("Админ-панель: http://127.0.0.1:$port/")
    }

    fun close() = closeQuietly(server)

    private fun loadPage(): ByteArray =
        AdminServer::class.java.getResourceAsStream("/admin/index.html")?.use { it.readBytes() }
            ?: error("Не найден ресурс /admin/index.html")

    private inner class AcceptHandler : ChannelHandler {
        override fun onReady(key: SelectionKey) {
            while (true) {
                val ch = try {
                    server.accept()
                } catch (_: IOException) {
                    null
                } ?: return
                try {
                    HttpConnection(ch)
                } catch (_: IOException) {
                    closeQuietly(ch)
                }
            }
        }
    }

    private inner class HttpConnection(private val ch: SocketChannel) : ChannelHandler {
        private val inBuf: ByteBuffer = ByteBuffer.allocate(8192)
        private var out: ByteBuffer? = null
        private val key: SelectionKey = loop.register(ch, SelectionKey.OP_READ, this)
        private val timer: Timer = loop.schedule(10_000) { close() }

        override fun onReady(key: SelectionKey) {
            if (out == null && key.isReadable) {
                val n = ch.read(inBuf)
                if (n < 0) {
                    close()
                    return
                }
                if (n > 0) tryRespond()
            }
            flush()
        }

        override fun onError(cause: Exception) = close()

        private fun tryRespond() {
            val text = String(inBuf.array(), 0, inBuf.position(), Charsets.ISO_8859_1)
            val end = text.indexOf("\r\n\r\n")
            if (end < 0) {
                if (!inBuf.hasRemaining()) send(text(431, "Request Header Fields Too Large"))
                return
            }
            val response = try {
                handle(text.substring(0, end))
            } catch (e: Exception) {
                Log.warn("Админ-панель: ошибка обработки запроса: $e")
                text(500, "Internal Server Error")
            }
            send(response)
        }

        private fun send(r: Response) {
            out = serialize(r)
            key.interestOps(SelectionKey.OP_WRITE)
        }

        private fun flush() {
            val o = out ?: return
            ch.write(o)
            if (!o.hasRemaining()) close()
        }

        private fun close() {
            timer.cancel()
            closeQuietly(ch)
        }
    }

    // ---- Маршруты ----

    private fun handle(head: String): Response {
        val lines = head.split("\r\n")
        val parts = lines[0].split(' ')
        if (parts.size != 3) return text(400, "Bad Request")
        val method = parts[0]
        val target = parts[1]

        val headers = HashMap<String, String>()
        for (l in lines.drop(1)) {
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val host = headers["host"]
        if (host == null || host !in allowedHosts) return text(403, "Forbidden")

        val path = target.substringBefore('?')
        val query = try {
            parseQuery(target.substringAfter('?', ""))
        } catch (_: IllegalArgumentException) {
            return text(400, "Bad Request")
        }

        return when {
            method == "GET" && path == "/" -> Response(200, "text/html; charset=utf-8", page)

            method == "GET" && path == "/api/state" -> json(200, stateJson())

            method == "POST" && (path == "/api/ban" || path == "/api/unban") -> {
                if (headers["x-admin"] != "1") return text(403, "Forbidden")
                val uid = query["uid"]?.takeIf { it.isNotEmpty() && it.length <= 64 }
                    ?: return text(400, "uid required")
                if (path == "/api/ban") {
                    val closed = registry.ban(uid) ?: return json(404, """{"ok":false,"error":"unknown client"}""")
                    json(200, """{"ok":true,"closed":$closed}""")
                } else if (registry.unban(uid)) {
                    json(200, """{"ok":true}""")
                } else {
                    json(404, """{"ok":false,"error":"not banned"}""")
                }
            }

            else -> text(404, "Not Found")
        }
    }

    private fun stateJson(): String {
        val now = System.currentTimeMillis()
        return Json.state(now, (now - startedMs) / 1000, proxyPort, registry.snapshot(), addresses(now))
    }

    private var addressesCache: List<LocalAddress> = emptyList()
    private var addressesCachedAtMs = 0L

    private fun addresses(nowMs: Long): List<LocalAddress> {
        if (nowMs - addressesCachedAtMs > 5_000) {
            addressesCache = NetworkAddresses.list()
            addressesCachedAtMs = nowMs
        }
        return addressesCache
    }

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isEmpty()) return emptyMap()
        val m = HashMap<String, String>()
        for (pair in q.split('&')) {
            val i = pair.indexOf('=')
            if (i < 0) continue
            m[URLDecoder.decode(pair.substring(0, i), Charsets.UTF_8)] =
                URLDecoder.decode(pair.substring(i + 1), Charsets.UTF_8)
        }
        return m
    }

    private fun text(status: Int, body: String) =
        Response(status, "text/plain; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun json(status: Int, body: String) =
        Response(status, "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun serialize(r: Response): ByteBuffer {
        val head = "HTTP/1.1 ${r.status} ${reason(r.status)}\r\n" +
                "Content-Type: ${r.contentType}\r\n" +
                "Content-Length: ${r.body.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Content-Security-Policy: $CSP\r\n" +
                "Connection: close\r\n\r\n"
        val h = head.toByteArray(Charsets.ISO_8859_1)
        return ByteBuffer.allocate(h.size + r.body.size).put(h).put(r.body).flip()
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        431 -> "Request Header Fields Too Large"
        else -> "Internal Server Error"
    }

    private companion object {
        const val CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'"
    }
}