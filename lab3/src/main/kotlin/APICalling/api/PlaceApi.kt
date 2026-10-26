package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import APICalling.net.Urls.enc
import java.util.Locale
import java.util.concurrent.CompletableFuture

class PlacesApi(private val client: JsonHttpClient) {

    private val kinds = mapOf(
        "Q570116" to "достопримечательность",
        "Q33506" to "музей",
        "Q4989906" to "памятник",
        "Q23413" to "замок",
        "Q16970" to "храм",
        "Q22698" to "парк",
    )

    fun nearby(loc: Location): CompletableFuture<List<PlaceRef>> {
        val radiusKm = String.format(Locale.US, "%.2f", Config.SEARCH_RADIUS_M / 1000.0)
        val lon = String.format(Locale.US, "%.5f", loc.lon)
        val lat = String.format(Locale.US, "%.5f", loc.lat)
        val types = kinds.keys.joinToString(" ") { "wd:$it" }

        val sparql = """
            SELECT ?place ?placeLabel ?type ?dist WHERE {
              hint:Query hint:optimizer "None" .
              SERVICE wikibase:around {
                ?place wdt:P625 ?location .
                bd:serviceParam wikibase:center "Point($lon $lat)"^^geo:wktLiteral .
                bd:serviceParam wikibase:radius "$radiusKm" .
                bd:serviceParam wikibase:distance ?dist .
              }
              VALUES ?type { $types }
              ?place wdt:P31/wdt:P279* ?type .
              SERVICE wikibase:label { bd:serviceParam wikibase:language "ru,en". }
            }
            ORDER BY ?dist
            LIMIT 60
        """.trimIndent()

        return client.getJson("${Config.PLACES_URL}?query=${enc(sparql)}&format=json")
            .thenApply { json ->
                json.get("results").get("bindings")
                    .map { row ->
                        val id = row.get("place").get("value").asText().substringAfterLast('/')
                        val label = row.get("placeLabel").get("value").asText()
                        val typeId = row.get("type").get("value").asText().substringAfterLast('/')
                        PlaceRef(name = label, kind = kinds[typeId] ?: "место", wikidataId = id)
                    }
                    .filter { !it.name.matches(Regex("Q\\d+")) }
                    .distinctBy { it.wikidataId }
                    .take(Config.MAX_PLACES)
            }
    }
}