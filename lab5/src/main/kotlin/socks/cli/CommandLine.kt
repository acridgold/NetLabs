package socks.cli

import org.xbill.DNS.ResolverConfig
import socks.config.ProxyConfig
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.system.exitProcess

object CommandLine {

    fun parse(args: Array<String>): ProxyConfig {
        if (args.isEmpty() || args.size > 2) usage()

        val port = args[0].toIntOrNull()?.takeIf { it in 1..65535 }
            ?: usage("Некорректный порт: ${args[0]}")

        val dns = if (args.size == 2) parseResolver(args[1]) else systemResolver()
        return ProxyConfig(listenPort = port, dnsServer = dns)
    }

    private fun usage(msg: String? = null): Nothing {
        if (msg != null) System.err.println(msg)
        System.err.println("Использование: socks-proxy <port> [dns_ipv4:port]")
        System.err.println("Пример:        socks-proxy 1080 1.1.1.1:53")
        exitProcess(1)
    }


    private fun parseResolver(arg: String): InetSocketAddress {
        val idx = arg.lastIndexOf(':')
        if (idx <= 0) usage("Резолвер должен быть в формате IPv4:port, получено: $arg")
        val host = arg.substring(0, idx)
        val port = arg.substring(idx + 1).toIntOrNull()?.takeIf { it in 1..65535 }
            ?: usage("Некорректный порт резолвера: $arg")
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) {
            usage("Некорректный IPv4-адрес резолвера: $host")
        }
        return InetSocketAddress(InetAddress.getByName(host), port)
    }


    private fun systemResolver(): InetSocketAddress {
        val servers = ResolverConfig.getCurrentConfig().servers()
        val pick = servers.firstOrNull { it.address is Inet4Address } ?: servers.firstOrNull()
            ?: usage("Не удалось определить системный DNS-резолвер, укажите его вторым аргументом")
        return InetSocketAddress(pick.address, 53)
    }
}
