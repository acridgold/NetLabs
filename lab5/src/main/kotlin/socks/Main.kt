package socks

import socks.cli.CommandLine
import socks.proxy.ProxyServer

fun main(args: Array<String>) {
    val config = CommandLine.parse(args)
    println("SOCKS5 proxy: порт ${config.listenPort}, DNS-резолвер ${config.dnsServer.address.hostAddress}:${config.dnsServer.port}")
    ProxyServer(config).run()
}
