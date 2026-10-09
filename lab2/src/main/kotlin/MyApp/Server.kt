package MyApp

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

const val DEFAULT_MAX_CLIENTS = 32

private const val REPORT_INTERVAL_SEC = 3L
private const val SOCKET_TIMEOUT_MS = 30_000
private const val MAX_FS_NAME_BYTES = 200

private class Session(
    val id: Int,
    val remote: String,
    val fileName: String,
    val expected: Long,
    private val headerNs: Long,
) {
    private val tag = "[#$id $remote]"
    private val startNs = System.nanoTime()
    private val startWallMs = System.currentTimeMillis()

    @Volatile
    private var received = 0L
    @Volatile
    private var readNs = 0L
    @Volatile
    private var writeNs = 0L
    @Volatile
    private var reads = 0L
    @Volatile
    private var firstByteNs = -1L
    private val intervalMaxReadNs = AtomicLong()

    private var lastNs = startNs
    private var lastBytes = 0L
    private var lastReads = 0L
    private var lastReadNs = 0L
    private var lastWriteNs = 0L
    private var maxReadNs = 0L
    private var reported = false
    private val stats = SpeedStats()

    val receivedBytes: Long get() = received

    fun onRead(ns: Long, bytes: Int) {
        if (reads == 0L) {
            firstByteNs = ns // ожидание первого байта ≈ RTT + реакция клиента, в «паузы» не входит
        } else {
            readNs += ns
            intervalMaxReadNs.accumulateAndGet(ns) { a, b -> if (a > b) a else b }
        }
        reads++
        received += bytes
    }

    fun onWrite(ns: Long) {
        writeNs += ns
    }

    @Synchronized
    fun report(): Double {
        val now = System.nanoTime()
        val bytes = received
        val rNs = readNs
        val wNs = writeNs
        val nReads = reads
        val dt = (now - lastNs) / 1e9
        val elapsed = (now - startNs) / 1e9
        val dBytes = bytes - lastBytes
        val dReads = nReads - lastReads
        val dRead = rNs - lastReadNs
        val dWrite = wNs - lastWriteNs
        val inst = if (dt > 0) dBytes / dt else 0.0
        val avg = if (elapsed > 0) bytes / elapsed else 0.0
        val intMax = intervalMaxReadNs.getAndSet(0)
        maxReadNs = maxOf(maxReadNs, intMax)
        if (dt >= 1.0) stats.add(inst)

        val pct = if (expected > 0) bytes * 100.0 / expected else 100.0
        val eta = if (avg > 0 && bytes < expected)
            ", ETA ${formatElapsed(((expected - bytes) / avg * 1e9).toLong())}" else ""
        Log.info(
            "$tag $fileName: ${num(pct)}% (${formatSize(bytes)} / ${formatSize(expected)}), прошло ${
                formatElapsed(
                    now - startNs
                )
            }$eta"
        )
        Log.info(
            "$tag   скорость: мгновенная ${formatSpeed(inst)} (${formatBits(inst)}), средняя ${formatSpeed(avg)} (${
                formatBits(
                    avg
                )
            })"
        )
        if (dReads > 0) {
            val net = if (dt > 0) dRead / (dt * 1e9) * 100 else 0.0
            val disk = if (dt > 0) dWrite / (dt * 1e9) * 100 else 0.0
            Log.info(
                "$tag   диагностика: read() ${dReads} шт., средний размер ${formatSize(dBytes / dReads)}, " +
                        "ср. ожидание ${formatLatency(dRead / dReads)}, макс. пауза ${formatLatency(intMax)}; " +
                        "доля времени: сеть ${num(net, 0)}%, диск ${num(disk, 0)}% -> ${bottleneck(net, disk)}"
            )
        } else {
            Log.info("$tag   диагностика: за ${num(dt)} с не пришло ни одного байта (простой)")
        }

        lastNs = now
        lastBytes = bytes
        lastReads = nReads
        lastReadNs = rNs
        lastWriteNs = wNs
        reported = true
        return inst
    }

    @Synchronized
    fun reportIfNeverReported() {
        if (!reported) report()
    }

    @Synchronized
    fun finish(ok: Boolean, reason: String?) {
        reportIfNeverReported()
        val endNs = System.nanoTime()
        val endWallMs = System.currentTimeMillis()
        val totalNs = endNs - startNs
        val bytes = received
        maxReadNs = maxOf(maxReadNs, intervalMaxReadNs.getAndSet(0))
        val timedReads = maxOf(reads - 1, 1L)
        fun share(ns: Long) = if (totalNs > 0) ns * 100.0 / totalNs else 0.0
        val net = share(readNs)
        val disk = share(writeNs)
        val other = (100 - net - disk).coerceAtLeast(0.0)

        val verdict = if (ok) "УСПЕХ" else "ОШИБКА" + (reason?.let { " ($it)" } ?: "")
        Log.info("$tag ИТОГ '$fileName': $verdict")
        Log.info(
            "$tag   объём: ${formatSize(bytes)} из ${formatSize(expected)}; " +
                    "время: ${Log.clock(startWallMs)} -> ${Log.clock(endWallMs)}, длительность ${formatElapsed(totalNs)}; " +
                    "средняя скорость ${formatSpeed(rate(bytes, totalNs))} (${formatBits(rate(bytes, totalNs))})"
        )
        if (stats.count > 0) {
            Log.info(
                "$tag   скорость по интервалам (${stats.count} шт.): мин ${formatSpeed(stats.min)}, " +
                        "макс ${formatSpeed(stats.max)}, σ ${formatSpeed(stats.stddev)}"
            )
        }
        val fb = if (firstByteNs >= 0) formatLatency(firstByteNs) else "н/д"
        Log.info(
            "$tag   задержки: от accept до заголовка ${formatLatency(headerNs)}, " +
                    "первый байт после READY $fb, " +
                    "ср. ожидание read() ${formatLatency(readNs / timedReads)}, макс. пауза ${formatLatency(maxReadNs)}"
        )
        Log.info(
            "$tag   доли времени: ожидание сети ${num(net)}%, запись на диск ${num(disk)}%, прочее ${num(other)}%; " +
                    "read() ${reads} шт., средний размер ${formatSize(if (reads > 0) bytes / reads else 0)}"
        )
    }
}

