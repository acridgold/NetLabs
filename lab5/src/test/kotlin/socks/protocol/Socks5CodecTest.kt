package socks.protocol

import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Socks5CodecTest {

    private fun bytes(vararg b: Int): ByteBuffer =
        ByteBuffer.wrap(ByteArray(b.size) { b[it].toByte() })

    @Test
    fun greetingNeedsMoreData() {
        assertEquals(Parsed.NeedMore, Socks5Codec.parseGreeting(bytes(5)))
        assertEquals(Parsed.NeedMore, Socks5Codec.parseGreeting(bytes(5, 2, 0)))
    }

    @Test
    fun greetingParsed() {
        assertEquals(
            Parsed.Ok(Greeting(setOf(0, 2)), 4),
            Socks5Codec.parseGreeting(bytes(5, 2, 0, 2)),
        )
    }

    @Test
    fun greetingWrongVersionIsMalformed() {
        assertEquals(Parsed.Malformed, Socks5Codec.parseGreeting(bytes(4, 1, 0)))
    }

    @Test
    fun requestIpv4() {
        val expected = Destination.Ip(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 8080)
        assertEquals(
            Parsed.Ok(ConnectRequest(expected), 10),
            Socks5Codec.parseRequest(bytes(5, 1, 0, 1, 127, 0, 0, 1, 0x1F, 0x90)),
        )
    }

    @Test
    fun requestDomain() {
        val host = "example.com"
        val raw = intArrayOf(5, 1, 0, 3, host.length) + host.map { it.code } + intArrayOf(0, 80)
        assertEquals(
            Parsed.Ok(ConnectRequest(Destination.Domain(host, 80)), raw.size),
            Socks5Codec.parseRequest(bytes(*raw)),
        )
    }

    @Test
    fun domainThatIsIpv4LiteralBecomesIp() {
        val host = "10.1.2.3"
        val raw = intArrayOf(5, 1, 0, 3, host.length) + host.map { it.code } + intArrayOf(1, 0xBB)
        val expected = Destination.Ip(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 3)), 443)
        assertEquals(
            Parsed.Ok(ConnectRequest(expected), raw.size),
            Socks5Codec.parseRequest(bytes(*raw)),
        )
    }

    @Test
    fun requestIncompleteDomain() {
        assertEquals(Parsed.NeedMore, Socks5Codec.parseRequest(bytes(5, 1, 0, 3, 11, 'e'.code)))
    }

    @Test
    fun ipv6IsRefused() {
        assertEquals(
            Parsed.Refused(ReplyCode.ADDRESS_TYPE_NOT_SUPPORTED),
            Socks5Codec.parseRequest(bytes(5, 1, 0, 4)),
        )
    }

    @Test
    fun bindIsRefused() {
        assertEquals(
            Parsed.Refused(ReplyCode.COMMAND_NOT_SUPPORTED),
            Socks5Codec.parseRequest(bytes(5, 2, 0, 1)),
        )
    }

    @Test
    fun errorReplyBytes() {
        assertContentEquals(
            byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0),
            Socks5Codec.reply(ReplyCode.HOST_UNREACHABLE),
        )
    }

    @Test
    fun methodSelectionBytes() {
        assertContentEquals(byteArrayOf(5, 0), Socks5Codec.methodSelection(Socks5.METHOD_NO_AUTH))
        assertContentEquals(byteArrayOf(5, -1), Socks5Codec.methodSelection(Socks5.METHOD_NO_ACCEPTABLE))
    }
}
