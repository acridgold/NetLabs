import java.net.*
import java.time.Instant
import java.util.*
import kotlin.concurrent.thread

const val HEARTBEAT_PERIOD_MS = 1000L
const val NODE_TIMEOUT_MS = 3500L
const val GC_PERIOD_MS = 500L
const val BUFFER_SIZE = 1024

class SelfDiscovery(
    private val groupAddress: InetAddress,
    private val port: Int,
    private val networkInterface: NetworkInterface?,
    private val selfId: UUID
) {
    private val socketAddress = InetSocketAddress(groupAddress, port)
    private val socket = MulticastSocket(port).apply {
        reuseAddress = true
        timeToLive = 4

        if (networkInterface != null) {
            try {
                setNetworkInterface(networkInterface)
            } catch (e: SocketException) {
                System.err.println("Не удалось установить исходящий интерфейс: ${e.message}")
            }
        }

        try {
            joinGroup(socketAddress, networkInterface)
        } catch (e: SocketException) {
            System.err.println(
                "Не удалось присоединиться к multicast-группе через интерфейс " +
                        "${networkInterface?.name ?: "(по умолчанию)"}: ${e.message}"
            )
            System.err.println("Запустите программу с явным указанием интерфейса, например: 230.0.0.1 5000 lo")
            throw e
        }
    }

    private val registry = PeerRegistry(NODE_TIMEOUT_MS)

    @Volatile
    private var running = true
    private var lastPrintedSnapshot: Set<String> = emptySet()

    fun start() {
        val receiverThread = thread(name = "receiver") { receiveLoop() }
        val senderThread = thread(name = "sender") { sendLoop() }
        val gcThread = thread(name = "gc") { gcLoop() }

        receiverThread.join()
        senderThread.join()
        gcThread.join()
    }

    fun stop() {
        running = false
        try {
            socket.leaveGroup(socketAddress, networkInterface)
        } catch (_: Exception) {
        }
        socket.close()
    }

    private fun sendLoop() {
        while (running) {
            try {
                val data = Protocol.formatBytes(selfId)
                val packet = DatagramPacket(data, data.size, groupAddress, port)
                socket.send(packet)
            } catch (e: Exception) {
                if (running) System.err.println("Ошибка отправки heartbeat: ${e.message}")
            }
            Thread.sleep(HEARTBEAT_PERIOD_MS)
        }
    }

    private fun receiveLoop() {
        val buffer = ByteArray(BUFFER_SIZE)
        while (running) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                handleIncoming(packet)
            } catch (e: Exception) {
                if (running) System.err.println("Ошибка приема: ${e.message}")
            }
        }
    }

    private fun handleIncoming(packet: DatagramPacket) {
        val text = String(packet.data, 0, packet.length)
        val remoteId = Protocol.parse(text) ?: return
        if (remoteId == selfId) return

        val changed = registry.heartbeat(remoteId, packet.address, System.currentTimeMillis())
        if (changed) printIfChanged()
    }

    private fun gcLoop() {
        while (running) {
            Thread.sleep(GC_PERIOD_MS)
            if (registry.prune(System.currentTimeMillis())) printIfChanged()
        }
    }

    private fun printIfChanged() {
        val snapshot = registry.snapshot()
        if (snapshot != lastPrintedSnapshot) {
            lastPrintedSnapshot = snapshot
            val timestamp = Instant.now()
            if (snapshot.isEmpty()) {
                println("[$timestamp] Живых копий не обнаружено (список пуст).")
            } else {
                println("[$timestamp] Живые копии (${snapshot.size}): ${snapshot.joinToString(", ")}")
            }
        }
    }
}