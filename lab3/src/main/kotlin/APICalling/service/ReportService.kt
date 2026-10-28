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
    fun loadReport(loc: Location, radiusM: Int): CompletableFuture<Report> {
        val weather = weatherApi.current(loc).asOutcome()
        val places = placesWithDescriptions(loc, radiusM).asOutcome()
        return weather.thenCombine(places) { w, p -> Report(loc, radiusM, w, p) }
    }

    private fun placesWithDescriptions(loc: Location, radiusM: Int): CompletableFuture<List<Place>> =
        placesApi.nearby(loc, radiusM).thenCompose { refs ->
            refs.map { descriptionApi.describe(it) }
                .fold(CompletableFuture.completedFuture(emptyList<Place>())) { acc, future ->
                    acc.thenCombine(future) { list, place -> list + place }
                }
        }
}
