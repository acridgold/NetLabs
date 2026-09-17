import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConfigTest {

    @Test
    fun `парсинг падает при пустых аргументах`() {
        assertThrows<IllegalArgumentException> { parseArguments(emptyArray()) }
    }

    @Test
    fun `корректный парсинг IPv4 адреса и порта`() {
        val config = parseArguments(arrayOf("230.0.0.1", "5000"))
        assertEquals("230.0.0.1", config.groupAddress.hostAddress)
        assertEquals(5000, config.port)
        assertFalse(config.isIpv6)
        assertNull(config.interfaceName)
    }

    @Test
    fun `корректный парсинг IPv6 адреса и сетевого интерфейса`() {
        val config = parseArguments(arrayOf("ff02::1", "5000", "eth0"))
        assertTrue(config.isIpv6)
        assertEquals("eth0", config.interfaceName)
    }

    @Test
    fun `устанавливается порт по умолчанию, если он не передан`() {
        val config = parseArguments(arrayOf("230.0.0.1"))
        assertEquals(DEFAULT_PORT, config.port)
    }

    @Test
    fun `парсинг падает, если передан не multicast адрес`() {
        assertThrows<IllegalArgumentException> { parseArguments(arrayOf("8.8.8.8", "5000")) }
    }
}