package socks.clients

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientRegistryTest {

    private class FakeConn(
        override val id: Long,
        override val uid: String,
        private val registry: ClientRegistry,
        var up: Long = 0,
        var down: Long = 0,
    ) : TrackedConnection {
        var killed = false
        override val clientAddr: String = "$uid:1000"
        override val target: String = "example.com:443"
        override val startedAtMs: Long = 0L
        override val bytesUp: Long get() = up
        override val bytesDown: Long get() = down

        override fun kill() {
            killed = true
            registry.unregister(this)
        }
    }

    @Test
    fun banClosesAllConnectionsOfClientAndBlocksIt() {
        val r = ClientRegistry()
        val a = FakeConn(1, "10.0.0.1", r)
        val b = FakeConn(2, "10.0.0.1", r)
        val other = FakeConn(3, "10.0.0.2", r)
        listOf(a, b, other).forEach { r.register(it) }

        assertEquals(2, r.ban("10.0.0.1"))

        assertTrue(a.killed)
        assertTrue(b.killed)
        assertFalse(other.killed)
        assertTrue(r.isBanned("10.0.0.1"))
        assertFalse(r.isBanned("10.0.0.2"))
    }

    @Test
    fun banUnknownClientReturnsNull() {
        assertNull(ClientRegistry().ban("1.2.3.4"))
    }

    @Test
    fun unbanAllowsClientAgain() {
        val r = ClientRegistry()
        r.register(FakeConn(1, "10.0.0.1", r))
        r.ban("10.0.0.1")
        assertTrue(r.unban("10.0.0.1"))
        assertFalse(r.isBanned("10.0.0.1"))
        assertFalse(r.unban("10.0.0.1")) // уже разбанен
    }

    @Test
    fun trafficOfClosedAndActiveConnectionsIsSummed() {
        val r = ClientRegistry()
        val a = FakeConn(1, "10.0.0.1", r, up = 100, down = 200)
        val b = FakeConn(2, "10.0.0.1", r, up = 1, down = 2)
        r.register(a)
        r.register(b)
        r.unregister(a)

        val view = r.snapshot().single()
        assertEquals(101L, view.bytesUp)
        assertEquals(202L, view.bytesDown)
        assertEquals(1, view.activeConnections)
        assertEquals(2L, view.totalConnections)
    }
}
