import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ProtocolTest {

    @Test
    fun `formatBytes и parse корректно кодируют и декодируют UUID`() {
        val id = UUID.randomUUID()
        val bytes = Protocol.formatBytes(id, 123456789L)
        val text = String(bytes) // По умолчанию используется UTF-8

        assertEquals(id, Protocol.parse(text))
    }

    @Test
    fun `parse возвращает null при неверном magic-префиксе`() {
        val message = "OTHER-PROTOCOL|${UUID.randomUUID()}|123"
        assertNull(Protocol.parse(message))
    }

    @Test
    fun `parse возвращает null, если UUID невалиден`() {
        val message = "${Protocol.MAGIC}|not-a-uuid|123"
        assertNull(Protocol.parse(message))
    }
}