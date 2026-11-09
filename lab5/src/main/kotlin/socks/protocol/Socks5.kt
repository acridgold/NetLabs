package socks.protocol

import java.net.InetAddress

object Socks5 {
    const val VERSION = 0x05
    const val METHOD_NO_AUTH = 0x00
    const val METHOD_NO_ACCEPTABLE = 0xFF
    const val CMD_CONNECT = 0x01
    const val ATYP_IPV4 = 0x01
    const val ATYP_DOMAIN = 0x03
    const val ATYP_IPV6 = 0x04
}


enum class ReplyCode(val value: Int) {
    SUCCEEDED(0x00),
    GENERAL_FAILURE(0x01),
    NET_UNREACHABLE(0x03),
    HOST_UNREACHABLE(0x04),
    CONNECTION_REFUSED(0x05),
    COMMAND_NOT_SUPPORTED(0x07),
    ADDRESS_TYPE_NOT_SUPPORTED(0x08),
}

sealed interface Destination {
    val port: Int

    data class Ip(val address: InetAddress, override val port: Int) : Destination
    data class Domain(val host: String, override val port: Int) : Destination
}

data class Greeting(val methods: Set<Int>)

data class ConnectRequest(val destination: Destination)


sealed interface Parsed<out T> {

    data object NeedMore : Parsed<Nothing>


    data class Ok<T>(val value: T, val consumed: Int) : Parsed<T>


    data object Malformed : Parsed<Nothing>


    data class Refused(val code: ReplyCode) : Parsed<Nothing>
}
