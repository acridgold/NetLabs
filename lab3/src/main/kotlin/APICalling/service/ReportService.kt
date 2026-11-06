package APICalling.service

import APICalling.api.DescriptionApi
import APICalling.api.PlacesApi
import APICalling.api.WeatherApi
import APICalling.model.Location
import APICalling.model.Place
import APICalling.model.Report
import APICalling.model.asOutcome
import APICalling.net.RequestScope
import APICalling.net.cancelScopeOnCancel
import java.util.concurrent.CompletableFuture

class ReportService(
    private val weatherApi: WeatherApi,
    private val placesApi: PlacesApi,
    private val descriptionApi: DescriptionApi,
) {
    fun loadReport(loc: Location, radiusM: Int): CompletableFuture<Report> {
        val scope = RequestScope()
        val weather = weatherApi.forecast(loc, scope).asOutcome()
        val places = placesWithDescriptions(loc, radiusM, scope).asOutcome()
        return weather.thenCombine(places) { w, p -> Report(loc, radiusM, w, p) }
            .cancelScopeOnCancel(scope)
    }

    private fun placesWithDescriptions(
        loc: Location,
        radiusM: Int,
        scope: RequestScope
    ): CompletableFuture<List<Place>> =
        placesApi.nearby(loc, radiusM, scope).thenCompose { refs ->
            refs.map { descriptionApi.describe(it, scope) }
                .fold(CompletableFuture.completedFuture(emptyList<Place>())) { acc, future ->
                    acc.thenCombine(future) { list, place -> list + place }
                }
        }
}
