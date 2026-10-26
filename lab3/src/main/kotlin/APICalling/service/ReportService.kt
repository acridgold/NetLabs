package APICalling.service

import APICalling.api.DescriptionApi
import APICalling.api.PlacesApi
import APICalling.api.WeatherApi
import APICalling.model.Location
import APICalling.model.Place
import APICalling.model.Report
import APICalling.model.asOutcome
import java.util.concurrent.CompletableFuture

class ReportService(
    private val weatherApi: WeatherApi,
    private val placesApi: PlacesApi,
    private val descriptionApi: DescriptionApi,
) {
    fun loadReport(loc: Location): CompletableFuture<Report> {
        val weather = weatherApi.current(loc).asOutcome()
        val places = placesWithDescriptions(loc).asOutcome()
        return weather.thenCombine(places) { w, p -> Report(loc, w, p) }
    }

    private fun placesWithDescriptions(loc: Location): CompletableFuture<List<Place>> =
        placesApi.nearby(loc).thenCompose { refs ->
            val futures = refs.map(descriptionApi::describe)
            CompletableFuture.allOf(*futures.toTypedArray())
                .thenApply { futures.map { it.join() } }
        }
}