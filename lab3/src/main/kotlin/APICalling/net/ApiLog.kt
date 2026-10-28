package APICalling.net

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ApiLog {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun ok(message: String) = println("${LocalTime.now().format(fmt)} [API] OK    $message")

    fun error(message: String) = println("${LocalTime.now().format(fmt)} [API] ERROR $message")
}
