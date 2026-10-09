package MyApp
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

object Log {
    private val full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z")
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun info(msg: String) = println("${ZonedDateTime.now().format(full)} INFO  $msg")

    fun error(msg: String) = System.err.println("${ZonedDateTime.now().format(full)} ERROR $msg")

    fun clock(epochMs: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(clockFmt)
}

class SpeedStats {
    var count = 0
        private set
    var min = Double.MAX_VALUE
        private set
    var max = 0.0
        private set
    private var sum = 0.0
    private var sumSq = 0.0

    fun add(v: Double) {
        count++
        sum += v
        sumSq += v * v
        if (v < min) min = v
        if (v > max) max = v
    }

    val mean: Double get() = if (count > 0) sum / count else 0.0
    val stddev: Double get() = if (count > 1) sqrt((sumSq / count - mean * mean).coerceAtLeast(0.0)) else 0.0
}

fun num(v: Double, digits: Int = 1): String = String.format(Locale.ROOT, "%.${digits}f", v)

fun rate(bytes: Long, ns: Long): Double = if (ns > 0) bytes / (ns / 1e9) else 0.0

private fun scaled(value: Double, units: Array<String>): String {
    var v = value
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) String.format(Locale.ROOT, "%.0f %s", v, units[i])
    else String.format(Locale.ROOT, "%.2f %s", v, units[i])
}

fun formatSize(bytes: Long): String = scaled(bytes.toDouble(), arrayOf("B", "KiB", "MiB", "GiB", "TiB"))

fun formatSpeed(bytesPerSec: Double): String =
    scaled(bytesPerSec, arrayOf("B/s", "KiB/s", "MiB/s", "GiB/s", "TiB/s"))

fun formatBits(bytesPerSec: Double): String {
    val bits = bytesPerSec * 8
    return when {
        bits >= 1e9 -> String.format(Locale.ROOT, "%.2f Gbit/s", bits / 1e9)
        bits >= 1e6 -> String.format(Locale.ROOT, "%.1f Mbit/s", bits / 1e6)
        bits >= 1e3 -> String.format(Locale.ROOT, "%.1f kbit/s", bits / 1e3)
        else -> String.format(Locale.ROOT, "%.0f bit/s", bits)
    }
}

fun formatLatency(ns: Long): String = when {
    ns < 1_000 -> "$ns нс"
    ns < 1_000_000 -> String.format(Locale.ROOT, "%.1f мкс", ns / 1e3)
    ns < 1_000_000_000 -> String.format(Locale.ROOT, "%.2f мс", ns / 1e6)
    else -> String.format(Locale.ROOT, "%.2f с", ns / 1e9)
}

fun formatElapsed(ns: Long): String {
    val s = ns / 1e9
    return when {
        s < 60 -> String.format(Locale.ROOT, "%.2f с", s)
        s < 3600 -> {
            val m = (s / 60).toLong()
            String.format(Locale.ROOT, "%d мин %04.1f с", m, s - m * 60)
        }
        else -> {
            val h = (s / 3600).toLong()
            String.format(Locale.ROOT, "%d ч %02d мин", h, ((s - h * 3600) / 60).toLong())
        }
    }
}