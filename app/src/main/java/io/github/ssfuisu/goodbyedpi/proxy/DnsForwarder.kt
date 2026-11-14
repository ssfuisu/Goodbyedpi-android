package io.github.ssfuisu.goodbyedpi.proxy

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Resolves raw DNS queries through the configured server.
 * DNS-over-HTTPS (https://<ip>/dns-query) is tried first when enabled, so ISP
 * interception of port 53 has no effect; plain UDP is the fallback.
 */
class DnsForwarder(private val server: String, private val useDoh: Boolean) {

    @Volatile
    private var dohDisabledUntil = 0L

    fun resolve(query: ByteArray): ByteArray? {
        if (useDoh && System.currentTimeMillis() >= dohDisabledUntil) {
            try {
                return doh(query)
            } catch (_: Exception) {
                dohDisabledUntil = System.currentTimeMillis() + DOH_RETRY_MS
            }
        }
        return try {
            udp(query)
        } catch (_: Exception) {
            null
        }
    }

    private fun doh(query: ByteArray): ByteArray {
        val conn = URL("https://$server/dns-query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setFixedLengthStreamingMode(query.size)
            conn.outputStream.use { it.write(query) }
            if (conn.responseCode != 200) error("DoH HTTP ${conn.responseCode}")
            val body = conn.inputStream.use { it.readBytes() }
            if (body.size < 12) error("short DoH answer")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun udp(query: ByteArray): ByteArray {
        DatagramSocket().use { s ->
            s.soTimeout = TIMEOUT_MS
            s.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(4096)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            return buf.copyOf(p.length)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5000
        const val DOH_RETRY_MS = 60_000L
    }
}
