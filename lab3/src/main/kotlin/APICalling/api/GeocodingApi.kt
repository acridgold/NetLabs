package APICalling.api

import APICalling.Config
import APICalling.model.Location
import APICalling.net.JsonHttpClient
import APICalling.net.Urls.enc
import java.util.concurrent.CompletableFuture

class GeocodingApi(private val client: JsonHttpClient) {
    fun search(query: String): CompletableFuture<List<Location>> =
        client.getJson("${Config.GEOCODING_URL}?name=${enc(query)}&count=8&language=ru&format=json")
            .thenApply { json ->
                val results = json.get("results")
                if (results == null) {
                    emptyList()
                } else {
                    results.map { node ->
                        Location(
                            name = node.get("name").asText(),
                            admin = node.get("admin1")?.asText(),
                            country = node.get("country")?.asText(),
                            lat = node.get("latitude").asDouble(),
                            lon = node.get("longitude").asDouble(),
                        )
                    }
                }
            }
}