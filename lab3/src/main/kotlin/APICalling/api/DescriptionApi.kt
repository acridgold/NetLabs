package APICalling.api

import APICalling.Config
import APICalling.model.Place
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import java.util.concurrent.CompletableFuture

class DescriptionApi(private val client: JsonHttpClient) {

    private class Info(val description: String?, val imageUrl: String?)

    fun describe(ref: PlaceRef): CompletableFuture<Place> =
        client.getJson(
            "${Config.DESCRIPTION_URL}?action=query&prop=extracts%7Cpageimages&pageids=${ref.pageId}" +
                    "&exintro=1&explaintext=1&exsentences=3&piprop=thumbnail&pithumbsize=${Config.IMAGE_SIZE_PX}" +
                    "&format=json&formatversion=2"
        )
            .thenApply { json ->
                val page = json.get("query").get("pages").get(0)
                Info(
                    description = page.get("extract")?.asText()?.trim()?.takeIf { it.isNotEmpty() },
                    imageUrl = page.get("thumbnail")?.get("source")?.asText(),
                )
            }
            .thenCompose { info ->
                loadImage(info.imageUrl).thenApply { bytes ->
                    Place(ref.title, ref.distanceM, info.description, info.imageUrl, bytes)
                }
            }
            .exceptionally { Place(ref.title, ref.distanceM, null, null, null) }

    private fun loadImage(url: String?): CompletableFuture<ByteArray?> =
        if (url == null) CompletableFuture.completedFuture(null)
        else client.getBytes(url).handle<ByteArray?> { bytes, error -> if (error == null) bytes else null }
}
