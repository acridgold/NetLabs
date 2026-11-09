package socks.util

import java.io.Closeable
import java.io.IOException

internal fun closeQuietly(c: Closeable?) {
    try {
        c?.close()
    } catch (_: IOException) {
    }
}
