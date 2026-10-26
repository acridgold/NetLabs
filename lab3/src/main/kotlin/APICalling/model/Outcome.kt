package APICalling.model

import java.util.concurrent.CompletableFuture

class Outcome<T>(val value: T?, val error: String?)

fun <T> CompletableFuture<T>.asOutcome(): CompletableFuture<Outcome<T>> =
    handle<Outcome<T>> { value, ex ->
        if (ex == null) Outcome(value, null)
        else Outcome(null, (ex.cause ?: ex).message ?: ex.javaClass.simpleName)
    }
