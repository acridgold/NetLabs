package socks.protocol

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer


object Socks5Codec {

    fun parseGreeting(buf: ByteBuffer): Parsed<Greeting> {
        val base = buf.position()
        val avail = buf.remaining()
        if (avail < 2) return Parsed.NeedMore
        if (u(buf, base) != Socks5.VERSION) return Parsed.Malformed
        val n = u(buf, base + 1)
        if (avail < 2 + n) return Parsed.NeedMore
        val methods = HashSet<Int>()
        for (i in 0 until n) methods.add(u(buf, base + 2 + i))
        return Parsed.Ok(Greeting(methods), 2 + n)
    }

    fun parseRequest(buf: ByteBuffer): Parsed<ConnectRequest> {
        val base = buf.position()
        val avail = buf.remaining()
        if (avail < 4) return Parsed.NeedMore
        if (u(buf, base) != Socks5.VERSION) return Parsed.Malformed
        if (u(buf, base + 1) != Socks5.CMD_CONNECT) return Parsed.Refused(ReplyCode.COMMAND_NOT_SUPPORTED)

        val atyp = u(buf, base + 3)
        val total = when (atyp) {
            Socks5.ATYP_IPV4 -> 4 + 4 + 2
            Socks5.ATYP_DOMAIN -> {
                if (avail < 5) return Parsed.NeedMore
                val len = u(buf, base + 4)
                if (len == 0) return Parsed.Refused(ReplyCode.GENERAL_FAILURE)
                4 + 1 + len + 2
            }
            else -> return Parsed.Refused(ReplyCode.ADDRESS_TYPE_NOT_SUPPORTED) // в т.ч. IPv6
        }
        if (avail < total) return Parsed.NeedMore

        val port = (u(buf, base + total - 2) shl 8) or u(buf, base + total - 1)
        val destination = if (atyp == Socks5.ATYP_IPV4) {
            val raw = ByteArray(4) { buf.get(base + 4 + it) }
            Destination.Ip(InetAddress.getByAddress(raw), port)
        } else {
            val len = u(buf, base + 4)
            val host = ByteArray(len) { buf.get(base + 5 + it) }.toString(Charsets.US_ASCII)
            parseIpv4Literal(host)?.let { Destination.Ip(it, port) } ?: Destination.Domain(host, port)
        }
        return Parsed.Ok(ConnectRequest(destination), total)
    }

    fun methodSelection(method: Int): ByteArray =
        byteArrayOf(Socks5.VERSION.toByte(), method.toByte())


    fun reply(code: ReplyCode, bound: InetSocketAddress? = null): ByteArray {
        val ip = (bound?.address as? Inet4Address)?.address ?: ByteArray(4)
        val port = bound?.port ?: 0
        return byteArrayOf(
            Socks5.VERSION.toByte(), code.value.toByte(), 0x00, Socks5.ATYP_IPV4.toByte(),
            ip[0], ip[1], ip[2], ip[3],
            (port shr 8).toByte(), (port and 0xFF).toByte(),
        )
    }

    private fun u(buf: ByteBuffer, index: Int): Int = buf.get(index).toInt() and 0xFF

    private fun parseIpv4Literal(host: String): InetAddress? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val raw = ByteArray(4)
        for (i in 0 until 4) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            raw[i] = v.toByte()
        }
        return InetAddress.getByAddress(raw)
    }
}
