package APICalling.net

import APICalling.Config
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ConnectException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class ApiException(val code: Int, message: String) : RuntimeException(message)

class JsonHttpClient(private val userAgent: String = Config.USER_AGENT) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val mapper = ObjectMapper()

    fun <T> getJson(url: String, scope: RequestScope, parse: (JsonNode) -> T): CompletableFuture<T> {
        val call = Call(URI.create(url))
        return send(call, scope, "application/json", HttpResponse.BodyHandlers.ofString(), Config.REQUEST_TIMEOUT_S)
            .thenApply { response -> call.process(response.statusCode()) { parse(mapper.readTree(response.body())) } }
    }

    fun <T> getBytes(url: String, scope: RequestScope, process: (ByteArray) -> T): CompletableFuture<T> {
        val call = Call(URI.create(url))
        return send(call, scope, "image/*", HttpResponse.BodyHandlers.ofByteArray(), Config.IMAGE_TIMEOUT_S)
            .thenApply { response -> call.process(response.statusCode()) { process(response.body()) } }
    }

    private inner class Call(val uri: URI) {
        val label: String = describe(uri)
        private val started = System.nanoTime()

        fun ms(): Long = (System.nanoTime() - started) / 1_000_000

        fun <R> process(status: Int, block: () -> R): R = try {
            val result = block()
            ApiLog.ok("$label -> HTTP $status (${ms()} ms)")
            result
        } catch (e: Exception) {
            val reason = (e.message ?: e.javaClass.simpleName).lineSequence().first().take(160)
            ApiLog.error("$label -> HTTP $status, ответ не обработан: $reason (${ms()} ms)")
            throw ApiException(status, "ответ от ${uri.host} не обработан: $reason")
        }
    }

    private fun <B> send(
        call: Call,
        scope: RequestScope,
        accept: String,
        handler: HttpResponse.BodyHandler<B>,
        timeoutSeconds: Long,
    ): CompletableFuture<HttpResponse<B>> {
        if (scope.isCancelled) {
            ApiLog.cancelled("${call.label} -> отменён до отправки")
            return CompletableFuture.failedFuture(CancellationException("Запрос отменён"))
        }
        val request = HttpRequest.newBuilder(call.uri)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("User-Agent", userAgent)
            .header("Accept", accept)
            .GET()
            .build()

        val raw = scope.track(http.sendAsync(request, handler))
        return raw.handle<HttpResponse<B>> { response, error ->
            when {
                error != null -> {
                    val cause = if (error is CompletionException) error.cause ?: error else error
                    if (cause is CancellationException) {
                        ApiLog.cancelled("${call.label} -> отменён (${call.ms()} ms)")
                        throw CompletionException(cause)
                    }
                    val text = when (cause) {
                        is HttpTimeoutException -> "превышено время ожидания ответа от ${call.uri.host}"
                        is ConnectException -> "нет соединения с ${call.uri.host}"
                        else -> cause.message ?: cause.javaClass.simpleName
                    }
                    ApiLog.error("${call.label} -> FAILED: $text (${call.ms()} ms)")
                    throw ApiException(0, text)
                }

                response.statusCode() !in 200..299 -> {
                    ApiLog.error("${call.label} -> HTTP ${response.statusCode()} (${call.ms()} ms)")
                    throw ApiException(response.statusCode(), "HTTP ${response.statusCode()} от ${call.uri.host}")
                }

                else -> response
            }
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
