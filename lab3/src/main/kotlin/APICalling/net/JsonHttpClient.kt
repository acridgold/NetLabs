package APICalling.net

import APICalling.Config
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture

class ApiException(val code: Int, message: String) : RuntimeException(message)

class JsonHttpClient(private val userAgent: String = Config.USER_AGENT) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mapper = ObjectMapper()

    fun getJson(url: String): CompletableFuture<JsonNode> =
        execute(url, "application/json", HttpResponse.BodyHandlers.ofString())
            .thenApply { body -> mapper.readTree(body) }

    fun getBytes(url: String): CompletableFuture<ByteArray> =
        execute(url, "image/*", HttpResponse.BodyHandlers.ofByteArray())

    private fun <B> execute(url: String, accept: String, handler: HttpResponse.BodyHandler<B>): CompletableFuture<B> {
        val uri = URI.create(url)
        val label = describe(uri)
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .GET()
            .build()
        val started = System.nanoTime()

        return http.sendAsync(request, handler)
            .whenComplete { response, error ->
                val ms = (System.nanoTime() - started) / 1_000_000
                when {
                    error != null -> ApiLog.error("$label -> FAILED: ${(error.cause ?: error).message} (${ms} ms)")
                    response.statusCode() !in 200..299 -> ApiLog.error("$label -> HTTP ${response.statusCode()} (${ms} ms)")
                    else -> ApiLog.ok("$label -> HTTP ${response.statusCode()} (${ms} ms)")
                }
            }
            .thenApply { response ->
                if (response.statusCode() !in 200..299) {
                    throw ApiException(response.statusCode(), "HTTP ${response.statusCode()} от ${uri.host}")
                }
                response.body()
            }
    }

    private fun describe(uri: URI): String {
        val query = uri.rawQuery?.let { Regex("(?i)(key=)[^&]*").replace(it, "\$1***") }
        val text = "GET ${uri.host}${uri.rawPath}" + (if (query != null) "?$query" else "")
        return if (text.length > 160) text.take(157) + "..." else text
    }
}

object Urls {
    fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    fun coord(v: Double): String = String.format(Locale.US, "%.4f", v)
}
