package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import APICalling.net.Urls.enc
import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CompletableFuture

class GeocodingApi(private val client: JsonHttpClient) {

    fun search(query: String, scope: RequestScope): CompletableFuture<List<Location>> {
        val key = Config.graphHopperKey.trim()
        if (key.isEmpty()) {
            return CompletableFuture.failedFuture(
                IllegalStateException("Введите ключ GraphHopper (поле слева, переменная GRAPHHOPPER_KEY или файл secrets.properties)")
            )
        }
        val url = "${Config.GEOCODING_URL}?q=${enc(query)}&locale=ru&limit=${Config.GEOCODE_LIMIT}&key=${enc(key)}"
        return client.getJson(url, scope) { json -> parseHits(json) }
    }

    internal fun parseHits(json: JsonNode): List<Location> {
        val hits = json.requireObject().arrayField("hits")
        if (hits.size() == 0) return emptyList()

        val seen = HashSet<String>()
        val locations = hits.mapNotNull { parseHit(it) }.filter { seen.add(it.uniqueKey) }
        if (locations.isEmpty()) {
            invalid("ни один из ${hits.size()} результатов не прошёл проверку (нет корректных координат)")
        }
        return locations
    }

    private fun parseHit(hit: JsonNode): Location? {
        if (!hit.isObject) return null
        val point = hit.get("point")?.takeIf { it.isObject } ?: return null
        val lat = point.coordinate("lat", -90.0..90.0) ?: return null
        val lon = point.coordinate("lng", -180.0..180.0) ?: return null

        val street = hit.optionalTextField("street")
        val house = hit.optionalTextField("housenumber")
        val city = hit.optionalTextField("city")
        val name = hit.optionalTextField("name")
            ?: listOfNotNull(street, house).joinToString(" ").ifBlank { null }
            ?: city
            ?: "Без названия"

        return Location(
            name = name,
            street = street,
            houseNumber = house,
            city = city,
            state = hit.optionalTextField("state"),
            country = hit.optionalTextField("country"),
            postcode = hit.optionalTextField("postcode"),
            osmKey = hit.optionalTextField("osm_key"),
            osmValue = hit.optionalTextField("osm_value"),
            osmType = hit.optionalTextField("osm_type"),
            osmId = hit.get("osm_id")?.takeIf { it.isIntegralNumber }?.asLong(),
            lat = lat,
            lon = lon,
        )
    }

    private fun JsonNode.coordinate(name: String, range: ClosedFloatingPointRange<Double>): Double? {
        val node = get(name)
        if (node == null || !node.isNumber) return null
        return node.doubleValue().takeIf { it.isFinite() && it in range }
    }
}
