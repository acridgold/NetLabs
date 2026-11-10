package socks.clients

import socks.util.Log


interface TrackedConnection {
    val id: Long
    val uid: String
    val clientAddr: String
    val target: String
    val startedAtMs: Long

    val bytesUp: Long
    val bytesDown: Long

    fun kill()
}

data class ConnectionView(
    val id: Long,
    val clientAddr: String,
    val target: String,
    val startedAtMs: Long,
    val bytesUp: Long,
    val bytesDown: Long,
)

data class ClientView(
    val uid: String,
    val banned: Boolean,
    val activeConnections: Int,
    val totalConnections: Long,
    val blockedAttempts: Long,
    val bytesUp: Long,
    val bytesDown: Long,
    val connections: List<ConnectionView>,
)

class ClientRegistry {

    private class Record(val uid: String) {
        val connections = LinkedHashSet<TrackedConnection>()
        var banned = false
        var totalConnections = 0L
        var blockedAttempts = 0L
        var closedUp = 0L
        var closedDown = 0L
        var lastSeenMs = 0L
        var lastBlockedLogMs = 0L
    }

    private val clients = LinkedHashMap<String, Record>()

    fun isBanned(uid: String): Boolean = clients[uid]?.banned == true

    fun register(c: TrackedConnection) {
        val rec = clients.getOrPut(c.uid) { Record(c.uid) }
        rec.connections.add(c)
        rec.totalConnections++
        rec.lastSeenMs = System.currentTimeMillis()
    }

    fun unregister(c: TrackedConnection) {
        val rec = clients[c.uid] ?: return
        if (rec.connections.remove(c)) {
            rec.closedUp += c.bytesUp
            rec.closedDown += c.bytesDown
            rec.lastSeenMs = System.currentTimeMillis()
        }
    }

    fun onBlocked(uid: String) {
        val rec = clients[uid] ?: return
        rec.blockedAttempts++
        val now = System.currentTimeMillis()
        if (now - rec.lastBlockedLogMs >= BLOCKED_LOG_INTERVAL_MS) {
            rec.lastBlockedLogMs = now
            Log.access("$uid BLOCKED: клиент забанен (отклонено подключений: ${rec.blockedAttempts})")
        }
    }

    fun ban(uid: String): Int? {
        val rec = clients[uid] ?: return null
        rec.banned = true
        val victims = rec.connections.toList() // kill() меняет исходное множество
        Log.ban(uid, victims.size)
        victims.forEach { it.kill() }
        return victims.size
    }

    fun unban(uid: String): Boolean {
        val rec = clients[uid] ?: return false
        if (!rec.banned) return false
        rec.banned = false
        Log.unban(uid)
        return true
    }

    fun snapshot(): List<ClientView> {
        val now = System.currentTimeMillis()
        clients.values.removeIf { !it.banned && it.connections.isEmpty() && now - it.lastSeenMs > IDLE_PRUNE_MS }
        return clients.values.map { r ->
            val conns = r.connections.map {
                ConnectionView(it.id, it.clientAddr, it.target, it.startedAtMs, it.bytesUp, it.bytesDown)
            }
            ClientView(
                uid = r.uid,
                banned = r.banned,
                activeConnections = conns.size,
                totalConnections = r.totalConnections,
                blockedAttempts = r.blockedAttempts,
                bytesUp = r.closedUp + conns.sumOf { it.bytesUp },
                bytesDown = r.closedDown + conns.sumOf { it.bytesDown },
                connections = conns,
            )
        }
    }

    private companion object {
        const val BLOCKED_LOG_INTERVAL_MS = 5_000L
        const val IDLE_PRUNE_MS = 30 * 60 * 1000L
    }
}
