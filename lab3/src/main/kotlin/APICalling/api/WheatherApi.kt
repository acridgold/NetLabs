package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.Weather
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import APICalling.net.Urls.coord
import com.fasterxml.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.concurrent.CompletableFuture
import kotlin.math.exp

class WeatherApi(private val client: JsonHttpClient) {
    fun forecast(loc: Location, scope: RequestScope): CompletableFuture<Weather> =
        client.getJson("${Config.WEATHER_URL}?lat=${coord(loc.lat)}&lon=${coord(loc.lon)}", scope) { json ->
            parseForecast(json)
        }

    internal fun parseForecast(json: JsonNode): Weather {
        val series = json.requireObject().objectField("properties").arrayField("timeseries")
        if (series.size() == 0) invalid("прогноз пуст (timeseries без записей)")

        val first = series.get(0).requireObject("первая запись прогноза")
        val time = first.get("time")?.takeIf { it.isTextual }?.asText() ?: invalid("у записи прогноза нет времени")
        try {
            OffsetDateTime.parse(time)
        } catch (e: DateTimeParseException) {
            invalid("некорректное время прогноза: $time")
        }

        val data = first.objectField("data")
        val details = data.objectField("instant").objectField("details")
        val temp = details.numberField("air_temperature", -100.0..70.0)
        val wind = details.numberField("wind_speed", 0.0..120.0)
        val humidity = details.numberField("relative_humidity", 0.0..100.0)
        val pressureHpa = details.optionalNumberField("air_pressure_at_sea_level", 800.0..1100.0)

        return Weather(
            tempC = temp,
            feelsLikeC = apparentTemperature(temp, humidity, wind),
            windMs = wind,
            humidity = humidity,
            pressureMmHg = pressureHpa?.times(Config.HPA_TO_MMHG),
            symbol = data.get("next_1_hours")?.get("summary")?.optionalTextField("symbol_code"),
            time = time,
        )
    }

    private fun apparentTemperature(tempC: Double, humidityPct: Double, windMs: Double): Double {
        val vapourPressure = humidityPct / 100.0 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return tempC + 0.33 * vapourPressure - 0.70 * windMs - 4.00
    }
}
