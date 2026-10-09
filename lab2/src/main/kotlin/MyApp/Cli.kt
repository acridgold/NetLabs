package MyApp
import kotlin.system.exitProcess

private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = 5267

private val USAGE = """
    Использование:
      server [port] [maxClients]     порт по умолчанию $DEFAULT_PORT, потолок одновременных клиентов $DEFAULT_MAX_CLIENTS
      client <file> [host] [port]    host:port по умолчанию $DEFAULT_HOST:$DEFAULT_PORT
      help                           эта справка

    Примеры:
      server 5267 8                        -> не более 8 клиентов одновременно
      client big.iso                       -> $DEFAULT_HOST:$DEFAULT_PORT
      client /data/video.mkv server        -> server:$DEFAULT_PORT
      client ./test.bin 10.0.0.5 6000
""".trimIndent()

fun runCli(args: Array<String>) {
    val exitCode = when (args.firstOrNull()) {
        "server" ->
            if (args.size in 1..3) {
                runServer(
                    args.getOrNull(1)?.let(::parsePort) ?: DEFAULT_PORT,
                    args.getOrNull(2)?.let(::parseMaxClients) ?: DEFAULT_MAX_CLIENTS,
                )
                0
            } else usage()

        "client" ->
            if (args.size in 2..4) {
                runClient(
                    args[1],
                    args.getOrElse(2) { DEFAULT_HOST },
                    args.getOrNull(3)?.let(::parsePort) ?: DEFAULT_PORT,
                )
            } else usage()

        "help", "-h", "--help" -> {
            println(USAGE)
            0
        }

        else -> usage()
    }
    exitProcess(exitCode)
}

private fun parsePort(s: String): Int =
    s.toIntOrNull()?.takeIf { it in 0..65535 } ?: run {
        System.err.println("Некорректный порт: $s")
        usage()
    }

private fun parseMaxClients(s: String): Int =
    s.toIntOrNull()?.takeIf { it >= 1 } ?: run {
        System.err.println("Некорректное число клиентов: $s")
        usage()
    }

private fun usage(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}