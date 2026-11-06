package APICalling.net

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ApiLog {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun line(level: String, message: String) =
        println("${LocalTime.now().format(fmt)} [API] $level $message")

    fun ok(message: String) = line("OK    ", message)

    fun error(message: String) = line("ERROR ", message)

    fun cancelled(message: String) = line("CANCEL", message)
}
