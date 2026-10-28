package APICalling.ui

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val ru: Locale = Locale.forLanguageTag("ru")

    fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun distance(m: Int): String = if (m < 1000) "$m м" else String.format(ru, "%.1f км", m / 1000.0)

    fun radius(m: Int): String =
        if (m < 1000) "$m м" else String.format(ru, if (m % 1000 == 0) "%.0f км" else "%.1f км", m / 1000.0)

    fun temp(c: Double): String = String.format(ru, "%.1f°", c)

    fun number(v: Double, digits: Int = 1): String = String.format(ru, "%.${digits}f", v)

    fun updated(iso: String): String = try {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", ru))
    } catch (e: Exception) {
        iso
    }

    private val symbols = mapOf(
        "clearsky" to "Ясно",
        "fair" to "Малооблачно",
        "partlycloudy" to "Переменная облачность",
        "cloudy" to "Облачно",
        "fog" to "Туман",
        "lightrain" to "Небольшой дождь",
        "rain" to "Дождь",
        "heavyrain" to "Сильный дождь",
        "lightrainshowers" to "Небольшой ливень",
        "rainshowers" to "Ливень",
        "heavyrainshowers" to "Сильный ливень",
        "lightsnow" to "Небольшой снег",
        "snow" to "Снег",
        "heavysnow" to "Сильный снег",
        "lightsnowshowers" to "Небольшой снегопад",
        "snowshowers" to "Снегопад",
        "heavysnowshowers" to "Сильный снегопад",
        "lightsleet" to "Небольшой дождь со снегом",
        "sleet" to "Дождь со снегом",
        "heavysleet" to "Сильный дождь со снегом",
        "lightsleetshowers" to "Небольшая крупа",
        "sleetshowers" to "Крупа",
        "heavysleetshowers" to "Сильная крупа",
    )

    fun condition(code: String?): String? {
        val base = code?.substringBefore('_') ?: return null
        if ("thunder" in base) return "Гроза"
        return symbols[base] ?: base
    }
}
