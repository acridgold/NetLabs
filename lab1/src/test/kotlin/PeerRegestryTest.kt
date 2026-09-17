import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.util.*

class PeerRegistryTest {

    private val timeoutMs = 3500L
    private fun addr(ip: String): InetAddress = InetAddress.getByName(ip)

    @Test
    fun `добавление нового узла возвращает true`() {
        val registry = PeerRegistry(timeoutMs)
        val id = UUID.randomUUID()

        val changed = registry.heartbeat(id, addr("10.0.0.1"), 1000L)

        assertTrue(changed)
        assertEquals(setOf("10.0.0.1"), registry.snapshot())
    }

    @Test
    fun `обновление существующего узла без смены IP возвращает false`() {
        val registry = PeerRegistry(timeoutMs)
        val id = UUID.randomUUID()
        registry.heartbeat(id, addr("10.0.0.1"), 1000L)

        val changed = registry.heartbeat(id, addr("10.0.0.1"), 2000L)

        assertFalse(changed)
    }

    @Test
    fun `изменение IP-адреса существующего узла возвращает true`() {
        val registry = PeerRegistry(timeoutMs)
        val id = UUID.randomUUID()
        registry.heartbeat(id, addr("10.0.0.1"), 1000L)

        val changed = registry.heartbeat(id, addr("10.0.0.2"), 2000L)

        assertTrue(changed)
        assertEquals(setOf("10.0.0.2"), registry.snapshot())
    }

    @Test
    fun `prune удаляет узел, если превышен таймаут`() {
        val registry = PeerRegistry(timeoutMs)
        val id = UUID.randomUUID()
        registry.heartbeat(id, addr("10.0.0.1"), 1000L)

        val pruned = registry.prune(1000L + timeoutMs + 1)

        assertTrue(pruned)
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun `prune не удаляет активные узлы`() {
        val registry = PeerRegistry(timeoutMs)
        registry.heartbeat(UUID.randomUUID(), addr("10.0.0.1"), 1000L)

        val pruned = registry.prune(1000L + timeoutMs - 100)

        assertFalse(pruned)
        assertEquals(setOf("10.0.0.1"), registry.snapshot())
    }

    @Test
    fun `snapshot возвращает отсортированный список IP-адресов`() {
        val registry = PeerRegistry(timeoutMs)
        registry.heartbeat(UUID.randomUUID(), addr("10.0.0.5"), 1000L)
        registry.heartbeat(UUID.randomUUID(), addr("10.0.0.1"), 1000L)
        registry.heartbeat(UUID.randomUUID(), addr("10.0.0.3"), 1000L)

        // snapshot возвращает Set, так как toSortedSet сохраняет порядок
        assertEquals(setOf("10.0.0.1", "10.0.0.3", "10.0.0.5"), registry.snapshot())
    }
}