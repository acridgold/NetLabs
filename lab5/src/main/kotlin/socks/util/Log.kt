package socks.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object Log {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun ts(): String = LocalDateTime.now().format(fmt)


    fun access(msg: String) = println("${ts()} $msg")


    fun info(msg: String) = System.err.println("${ts()} [INFO] $msg")

    fun warn(msg: String) = System.err.println("${ts()} [WARN] $msg")


    fun ban(uid: String, closedConnections: Int) {
        val text = "  ЧЕРТ ЗАБАНЕН: $uid  (закрыто соединений: $closedConnections)  "
        val bar = "!".repeat(text.length + 2)
        val color = System.console()?.isTerminal == true
        val on = if (color) "\u001B[1;97;41m" else ""
        val off = if (color) "\u001B[0m" else ""
        val t = ts()
        println("$t $on$bar$off")
        println("$t $on!$text!$off")
        println("$t $on$bar$off")
    }

    fun unban(uid: String) = access("РАЗБАНЕН: $uid")
}
