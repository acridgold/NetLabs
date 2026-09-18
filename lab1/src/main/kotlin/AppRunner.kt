import java.net.NetworkInterface
import java.util.*
import kotlin.system.exitProcess

object AppRunner {

    fun run(args: Array<String>) {
        // Форсируем IPv4-стек: на системах с dual-stack (IPv4+IPv6) Java может
        // выбрать IPv6-сокет, и multicast-группа IPv4 (230.0.0.1) перестанет
        // работать. Это свойство должно быть установлено до создания сокетов.
        System.setProperty("java.net.preferIPv4Stack", "true")
        val config = runCatching { parseArguments(args) }.getOrElse {
            System.err.println(it.message)
            exitProcess(1)
        }

        val networkInterface: NetworkInterface? = config.interfaceName?.let { name ->
            NetworkInterface.getByName(name) ?: run {
                System.err.println("Указанный интерфейс '$name' не найден.")
                exitProcess(1)
            }
        } ?: pickDefaultInterface(config.isIpv6)

        // Диагностика: показываем все интерфейсы и какой выбран. Помогает понять,
        // почему multicast не доходит — часто выбирается VPN/туннель вместо реальной сети.
        println("Доступные сетевые интерфейсы:")
        NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
            val addrs = nif.inetAddresses.toList()
                .joinToString(", ") { "${it.hostAddress}${if (it.isLinkLocalAddress) " (link-local)" else ""}" }
            println("  ${nif.name}: up=${nif.isUp}, multicast=${nif.supportsMulticast()}, " +
                    "loopback=${nif.isLoopback}, addrs=[$addrs]")
        }
        println("Выбран интерфейс: ${networkInterface?.name ?: "(по умолчанию)"}")

        if (config.isIpv6 && networkInterface == null) {
            System.err.println(
                "Для IPv6 multicast не удалось определить подходящий сетевой интерфейс. " +
                        "Укажите его явно третьим аргументом (см. `ip link` / `ifconfig`)."
            )
            exitProcess(1)
        }

        val selfId = UUID.randomUUID()
        println(
            "Запуск обнаружения копий: протокол=${if (config.isIpv6) "IPv6" else "IPv4"}, " +
                    "группа=${config.groupAddress.hostAddress}, порт=${config.port}, " +
                    "интерфейс=${networkInterface?.name ?: "(по умолчанию)"}, selfId=$selfId"
        )

        val discovery = SelfDiscovery(
            groupAddress = config.groupAddress,
            port = config.port,
            networkInterface = networkInterface,
            selfId = selfId
        )

        Runtime.getRuntime().addShutdownHook(Thread { discovery.stop() })
        discovery.start()
    }
}
