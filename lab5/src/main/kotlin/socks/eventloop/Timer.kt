package socks.eventloop

import socks.util.Log
import java.util.PriorityQueue
import kotlin.math.max


fun interface Cancellable {
    fun cancel()
}

class Timer internal constructor(internal val deadlineMs: Long, action: () -> Unit) : Cancellable {
    internal var action: (() -> Unit)? = action

    val isActive: Boolean get() = action != null


    override fun cancel() {
        action = null
    }
}

internal class TimerQueue {
    private val queue = PriorityQueue<Timer>(compareBy<Timer> { it.deadlineMs })

    fun add(timer: Timer) {
        queue.add(timer)
    }


    fun nextTimeoutMs(nowMs: Long): Long {
        while (true) {
            val t = queue.peek() ?: return 0
            if (!t.isActive) {
                queue.poll()
                continue
            }
            return max(1L, t.deadlineMs - nowMs)
        }
    }

    fun fireDue(nowMs: Long) {
        while (true) {
            val t = queue.peek() ?: return
            if (t.isActive && t.deadlineMs > nowMs) return
            queue.poll()
            val a = t.action ?: continue
            t.action = null
            try {
                a()
            } catch (e: Exception) {
                Log.warn("Ошибка в таймере: $e")
            }
        }
    }
}
