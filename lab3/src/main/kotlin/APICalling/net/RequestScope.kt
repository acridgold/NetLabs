package APICalling.net

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class RequestScope {
    @Volatile
    private var cancelled = false
    private val active: MutableSet<CompletableFuture<*>> = ConcurrentHashMap.newKeySet()

    val isCancelled: Boolean get() = cancelled

    fun <T> track(future: CompletableFuture<T>): CompletableFuture<T> {
        if (cancelled) {
            future.cancel(true)
            return future
        }
        active.add(future)
        future.whenComplete { _, _ -> active.remove(future) }
        if (cancelled) future.cancel(true) // cancel() мог сработать между проверкой и добавлением
        return future
    }

    fun cancel() {
        cancelled = true
        active.toList().forEach { it.cancel(true) }
    }
}

fun <T> CompletableFuture<T>.cancelScopeOnCancel(scope: RequestScope): CompletableFuture<T> {
    val self = this
    self.whenComplete { _, _ -> if (self.isCancelled) scope.cancel() }
    return self
}
