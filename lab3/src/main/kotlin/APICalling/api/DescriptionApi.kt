package APICalling.api

import APICalling.Config
import APICalling.model.Place
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import java.util.concurrent.CompletableFuture

class DescriptionApi(private val client: JsonHttpClient) {
    fun describe(ref: PlaceRef): CompletableFuture<Place> =
        client.getJson(
            "${Config.DESCRIPTION_URL}?action=wbgetentities&ids=${ref.wikidataId}" +
                    "&props=descriptions&languages=ru%7Cen&format=json"
        ).thenApply { json ->
            val descriptions = json.get("entities").get(ref.wikidataId).get("descriptions")
            val text = descriptions.get("ru")?.get("value")?.asText()
                ?: descriptions.get("en")?.get("value")?.asText()
            Place(ref.name, ref.kind, text)
        }.exceptionally {
            Place(ref.name, ref.kind, null)
        }
}