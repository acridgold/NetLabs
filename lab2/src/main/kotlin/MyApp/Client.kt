package MyApp

import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths

private const val CLIENT_REPORT_INTERVAL_NS = 3_000_000_000L
private const val CONNECT_TIMEOUT_MS = 10_000
private const val CLIENT_SOCKET_TIMEOUT_MS = 60_000

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
                Log.error("Сервер не подтвердил готовность (ответ: ${ready ?: "соединение закрыто"})")
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

            Files.newInputStream(path).use { fileIn ->
                while (sent < size) {
                    val a = System.nanoTime()
                    val n = fileIn.read(buf, 0, minOf(buf.size.toLong(), size - sent).toInt())
                    val b = System.nanoTime()
                    if (n < 0) throw IOException("файл стал короче во время отправки")
                    out.write(buf, 0, n)
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
                        val pct = if (size > 0) sent * 100.0 / size else 100.0
                        Log.info(
                            "Отправлено ${num(pct)}% (${formatSize(sent)} / ${formatSize(size)}), " +
                                    "мгновенная ${formatSpeed(inst)} (${formatBits(inst)}), " +
                                    "средняя ${formatSpeed(avg)} (${formatBits(avg)}); " +
                                    "доля времени: чтение файла ${
                                        num(
                                            (fileReadNs - lastFileNs) * 100.0 / dtNs,
                                            0
                                        )
                                    }%, " +
                                    "запись в сокет ${num((sockWriteNs - lastSockNs) * 100.0 / dtNs, 0)}%"
                        )
                        lastNs = c
                        lastSent = sent
                        lastFileNs = fileReadNs
                        lastSockNs = sockWriteNs
                    }
                }
            }
            out.flush()
            val tSent = System.nanoTime()

            // --- подтверждение ---
            val reply = protocol.readReply(input)
            val tAck = System.nanoTime()
            val ok = reply == Reply.OK

            val sendNs = tSent - tData
            val toAckNs = tAck - tData
            val verdict =
                if (ok) "передача прошла успешно" else "передача завершилась неудачно (ответ сервера: ${reply ?: "соединение закрыто"})"
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
                "  скорость: отправка (до последнего write) ${formatSpeed(rate(size, sendNs))} (${
                    formatBits(
                        rate(
                            size,
                            sendNs
                        )
                    )
                }), " +
                        "эффективная (до подтверждения сервера) ${formatSpeed(rate(size, toAckNs))} (${
                            formatBits(
                                rate(
                                    size,
                                    toAckNs
                                )
                            )
                        })"
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
                            "запись в сокет ${num(sockWriteNs * 100.0 / sendNs)}% (макс. блокировка write ${
                                formatLatency(
                                    maxWriteNs
                                )
                            })"
                )
            }
            return if (ok) 0 else 1
        }
    } catch (e: Exception) {
        Log.error("Ошибка передачи: ${e.javaClass.simpleName}: ${e.message}")
        return 1
    }
}