import java.net.*

const val DEFAULT_PORT = 5000

data class Config(
    val groupAddress: InetAddress,
    val port: Int,
    val isIpv6: Boolean,
    val interfaceName: String?
)

fun parseArguments(args: Array<String>): Config {
    require(args.isNotEmpty()) { "Использование: <multicast-адрес> [порт=5000] [сетевой интерфейс]" }

    val groupAddress = try {
        InetAddress.getByName(args[0])
    } catch (e: UnknownHostException) {
        throw IllegalArgumentException("Не удалось разрешить адрес '${args[0]}'")
    }

    require(groupAddress.isMulticastAddress) { "Адрес ${groupAddress.hostAddress} не является multicast-адресом." }

    val port = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
    require(port in 1..65535) { "Порт должен быть в диапазоне 1..65535, получено: $port" }

    return Config(groupAddress, port, groupAddress is Inet6Address, args.getOrNull(2))
}

/**
 * Выбирает сетевой интерфейс для multicast.
 *
 * Раньше брался первый попавшийся интерфейс, у которого есть адрес нужного
 * семейства. На машинах с VPN/туннелями (utun, tap, tun, vpn, docker, vbox,
 * vmnet и т.п.) это часто оказывался не тот адаптер, и multicast-трафик уходил
 * не в ту сеть — Windows и Linux не видели друг друга.
 *
 * Теперь предпочитаем интерфейс с «глобальным» (не link-local) адресом нужного
 * семейства, игнорируя туннели и виртуальные адаптеры. Если такого нет —
 * откатываемся к любому поднятому multicast-интерфейсу.
 */
fun pickDefaultInterface(isIpv6: Boolean): NetworkInterface? {
    val interfaces = NetworkInterface.getNetworkInterfaces().toList()

    fun hasFamilyAddress(nif: NetworkInterface): Boolean =
        nif.inetAddresses.toList().any { addr ->
            if (isIpv6) addr is Inet6Address else addr is Inet4Address
        }

    fun hasGlobalAddress(nif: NetworkInterface): Boolean =
        nif.inetAddresses.toList().any { addr ->
            if (isIpv6) addr is Inet6Address && !addr.isLinkLocalAddress && !addr.isSiteLocalAddress
            else addr is Inet4Address && !addr.isLinkLocalAddress && !addr.isSiteLocalAddress
        }

    fun isUsable(nif: NetworkInterface): Boolean = try {
        nif.isUp && nif.supportsMulticast() && !nif.isLoopback && hasFamilyAddress(nif)
    } catch (e: SocketException) {
        false
    }

    // Туннели и виртуальные адаптеры, которые почти наверняка не нужны.
    val tunnelNames = listOf("utun", "tun", "tap", "vpn", "ppp", "docker", "veth",
        "vbox", "vmnet", "virbr", "br-", "awdl", "llw", "bridge", "anpi")

    fun isTunnel(nif: NetworkInterface): Boolean =
        tunnelNames.any { nif.name.startsWith(it, ignoreCase = true) }

    // 1) Реальный интерфейс с глобальным адресом нужного семейства.
    interfaces.firstOrNull { nif -> isUsable(nif) && !isTunnel(nif) && hasGlobalAddress(nif) }
        // 2) Любой не-туннельный интерфейс с адресом нужного семейства.
        ?: interfaces.firstOrNull { nif -> isUsable(nif) && !isTunnel(nif) }
        // 3) Любой поднятый multicast-интерфейс (включая туннели) — крайний случай.
        ?: interfaces.firstOrNull { nif -> isUsable(nif) }
}