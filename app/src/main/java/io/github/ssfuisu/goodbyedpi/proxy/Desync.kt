package io.github.ssfuisu.goodbyedpi.proxy

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** Options for the first client packet of each TCP connection. */
data class DesyncOptions(
    val splitTls: Boolean,
    val splitTlsRecord: Boolean,
    val httpTricks: Boolean,
)

/**
 * User-space DPI circumvention applied to the first payload sent by a client:
 *  - TLS: split the ClientHello into two TLS records inside the SNI and send it
 *    in separate TCP segments, so the hostname never appears in one piece.
 *  - HTTP: "Host:" -> "hoSt:", remove the space after the colon and split the
 *    request inside the host name.
 */
object Desync {

    private const val FALLBACK_SPLIT = 2

    fun send(out: OutputStream, data: ByteArray, opt: DesyncOptions) {
        when {
            isTlsClientHello(data) -> sendTls(out, data, opt)
            opt.httpTricks && isHttpRequest(data) -> sendHttp(out, data)
            else -> out.write(data)
        }
        out.flush()
    }

    // ---------------------------------------------------------------- TLS

    fun isTlsClientHello(d: ByteArray): Boolean =
        d.size > 9 && d[0] == 0x16.toByte() && d[1] == 0x03.toByte() && d[5] == 0x01.toByte()

    /** Total length of the first TLS record (header included), or -1. */
    fun tlsRecordLength(d: ByteArray): Int =
        if (d.size >= 5 && d[0] == 0x16.toByte() && d[1] == 0x03.toByte()) 5 + u16(d, 3) else -1

    private fun sendTls(out: OutputStream, data: ByteArray, opt: DesyncOptions) {
        val sni = findSni(data)
        var packet = data
        var tcpSplit = if (sni != null) sni.first + sni.second / 2 else FALLBACK_SPLIT

        val recordEnd = tlsRecordLength(data)
        // Only re-frame a complete record, otherwise the tail would be misparsed.
        if (opt.splitTlsRecord && recordEnd <= data.size) {
            val bodyLen = recordEnd - 5
            if (bodyLen > 2) {
                val cut = ((if (sni != null) sni.first + sni.second / 2 else 5 + FALLBACK_SPLIT) - 5)
                    .coerceIn(1, bodyLen - 1)
                val b = ByteArrayOutputStream(data.size + 5)
                b.write(data, 0, 3)
                b.write(cut ushr 8); b.write(cut and 0xff)
                b.write(data, 5, cut)
                b.write(data, 0, 3)
                b.write((bodyLen - cut) ushr 8); b.write((bodyLen - cut) and 0xff)
                b.write(data, 5 + cut, bodyLen - cut)
                if (data.size > recordEnd) b.write(data, recordEnd, data.size - recordEnd)
                packet = b.toByteArray()
                // Split the TCP stream inside the first half of the host name.
                tcpSplit = if (sni != null) sni.first + maxOf(1, sni.second / 4) else FALLBACK_SPLIT
            }
        }

        if (opt.splitTls) writeSplit(out, packet, tcpSplit) else out.write(packet)
    }

    /** Returns (offset, length) of the SNI host name inside a ClientHello record. */
    fun findSni(d: ByteArray): Pair<Int, Int>? {
        try {
            val end = minOf(d.size, tlsRecordLength(d))
            var p = 5 + 4 + 2 + 32 // record hdr, handshake hdr, version, random
            if (p >= end) return null
            p += 1 + (d[p].toInt() and 0xff) // session id
            p += 2 + u16(d, p) // cipher suites
            p += 1 + (d[p].toInt() and 0xff) // compression methods
            val extEnd = minOf(end, p + 2 + u16(d, p))
            p += 2
            while (p + 4 <= extEnd) {
                val type = u16(d, p)
                val len = u16(d, p + 2)
                p += 4
                if (type == 0) {
                    // server_name_list length (2), name type (1), name length (2)
                    if (p + 5 > extEnd || d[p + 2].toInt() != 0) return null
                    val nameLen = u16(d, p + 3)
                    val nameOff = p + 5
                    return if (nameLen > 0 && nameOff + nameLen <= d.size) nameOff to nameLen else null
                }
                p += len
            }
        } catch (_: IndexOutOfBoundsException) {
        }
        return null
    }

    // --------------------------------------------------------------- HTTP

    private val METHODS = listOf("GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ", "PATCH ", "CONNECT ")

    fun isHttpRequest(d: ByteArray): Boolean = METHODS.any { m ->
        d.size > m.length && m.indices.all { d[it] == m[it].code.toByte() }
    }

    private fun sendHttp(out: OutputStream, data: ByteArray) {
        val (packet, hostOffset, hostLen) = rewriteHttp(data) ?: run {
            out.write(data); return
        }
        writeSplit(out, packet, hostOffset + maxOf(1, hostLen / 2))
    }

    /** Returns the rewritten request plus offset/length of the host value. */
    fun rewriteHttp(data: ByteArray): Triple<ByteArray, Int, Int>? {
        val headerEnd = indexOf(data, "\r\n\r\n".toByteArray(), 0).let { if (it < 0) data.size else it }
        var i = 0
        var start = -1
        while (true) {
            i = indexOf(data, "\r\n".toByteArray(), i)
            if (i < 0 || i >= headerEnd) break
            val h = i + 2
            if (h + 5 <= data.size && String(data, h, 5, Charsets.US_ASCII).equals("host:", true)) {
                start = h; break
            }
            i = h
        }
        if (start < 0) return null

        var valueStart = start + 5
        while (valueStart < data.size && (data[valueStart] == ' '.code.toByte() || data[valueStart] == '\t'.code.toByte())) {
            valueStart++
        }
        val valueEnd = indexOf(data, "\r\n".toByteArray(), valueStart).let { if (it < 0) data.size else it }

        val b = ByteArrayOutputStream(data.size)
        b.write(data, 0, start)
        b.write("hoSt:".toByteArray())
        val hostOffset = b.size()
        b.write(data, valueStart, data.size - valueStart)
        return Triple(b.toByteArray(), hostOffset, valueEnd - valueStart)
    }

    // ------------------------------------------------------------ helpers

    private fun writeSplit(out: OutputStream, data: ByteArray, at: Int) {
        val pos = at.coerceIn(1, maxOf(1, data.size - 1))
        if (data.size < 2) {
            out.write(data); return
        }
        out.write(data, 0, pos)
        out.flush()
        out.write(data, pos, data.size - pos)
    }

    private fun u16(d: ByteArray, p: Int): Int =
        ((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff)

    private fun indexOf(d: ByteArray, pat: ByteArray, from: Int): Int {
        outer@ for (i in from..d.size - pat.size) {
            for (j in pat.indices) if (d[i + j] != pat[j]) continue@outer
            return i
        }
        return -1
    }
}
