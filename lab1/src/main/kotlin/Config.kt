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

fun pickDefaultInterface(isIpv6: Boolean): NetworkInterface? {
    return NetworkInterface.getNetworkInterfaces().toList().firstOrNull { nif ->
        try {
            nif.isUp && nif.supportsMulticast() && !nif.isLoopback &&
                    nif.inetAddresses.toList().any { addr ->
                        if (isIpv6) addr is Inet6Address else addr is Inet4Address
                    }
        } catch (e: SocketException) {
            false
        }
    }
}