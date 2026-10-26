package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.Weather
import APICalling.net.JsonHttpClient
import APICalling.net.Urls.coord
import java.util.concurrent.CompletableFuture

class WeatherApi(private val client: JsonHttpClient) {
    fun current(loc: Location): CompletableFuture<Weather> =
        client.getJson("${Config.WEATHER_URL}?lat=${coord(loc.lat)}&lon=${coord(loc.lon)}")
            .thenApply { json ->
                val now = json.get("properties").get("timeseries").get(0)
                val data = now.get("data")
                val details = data.get("instant").get("details")
                Weather(
                    tempC = details.get("air_temperature").asDouble(),
                    windMs = details.get("wind_speed").asDouble(),
                    humidity = details.get("relative_humidity").asDouble(),
                    symbol = data.get("next_1_hours")?.get("summary")?.get("symbol_code")?.asText(),
                    time = now.get("time").asText(),
                )
            }
}