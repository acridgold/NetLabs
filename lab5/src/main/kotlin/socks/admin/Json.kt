package socks.admin

import socks.clients.ClientView

internal object Json {

    fun str(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun state(
        nowMs: Long,
        uptimeSec: Long,
        proxyPort: Int,
        clients: List<ClientView>,
        addresses: List<LocalAddress> = emptyList(),
    ): String {
        val sb = StringBuilder(1024)
        sb.append("{\"now\":").append(nowMs)
            .append(",\"uptime\":").append(uptimeSec)
            .append(",\"proxyPort\":").append(proxyPort)
            .append(",\"addresses\":[")
        addresses.forEachIndexed { i, a ->
            if (i > 0) sb.append(',')
            sb.append("{\"iface\":").append(str(a.iface)).append(",\"ip\":").append(str(a.ip)).append('}')
        }
        sb.append("],\"clients\":[")
        clients.forEachIndexed { i, c ->
            if (i > 0) sb.append(',')
            sb.append("{\"uid\":").append(str(c.uid))
                .append(",\"banned\":").append(c.banned)
                .append(",\"active\":").append(c.activeConnections)
                .append(",\"total\":").append(c.totalConnections)
                .append(",\"blocked\":").append(c.blockedAttempts)
                .append(",\"up\":").append(c.bytesUp)
                .append(",\"down\":").append(c.bytesDown)
                .append(",\"conns\":[")
            c.connections.forEachIndexed { j, k ->
                if (j > 0) sb.append(',')
                sb.append("{\"id\":").append(k.id)
                    .append(",\"addr\":").append(str(k.clientAddr))
                    .append(",\"target\":").append(str(k.target))
                    .append(",\"started\":").append(k.startedAtMs)
                    .append(",\"up\":").append(k.bytesUp)
                    .append(",\"down\":").append(k.bytesDown)
                    .append('}')
            }
            sb.append("]}")
        }
        sb.append("]}")
        return sb.toString()
    }
}