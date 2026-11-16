package APICalling.api

import APICalling.Config
import APICalling.errorText
import APICalling.isCancellation
import APICalling.model.Place
import APICalling.model.PlaceRef
import APICalling.net.JsonHttpClient
import APICalling.net.RequestScope
import com.fasterxml.jackson.databind.JsonNode
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import javax.imageio.ImageIO

class DescriptionApi(private val client: JsonHttpClient) {

    internal class PageInfo(val description: String?, val imageUrl: String?, val imageProblem: String?)

    private class ImageResult(val image: BufferedImage?, val error: String?)

    fun describe(ref: PlaceRef, scope: RequestScope): CompletableFuture<Place> {
        val url = "${Config.DESCRIPTION_URL}?action=query&prop=extracts%7Cpageimages&pageids=${ref.pageId}" +
                "&exintro=1&explaintext=1&piprop=thumbnail&pithumbsize=${Config.IMAGE_SIZE_PX}" +
                "&format=json&formatversion=2"

        return client.getJson(url, scope) { json -> parsePage(json, ref.pageId) }
            .handle<CompletableFuture<Place>> { info, error ->
                when {
                    error != null && isCancellation(error) -> CompletableFuture.failedFuture(error)
                    error != null -> CompletableFuture.completedFuture(
                        Place(ref.pageId, ref.title, ref.distanceM, description = null, error = errorText(error))
                    )
                    info.imageProblem != null -> CompletableFuture.completedFuture(
                        Place(ref.pageId, ref.title, ref.distanceM, info.description, imageError = info.imageProblem)
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

    internal fun parsePage(json: JsonNode, expectedId: Long): PageInfo {
        json.requireObject().failIfMediaWikiError()
        val pages = json.objectField("query").arrayField("pages")
        if (pages.size() == 0) invalid("Wikipedia не вернула ни одной страницы")

        val page = pages.get(0).requireObject("страница")
        if (page.get("missing")?.asBoolean() == true) invalid("статья не найдена (id $expectedId)")
        val id = page.longField("pageid")
        if (id != expectedId) invalid("ответ относится к другой статье ($id вместо $expectedId)")

        val extract = page.get("extract")
        val description = when {
            extract == null || extract.isNull -> null
            extract.isTextual -> extract.asText().trim().takeIf { it.isNotEmpty() }
            else -> invalid("поле «extract» не является текстом")
        }

        val thumbnail = page.get("thumbnail")
        if (thumbnail == null || thumbnail.isNull) return PageInfo(description, null, null)
        val source = thumbnail.takeIf { it.isObject }?.optionalTextField("source")
        return if (source != null && isHttpsUrl(source)) {
            PageInfo(description, source, null)
        } else {
            PageInfo(description, null, "некорректный адрес картинки")
        }
    }

    private fun isHttpsUrl(text: String): Boolean = try {
        val uri = URI(text)
        uri.scheme == "https" && !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    private fun loadImage(url: String?, scope: RequestScope): CompletableFuture<ImageResult> {
        if (url == null) return CompletableFuture.completedFuture(ImageResult(null, null))
        return client.getBytes(url, scope) { bytes -> decode(bytes) }
            .handle<ImageResult> { image, error ->
                when {
                    error == null -> ImageResult(image, null)
                    isCancellation(error) -> throw if (error is CompletionException) error else CompletionException(error)
                    else -> ImageResult(null, errorText(error))
                }
            }
    }

    internal fun decode(bytes: ByteArray): BufferedImage {
        if (bytes.isEmpty()) invalid("пустой ответ вместо картинки")
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: invalid("формат картинки не поддерживается")
        if (image.width !in 1..MAX_IMAGE_SIDE || image.height !in 1..MAX_IMAGE_SIDE) {
            invalid("недопустимый размер картинки ${image.width}x${image.height}")
        }
        return image
    }

    private companion object {
        const val MAX_IMAGE_SIDE = 4096
    }
}
