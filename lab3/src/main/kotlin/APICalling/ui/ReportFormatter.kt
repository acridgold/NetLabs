package APICalling.ui

import APICalling.Config
import APICalling.model.Report

object ReportFormatter {
    private val symbolsRu = mapOf(
        "clearsky" to "ясно", "fair" to "малооблачно", "partlycloudy" to "переменная облачность",
        "cloudy" to "облачно", "fog" to "туман", "lightrain" to "небольшой дождь", "rain" to "дождь",
        "heavyrain" to "сильный дождь", "lightsnow" to "небольшой снег", "snow" to "снег",
        "heavysnow" to "сильный снег", "sleet" to "дождь со снегом",
    )

    private fun symbolRu(code: String?): String? {
        val base = code?.substringBefore('_') ?: return null
        return symbolsRu[base] ?: base
    }

    fun format(r: Report): String {
        val sb = StringBuilder()
        sb.appendLine("\n=== ${r.location} ===")

        sb.appendLine("\nПогода (MET Norway):")
        r.weather.value?.let {
            sb.appendLine("  Температура: ${it.tempC} °C, ветер ${it.windMs} м/с, влажность ${it.humidity} %")
            symbolRu(it.symbol)?.let { s -> sb.appendLine("  Ближайший час: $s") }
            sb.appendLine("  Данные на: ${it.time}")
        } ?: sb.appendLine("  не удалось получить: ${r.weather.error}")

        sb.appendLine("\nИнтересные места рядом (OpenStreetMap + Wikidata):")
        val places = r.places.value
        when {
            places == null -> sb.appendLine("  не удалось получить: ${r.places.error}")
            places.isEmpty() -> sb.appendLine("  ничего не найдено в радиусе ${Config.SEARCH_RADIUS_M} м")
            else -> places.forEachIndexed { i, p ->
                sb.appendLine("  ${i + 1}. ${p.name} (${p.kind})")
                sb.appendLine("     ${p.description ?: "описание отсутствует"}")
            }
        }
        return sb.toString()
    }
}