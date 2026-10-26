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
import java.util.*
import java.util.concurrent.CompletableFuture

class JsonHttpClient(private val userAgent: String = Config.USER_AGENT) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    private val mapper = ObjectMapper()

    fun getJson(url: String): CompletableFuture<JsonNode> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .GET()
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { resp ->
                check(resp.statusCode() in 200..299) { "HTTP ${resp.statusCode()} от ${request.uri().host}" }
                mapper.readTree(resp.body())
            }
    }
}

object Urls {
    fun enc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8)

    fun coord(v: Double): String = String.format(Locale.US, "%.4f", v)
}