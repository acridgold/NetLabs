package socks.dns

import socks.eventloop.Cancellable
import java.net.InetAddress

sealed interface ResolveResult {
    data class Resolved(val address: InetAddress) : ResolveResult
    data class Failed(val reason: String) : ResolveResult
}


interface Resolver {
    fun resolve(host: String, onResult: (ResolveResult) -> Unit): Cancellable
}
