import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*

object Protocol {
    const val MAGIC = "ALOOO"

    fun formatBytes(selfId: UUID, timestampMs: Long = Instant.now().toEpochMilli()):
            ByteArray = "$MAGIC|$selfId|$timestampMs".toByteArray(StandardCharsets.UTF_8)

    fun parse(text: String): UUID? = runCatching {
        val (magic, uuidStr, _) = text.split("|")
        require(magic == MAGIC)
        UUID.fromString(uuidStr)
    }.getOrNull()
}
