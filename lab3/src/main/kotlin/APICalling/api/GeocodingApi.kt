package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.net.JsonHttpClient
import APICalling.net.Urls.enc
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture

class GeocodingApi(private val client: JsonHttpClient) {

    fun search(query: String): CompletableFuture<List<Location>> {
        val key = Config.graphHopperKey.trim()
        if (key.isEmpty()) {
            return CompletableFuture.failedFuture(
                IllegalStateException("Введите ключ GraphHopper (поле слева или переменная окружения GRAPHHOPPER_KEY)")
            )
        }
        val url = "${Config.GEOCODING_URL}?q=${enc(query)}&locale=ru&limit=${Config.GEOCODE_LIMIT}&key=${enc(key)}"
        return client.getJson(url).thenApply { json -> parseHits(json) }
    }

    private fun parseHits(json: JsonNode): List<Location> {
        val hits = json.get("hits") ?: return emptyList()
        val seen = HashSet<String>()
        return hits.mapNotNull { hit -> parseHit(hit) }.filter { seen.add(it.uniqueKey) }
    }

    private fun parseHit(hit: JsonNode): Location? {
        val point = hit.get("point") ?: return null
        val lat = point.get("lat")?.asDouble() ?: return null
        val lon = point.get("lng")?.asDouble() ?: return null

        val street = hit.text("street")
        val house = hit.text("housenumber")
        val city = hit.text("city")
        val name = hit.text("name")
            ?: listOfNotNull(street, house).joinToString(" ").ifBlank { null }
            ?: city
            ?: "Без названия"

        return Location(
            name = name,
            street = street,
            houseNumber = house,
            city = city,
            state = hit.text("state"),
            country = hit.text("country"),
            postcode = hit.text("postcode"),
            osmKey = hit.text("osm_key"),
            osmValue = hit.text("osm_value"),
            osmType = hit.text("osm_type"),
            osmId = hit.get("osm_id")?.takeIf { !it.isNull }?.asLong(),
            lat = lat,
            lon = lon,
        )
    }

    private fun JsonNode.text(field: String): String? =
        get(field)?.takeIf { !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
}
