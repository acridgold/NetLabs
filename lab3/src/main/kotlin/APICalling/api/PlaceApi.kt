package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import APICalling.net.Urls.coord
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture

class PlacesApi(private val client: JsonHttpClient) {
    fun nearby(loc: Location, radiusM: Int, scope: RequestScope): CompletableFuture<List<PlaceRef>> {
        val radius = radiusM.coerceIn(10, Config.MAX_RADIUS_M)
        val url = "${Config.PLACES_URL}?action=query&list=geosearch" +
                "&gscoord=${coord(loc.lat)}%7C${coord(loc.lon)}" +
                "&gsradius=$radius&gslimit=${Config.MAX_PLACES}&format=json&formatversion=2"

        return client.getJson(url, scope) { json -> parseNearby(json) }
    }

    internal fun parseNearby(json: JsonNode): List<PlaceRef> {
        json.requireObject().failIfMediaWikiError()
        val found = json.objectField("query").arrayField("geosearch")
        if (found.size() == 0) return emptyList()

        val refs = found.mapNotNull { parseItem(it) }.distinctBy { it.pageId }
        if (refs.isEmpty()) invalid("ни один из ${found.size()} результатов не прошёл проверку")
        return refs
    }

    private fun parseItem(item: JsonNode): PlaceRef? {
        if (!item.isObject) return null
        val id = item.get("pageid")?.takeIf { it.isIntegralNumber && it.asLong() > 0 }?.asLong() ?: return null
        val title = item.optionalTextField("title") ?: return null
        val dist = item.get("dist")?.takeIf { it.isNumber }?.doubleValue()
            ?.takeIf { it.isFinite() && it in 0.0..(Config.MAX_RADIUS_M * 2.0) } ?: return null
        return PlaceRef(pageId = id, title = title, distanceM = dist.toInt())
    }
}
