import java.net.NetworkInterface
import java.util.*
import kotlin.system.exitProcess

object AppRunner {

    fun run(args: Array<String>) {
//        System.setProperty("java.net.preferIPv4Stack", "true")
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
