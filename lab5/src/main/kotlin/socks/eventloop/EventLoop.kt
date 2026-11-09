package socks.eventloop

import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector


interface ChannelHandler {
    fun onReady(key: SelectionKey)


    fun onError(cause: Exception) {}
}


class EventLoop : AutoCloseable {
    private val selector: Selector = Selector.open()
    private val timers = TimerQueue()

    @Volatile
    private var running = true

    fun register(channel: SelectableChannel, ops: Int, handler: ChannelHandler): SelectionKey {
        channel.configureBlocking(false)
        return channel.register(selector, ops, handler)
    }

    fun schedule(delayMs: Long, action: () -> Unit): Timer {
        val timer = Timer(nowMs() + delayMs, action)
        timers.add(timer)
        return timer
    }

    fun run() {
        while (running) {
            selector.select(timers.nextTimeoutMs(nowMs()))
            timers.fireDue(nowMs())

            val keys = selector.selectedKeys().iterator()
            while (keys.hasNext()) {
                val key = keys.next()
                keys.remove()
                dispatch(key)
            }
        }
    }

    fun stop() {
        running = false
        selector.wakeup()
    }

    override fun close() {
        selector.close()
    }

    private fun dispatch(key: SelectionKey) {
        val handler = key.attachment() as? ChannelHandler ?: return
        try {
            if (key.isValid) handler.onReady(key)
        } catch (_: CancelledKeyException) {
        } catch (e: Exception) {
            try {
                handler.onError(e)
            } catch (_: Exception) {
            }
        }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000
}
