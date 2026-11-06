package APICalling

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

fun rootCause(t: Throwable): Throwable {
    var current = t
    while ((current is CompletionException || current is ExecutionException) && current.cause != null) {
        current = current.cause!!
    }
    return current
}

fun isCancellation(t: Throwable?): Boolean = t != null && rootCause(t) is CancellationException

fun errorText(t: Throwable): String {
    val cause = rootCause(t)
    val message = cause.message?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
    return message ?: cause.javaClass.simpleName
}
