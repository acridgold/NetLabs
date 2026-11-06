package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.Weather
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import APICalling.net.Urls.coord
import java.util.concurrent.CompletableFuture
import kotlin.math.exp

class WeatherApi(private val client: JsonHttpClient) {
    fun forecast(loc: Location, scope: RequestScope): CompletableFuture<Weather> =
        client.getJson("${Config.WEATHER_URL}?lat=${coord(loc.lat)}&lon=${coord(loc.lon)}", scope) { json ->
            val first = json.get("properties").get("timeseries").get(0)
            val data = first.get("data")
            val details = data.get("instant").get("details")
            val temp = details.get("air_temperature").asDouble()
            val wind = details.get("wind_speed").asDouble()
            val humidity = details.get("relative_humidity").asDouble()
            Weather(
                tempC = temp,
                feelsLikeC = apparentTemperature(temp, humidity, wind),
                windMs = wind,
                humidity = humidity,
                pressureMmHg = details.get("air_pressure_at_sea_level")?.asDouble()?.times(Config.HPA_TO_MMHG),
                symbol = data.get("next_1_hours")?.get("summary")?.get("symbol_code")?.asText(),
                time = first.get("time").asText(),
            )
        }

    private fun apparentTemperature(tempC: Double, humidityPct: Double, windMs: Double): Double {
        val vapourPressure = humidityPct / 100.0 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
        return tempC + 0.33 * vapourPressure - 0.70 * windMs - 4.00
    }
}
