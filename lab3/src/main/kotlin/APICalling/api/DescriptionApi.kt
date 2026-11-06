package APICalling.api

import APICalling.Config
import APICalling.errorText
import APICalling.isCancellation
import APICalling.model.Place
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import javax.imageio.ImageIO

class DescriptionApi(private val client: JsonHttpClient) {

    private class Info(val description: String?, val imageUrl: String?)

    private class ImageResult(val image: BufferedImage?, val error: String?)

    fun describe(ref: PlaceRef, scope: RequestScope): CompletableFuture<Place> {
        val url = "${Config.DESCRIPTION_URL}?action=query&prop=extracts%7Cpageimages&pageids=${ref.pageId}" +
                "&exintro=1&explaintext=1&piprop=thumbnail&pithumbsize=${Config.IMAGE_SIZE_PX}" +
                "&format=json&formatversion=2"

        return client.getJson(url, scope) { json ->
            json.get("error")?.let { error ->
                throw IllegalStateException("Wikipedia: ${error.get("info")?.asText() ?: "ошибка запроса"}")
            }
            val page = json.get("query").get("pages").get(0)
            Info(
                description = page.get("extract")?.asText()?.trim()?.takeIf { it.isNotEmpty() },
                imageUrl = page.get("thumbnail")?.get("source")?.asText(),
            )
        }.handle<CompletableFuture<Place>> { info, error ->
            when {
                error != null && isCancellation(error) -> CompletableFuture.failedFuture(error)
                error != null -> CompletableFuture.completedFuture(
                    Place(ref.pageId, ref.title, ref.distanceM, description = null, error = errorText(error))
                )

                else -> loadImage(info.imageUrl, scope).thenApply { image ->
                    Place(
                        pageId = ref.pageId,
                        title = ref.title,
                        distanceM = ref.distanceM,
                        description = info.description,
                        imageUrl = info.imageUrl,
                        image = image.image,
                        imageError = image.error,
                    )
                }
            }
        }.thenCompose { it }
    }

    private fun loadImage(url: String?, scope: RequestScope): CompletableFuture<ImageResult> {
        if (url == null) return CompletableFuture.completedFuture(ImageResult(null, null))
        return client.getBytes(url, scope) { bytes ->
            ImageIO.read(ByteArrayInputStream(bytes))
                ?: throw IllegalStateException("формат картинки не поддерживается")
        }.handle<ImageResult> { image, error ->
            when {
                error == null -> ImageResult(image, null)
                isCancellation(error) -> throw if (error is CompletionException) error else CompletionException(error)
                else -> ImageResult(null, errorText(error))
            }
        }
    }
}
