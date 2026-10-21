package MyApp

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CLIENT_REPORT_INTERVAL_NS = 3_000_000_000L
private const val CONNECT_TIMEOUT_MS = 10_000
private const val CLIENT_SOCKET_TIMEOUT_MS = 60_000

private const val WRITE_STALL_TIMEOUT_MS = 30_000L

private class WriteWatchdog(private val socket: Socket, private val timeoutMs: Long) {
    @Volatile private var writeStartNs = 0L

    @Volatile var fired = false
        private set

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "write-watchdog").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleWithFixedDelay({ check() }, 500, 500, TimeUnit.MILLISECONDS)
    }

    fun beginWrite() {
        writeStartNs = System.nanoTime()
    }

    fun endWrite() {
        writeStartNs = 0L
    }

    private fun check() {
        val start = writeStartNs
        if (start != 0L && (System.nanoTime() - start) / 1_000_000 > timeoutMs) {
            fired = true
            runCatching { socket.close() }
        }
    }

    fun stop() {
        scheduler.shutdownNow()
    }
}

fun runClient(filePath: String, host: String, port: Int): Int {
    val protocol: TransferProtocol = TcpTransferProtocol()
    try {
        val path = Paths.get(filePath)
        if (!Files.isRegularFile(path)) {
            Log.error("Файл не найден или не является обычным файлом: $filePath")
            return 1
        }
        val name = path.fileName.toString()
        val size = Files.size(path)
        val header = FileHeader(name, size)
        try {
            protocol.validate(header)
        } catch (e: ProtocolException) {
            Log.error("Файл нельзя передать: ${e.message}")
            return 1
        }

        val tStart = System.nanoTime()
        val wallStart = System.currentTimeMillis()
        Log.info("Отправка '$name': ${formatSize(size)} ($size байт) -> $host:$port")

        val tDns = System.nanoTime()
        val addr = InetSocketAddress(host, port)
        val dnsNs = System.nanoTime() - tDns
        if (addr.isUnresolved) {
            Log.error("Не удалось разрешить имя хоста: $host")
            return 1
        }

        Socket().use { socket ->
            socket.tcpNoDelay = true
            val tConn = System.nanoTime()
            socket.connect(addr, CONNECT_TIMEOUT_MS)
            val connectNs = System.nanoTime() - tConn
            socket.soTimeout = CLIENT_SOCKET_TIMEOUT_MS
            Log.info(
                "Соединение: DNS ${formatLatency(dnsNs)}, TCP connect ${formatLatency(connectNs)} (≈ 1 RTT), " +
                        "${socket.localAddress.hostAddress}:${socket.localPort} -> ${addr.address.hostAddress}:$port"
            )

            val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))
            val input = DataInputStream(socket.getInputStream())
            val tHeader = System.nanoTime()
            protocol.writeHeader(out, header)

            val ready = protocol.readReply(input)
            val handshakeNs = System.nanoTime() - tHeader
            if (ready != Reply.READY) {
                val why = when (ready) {
                    Reply.FAIL -> "сервер отказал (занят, остановка или ошибка при создании файла)"
                    null -> "соединение закрыто"
                    else -> "ответ: $ready"
                }
                Log.error("Сервер не подтвердил готовность: $why")
                return 1
            }
            Log.info("Сервер готов: заголовок -> READY за ${formatLatency(handshakeNs)} (RTT + обработка на сервере)")

            // --- передача данных ---
            val buf = ByteArray(BUFFER_SIZE)
            val stats = SpeedStats()
            var sent = 0L
            var fileReadNs = 0L
            var sockWriteNs = 0L
            var maxWriteNs = 0L
            val tData = System.nanoTime()
            var lastNs = tData
            var lastSent = 0L
            var lastFileNs = 0L
            var lastSockNs = 0L

            val watchdog = WriteWatchdog(socket, WRITE_STALL_TIMEOUT_MS)
            try {
                try {
                    Files.newInputStream(path).use { fileIn ->
                        while (sent < size) {
                            val a = System.nanoTime()
                            val n = fileIn.read(buf, 0, minOf(buf.size.toLong(), size - sent).toInt())
                            val b = System.nanoTime()
                            if (n < 0) throw IOException("файл стал короче во время отправки")
                            watchdog.beginWrite()
                            out.write(buf, 0, n)
                            watchdog.endWrite()
                            val c = System.nanoTime()
                            fileReadNs += b - a
                            sockWriteNs += c - b
                            if (c - b > maxWriteNs) maxWriteNs = c - b
                            sent += n

                            if (c - lastNs >= CLIENT_REPORT_INTERVAL_NS) {
                                val dtNs = c - lastNs
                                val inst = rate(sent - lastSent, dtNs)
                                val avg = rate(sent, c - tData)
                                stats.add(inst)
                                val pct = sent * 100.0 / size
                                Log.info(
                                    "Отправлено ${num(pct)}% (${formatSize(sent)} / ${formatSize(size)}), " +
                                            "мгновенная ${formatSpeed(inst)} (${formatBits(inst)}), " +
                                            "средняя ${formatSpeed(avg)} (${formatBits(avg)}); " +
                                            "доля времени: чтение файла ${num((fileReadNs - lastFileNs) * 100.0 / dtNs, 0)}%, " +
                                            "запись в сокет ${num((sockWriteNs - lastSockNs) * 100.0 / dtNs, 0)}%"
                                )
                                lastNs = c
                                lastSent = sent
                                lastFileNs = fileReadNs
                                lastSockNs = sockWriteNs
                            }
                        }
                    }
                    watchdog.beginWrite()
                    out.flush()
                    watchdog.endWrite()
                } catch (e: IOException) {
                    if (watchdog.fired) {
                        throw IOException(
                            "таймаут записи: сервер не принимает данные дольше ${WRITE_STALL_TIMEOUT_MS / 1000} с " +
                                    "(отправлено ${formatSize(sent)} из ${formatSize(size)})", e
                        )
                    }
                    val early = runCatching {
                        socket.soTimeout = 2000
                        protocol.readReply(input)
                    }.getOrNull()
                    if (early == Reply.FAIL) {
                        Log.error("Сервер прервал приём и сообщил об ошибке (FAIL); отправлено ${formatSize(sent)} из ${formatSize(size)}")
                        return 1
                    }
                    throw e
                }
            } finally {
                watchdog.stop()
            }
            val tSent = System.nanoTime()

            // --- подтверждение ---
            val reply = protocol.readReply(input)
            val tAck = System.nanoTime()
            val ok = reply == Reply.OK

            val sendNs = tSent - tData
            val toAckNs = tAck - tData
            val verdict = if (ok) "передача прошла успешно" else "передача завершилась неудачно (ответ сервера: ${reply ?: "соединение закрыто"})"
            val head = "ИТОГ '$name': $verdict"
            if (ok) Log.info(head) else Log.error(head)
            Log.info(
                "  объём: ${formatSize(size)}; время: ${Log.clock(wallStart)} -> ${Log.clock(System.currentTimeMillis())}, " +
                        "всего ${formatElapsed(tAck - tStart)}"
            )
            Log.info(
                "  фазы: DNS ${formatLatency(dnsNs)}, TCP connect ${formatLatency(connectNs)}, " +
                        "заголовок -> READY ${formatLatency(handshakeNs)}, передача ${formatElapsed(sendNs)}, " +
                        "ожидание подтверждения ${formatLatency(tAck - tSent)}"
            )
            Log.info(
                "  скорость: отправка (до последнего write) ${formatSpeed(rate(size, sendNs))} (${formatBits(rate(size, sendNs))}), " +
                        "эффективная (до подтверждения сервера) ${formatSpeed(rate(size, toAckNs))} (${formatBits(rate(size, toAckNs))})"
            )
            if (stats.count > 0) {
                Log.info(
                    "  скорость по интервалам (${stats.count} шт.): мин ${formatSpeed(stats.min)}, " +
                            "макс ${formatSpeed(stats.max)}, σ ${formatSpeed(stats.stddev)}"
                )
            }
            if (sendNs > 0) {
                Log.info(
                    "  доли передачи: чтение файла ${num(fileReadNs * 100.0 / sendNs)}%, " +
                            "запись в сокет ${num(sockWriteNs * 100.0 / sendNs)}% (макс. блокировка write ${formatLatency(maxWriteNs)})"
                )
            }
            return if (ok) 0 else 1
        }
    } catch (e: Exception) {
        Log.error("Ошибка передачи: ${e.javaClass.simpleName}: ${e.message}")
        return 1
    }
}