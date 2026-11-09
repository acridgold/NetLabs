package socks.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object Log {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun ts(): String = LocalDateTime.now().format(fmt)


    fun access(msg: String) = println("${ts()} $msg")


    fun info(msg: String) = System.err.println("${ts()} [INFO] $msg")

    fun warn(msg: String) = System.err.println("${ts()} [WARN] $msg")
}