private fun bottleneck(netShare: Double, diskShare: Double): String = when {
    diskShare >= 50 -> "узкое место: диск сервера"
    netShare >= 50 -> "узкое место: сеть/отправитель"
    else -> "узкое место: CPU сервера/прочее"
}

private val sessions = ConcurrentHashMap<Int, Session>()
private val nextId = AtomicInteger(1)

fun runServer(port: Int, maxClients: Int = DEFAULT_MAX_CLIENTS) {
    require(maxClients >= 1) { "maxClients должен быть не меньше 1" }
    val uploads = Paths.get("uploads").toAbsolutePath().normalize()
    Files.createDirectories(uploads)
    val protocol: TransferProtocol = TcpTransferProtocol()

    val ticker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "speed-reporter").apply { isDaemon = true }
    }
    ticker.scheduleAtFixedRate({
        try {
            val active = sessions.values.toList()
            var sum = 0.0
            for (s in active) sum += runCatching { s.report() }.getOrDefault(0.0)
            if (active.size > 1) {
                Log.info(
                    "[сервер] активных клиентов: ${active.size} из $maxClients, суммарная скорость ${
                        formatSpeed(
                            sum
                        )
                    } (${formatBits(sum)})"
                )
            }
        } catch (ignored: Throwable) {
        }
    }, REPORT_INTERVAL_SEC, REPORT_INTERVAL_SEC, TimeUnit.SECONDS)

    val slots = Semaphore(maxClients)
    val threadNo = AtomicInteger(1)
    val workers = Executors.newFixedThreadPool(maxClients) { r ->
        Thread(r, "client-worker-${threadNo.getAndIncrement()}")
    }

    ServerSocket(port).use { server ->
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.close() } })
        Log.info(
            "Сервер слушает порт ${server.localPort}, каталог загрузок: $uploads, " +
                    "максимум одновременных клиентов: $maxClients, отчёты каждые $REPORT_INTERVAL_SEC с, " +
                    "часовой пояс ${ZoneId.systemDefault()}"
        )
        while (!server.isClosed) {
            if (!slots.tryAcquire()) {
                Log.info("[сервер] занято $maxClients из $maxClients слотов: новые клиенты ждут освобождения")
                try {
                    slots.acquire()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            val socket = try {
                server.accept()
            } catch (e: SocketException) {
                slots.release()
                if (server.isClosed) break
                Log.error("Ошибка accept: ${e.message}")
                continue
            } catch (e: IOException) {
                slots.release()
                Log.error("Ошибка accept: ${e.message}")
                continue
            }
            val id = nextId.getAndIncrement()
            try {
                workers.execute {
                    try {
                        handleClient(socket, id, uploads, protocol)
                    } finally {
                        slots.release()
                    }
                }
            } catch (e: Throwable) {
                slots.release()
                Log.error("Не удалось запустить обработчик клиента #$id: ${e.message}")
                runCatching { socket.close() }
            }
        }
    }
    workers.shutdown()
    try {
        workers.awaitTermination(30, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
    ticker.shutdownNow()
}

private fun remoteOf(s: Socket): String {
    val a = s.inetAddress?.hostAddress ?: "?"
    return if (a.contains(':')) "[$a]:${s.port}" else "$a:${s.port}"
}

private fun handleClient(socket: Socket, id: Int, uploads: Path, protocol: TransferProtocol) {
    val acceptNs = System.nanoTime()
    val remote = remoteOf(socket)
    var target: Path? = null
    var session: Session? = null
    var success = false
    var failure: String? = null
    try {
        socket.use { s ->
            s.soTimeout = SOCKET_TIMEOUT_MS
            s.tcpNoDelay = true
            val input = DataInputStream(BufferedInputStream(s.getInputStream(), BUFFER_SIZE))
            val output = DataOutputStream(s.getOutputStream())

            // --- заголовок ---
            val header = protocol.readHeader(input)
                ?: return@use
            val size = header.fileSize
            val headerNs = System.nanoTime() - acceptNs

            val path = createTargetFile(uploads, header.fileName)
            target = path
            val sess = Session(id, remote, path.fileName.toString(), size, headerNs)
            session = sess
            sessions[id] = sess
            Log.info("[#$id $remote] принимаем '${sess.fileName}', размер ${formatSize(size)} ($size байт), поток ${Thread.currentThread().name}")

            // --- готовность ---
            protocol.writeReply(output, Reply.READY)

            // --- тело ---
            Files.newOutputStream(path).use { fileOut ->
                val buf = ByteArray(BUFFER_SIZE)
                var remaining = size
                while (remaining > 0) {
                    val t0 = System.nanoTime()
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    val t1 = System.nanoTime()
                    if (n < 0) break
                    fileOut.write(buf, 0, n)
                    val t2 = System.nanoTime()
                    sess.onRead(t1 - t0, n)
                    sess.onWrite(t2 - t1)
                    remaining -= n
                }
            }

            success = sess.receivedBytes == size
            if (!success) failure = "получено ${sess.receivedBytes} из $size байт"
            protocol.writeReply(output, if (success) Reply.OK else Reply.FAIL)
        }
    } catch (e: Throwable) {
        failure = "${e.javaClass.simpleName}: ${e.message}"
        Log.error("[#$id $remote] ошибка: $failure")
    } finally {
        sessions.remove(id)
        runCatching { session?.finish(success, failure) }
        if (!success) target?.let { runCatching { Files.deleteIfExists(it) } }
        runCatching { socket.close() }
    }
}

private fun createTargetFile(uploads: Path, rawName: String): Path {
    val base = sanitize(rawName)
    var attempt = 0
    while (true) {
        val candidate = if (attempt == 0) base else withSuffix(base, attempt)
        val path = uploads.resolve(candidate).normalize()
        if (path.parent != uploads) throw IOException("недопустимое имя файла")
        try {
            return Files.createFile(path)
        } catch (ignored: FileAlreadyExistsException) {
            attempt++
        }
    }
}

private fun sanitize(raw: String): String {
    var name = raw.substringAfterLast('/').substringAfterLast('\\')
    name = name.filter { it.code >= 0x20 && it.code != 0x7F }.trim()
    if (name.isEmpty() || name == "." || name == "..") name = "file"
    return truncateUtf8(name, MAX_FS_NAME_BYTES)
}

private fun withSuffix(name: String, n: Int): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) "${name.substring(0, dot)} ($n)${name.substring(dot)}" else "$name ($n)"
}

private fun truncateUtf8(s: String, maxBytes: Int): String {
    if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
    val dot = s.lastIndexOf('.')
    val ext = if (dot > 0 && s.length - dot <= 16) s.substring(dot) else ""
    val stem = if (ext.isEmpty()) s else s.substring(0, dot)
    val budget = maxBytes - ext.toByteArray(Charsets.UTF_8).size
    val sb = StringBuilder()
    var used = 0
    var i = 0
    while (i < stem.length) {
        val cp = stem.codePointAt(i)
        val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
        if (used + len > budget) break
        sb.appendCodePoint(cp)
        used += len
        i += Character.charCount(cp)
    }
    return sb.toString() + ext
}