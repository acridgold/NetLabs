package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import APICalling.net.Urls.coord
import java.util.concurrent.CompletableFuture

class PlacesApi(private val client: JsonHttpClient) {
    fun nearby(loc: Location, radiusM: Int): CompletableFuture<List<PlaceRef>> {
        val radius = radiusM.coerceIn(10, Config.MAX_RADIUS_M)
        val url = "${Config.PLACES_URL}?action=query&list=geosearch" +
                "&gscoord=${coord(loc.lat)}%7C${coord(loc.lon)}" +
                "&gsradius=$radius&gslimit=${Config.MAX_PLACES}&format=json&formatversion=2"

        return client.getJson(url).thenApply { json ->
            json.get("error")?.let { error ->
                throw IllegalStateException("Wikipedia: ${error.get("info")?.asText() ?: "ошибка запроса"}")
            }
            val found = json.get("query")?.get("geosearch") ?: return@thenApply emptyList<PlaceRef>()
            found.map { item ->
                PlaceRef(
                    pageId = item.get("pageid").asLong(),
                    title = item.get("title").asText(),
                    distanceM = item.get("dist").asDouble().toInt(),
                )
            }
        }
    }
}
