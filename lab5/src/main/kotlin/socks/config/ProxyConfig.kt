package socks.config

import java.net.InetSocketAddress


data class ProxyConfig(
    val listenPort: Int,
    val dnsServer: InetSocketAddress,

    val uiPort: Int? = DEFAULT_UI_PORT,

    val bufferSize: Int = 32 * 1024,
    val handshakeTimeoutMs: Long = 30_000,
    val connectTimeoutMs: Long = 15_000,

    val errorReplyTimeoutMs: Long = 5_000,
    val dnsAttemptTimeoutMs: Long = 2_000,
    val dnsMaxAttempts: Int = 3,
) {
    init {
        require(bufferSize >= 8 * 1024) { "bufferSize должен быть не меньше 8 КиБ" }
        require(dnsMaxAttempts >= 1) { "dnsMaxAttempts должен быть >= 1" }
        require(uiPort == null || uiPort != listenPort) { "Порт панели совпадает с портом прокси" }
    }

    companion object {
        const val DEFAULT_UI_PORT = 8081
    }
}
