package socks.admin

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

internal data class LocalAddress(val iface: String, val ip: String)

internal object NetworkAddresses {

    fun list(): List<LocalAddress> {
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (_: SocketException) {
            null
        } ?: return emptyList()

        val result = ArrayList<LocalAddress>()
        for (nif in ifaces) {
            try {
                if (!nif.isUp || nif.isLoopback) continue
            } catch (_: SocketException) {
                continue
            }
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    result.add(LocalAddress(nif.name, addr.hostAddress))
                }
            }
        }
        return result.sortedWith(compareBy({ rank(it.iface) }, { it.iface }))
    }

    private fun rank(name: String): Int = when {
        name.startsWith("wl") || name.startsWith("en") || name.startsWith("eth") -> 0
        name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth") ||
                name.startsWith("virbr") -> 2
        else -> 1
    }
}