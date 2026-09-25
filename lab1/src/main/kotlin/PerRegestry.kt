import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class PeerInfo(
    val id: UUID,
    val address: InetAddress,
    val lastSeenMs: Long
)

class PeerRegistry(private val timeoutMs: Long) {
    private val peers = ConcurrentHashMap<UUID, PeerInfo>()

    fun heartbeat(id: UUID, address: InetAddress, nowMs: Long): Boolean {
        var addressChanged = false
        peers.compute(id) { _, existing ->
            if (existing == null) {
                addressChanged = true
                PeerInfo(id, address, nowMs)
            } else {
                addressChanged = existing.address != address
                existing.copy(address = address, lastSeenMs = nowMs)
            }
        }
        return addressChanged
    }

    fun prune(nowMs: Long): Boolean =
        peers.values.removeIf { nowMs - it.lastSeenMs > timeoutMs }

    fun size(): Int = peers.size

    fun snapshot(): Set<String> =
        peers.values.map { "${it.address.hostAddress} (${it.id.toString().take(8)})" }.toSortedSet()
}