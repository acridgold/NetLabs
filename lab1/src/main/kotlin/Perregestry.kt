import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class PeerInfo(
    val id: UUID,
    var address: InetAddress,
    var lastSeenMs: Long
)

class PeerRegistry(private val timeoutMs: Long) {
    private val peers = ConcurrentHashMap<UUID, PeerInfo>()

    fun heartbeat(id: UUID, address: InetAddress, nowMs: Long): Boolean {
        val existing = peers[id]
        if (existing == null) {
            peers[id] = PeerInfo(id, address, nowMs)
            return true
        }

        val addressChanged = existing.address != address
        if (addressChanged) existing.address = address

        existing.lastSeenMs = nowMs

        return addressChanged
    }

    fun prune(nowMs: Long): Boolean =
        peers.values.removeIf { nowMs - it.lastSeenMs > timeoutMs }

    fun snapshot(): Set<String> =
        peers.values.map { it.address.hostAddress }.toSortedSet()
}