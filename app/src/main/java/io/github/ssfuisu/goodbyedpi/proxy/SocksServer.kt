package io.github.ssfuisu.goodbyedpi.proxy

import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class ProxyConfig(
    val desync: DesyncOptions,
    val dns: DnsForwarder,
    val blockQuic: Boolean,
)

/**
 * Minimal SOCKS5 server (CONNECT + UDP ASSOCIATE, no auth) bound to loopback.
 * hev-socks5-tunnel feeds all device traffic into it.
 */
class SocksServer(private val config: ProxyConfig) {

    private val server = ServerSocket()
    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val dnsPool: ExecutorService = Executors.newFixedThreadPool(8)
    private val open = Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Closeable, Boolean>())

    @Volatile
    private var running = false

    val port: Int get() = server.localPort

    fun start(): Int {
        server.reuseAddress = true
        server.bind(InetSocketAddress(LOOPBACK, 0), 1024)
        running = true
        Thread({ acceptLoop() }, "socks-accept").start()
        return server.localPort
    }

    fun stop() {
        running = false
        closeQuietly(server)
        open.toList().forEach { closeQuietly(it) }
        open.clear()
        pool.shutdownNow()
        dnsPool.shutdownNow()
    }

    private fun acceptLoop() {
        while (running) {
            val client = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            try {
                pool.execute { handle(client) }
            } catch (_: Exception) {
                closeQuietly(client)
            }
        }
    }

    private fun track(c: Closeable) = open.add(c)
    private fun release(vararg cs: Closeable?) = cs.forEach { c ->
        if (c != null) {
            open.remove(c); closeQuietly(c)
        }
    }

    private fun handle(client: Socket) {
        track(client)
        try {
            client.tcpNoDelay = true
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            // Greeting
            if (input.readUnsignedByte() != 5) return release(client)
            val methods = input.readUnsignedByte()
            input.skipBytes(methods)
            output.write(byteArrayOf(5, 0))

            // Request
            if (input.readUnsignedByte() != 5) return release(client)
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte()
            val address: InetAddress = when (input.readUnsignedByte()) {
                1 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) })
                4 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) })
                3 -> {
                    val name = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                    InetAddress.getByName(String(name, Charsets.US_ASCII))
                }
                else -> return reply(output, 8).also { release(client) }
            }
            val port = input.readUnsignedShort()

            when (cmd) {
                1 -> connect(client, input, output, InetSocketAddress(address, port))
                3 -> udpAssociate(client, input, output)
                else -> {
                    reply(output, 7); release(client)
                }
            }
        } catch (_: Exception) {
            release(client)
        }
    }

    // ------------------------------------------------------------ CONNECT

    private fun connect(client: Socket, cin: InputStream, cout: OutputStream, target: InetSocketAddress) {
        val remote = Socket()
        track(remote)
        try {
            remote.tcpNoDelay = true
            remote.connect(target, CONNECT_TIMEOUT_MS)
        } catch (e: Exception) {
            reply(cout, 5)
            release(client, remote)
            return
        }
        reply(cout, 0)

        val rin = remote.getInputStream()
        val rout = remote.getOutputStream()
        val pending = AtomicInteger(2)
        val done = {
            if (pending.decrementAndGet() == 0) release(client, remote)
        }

        pool.execute {
            try {
                pipe(rin, cout)
                runCatching { client.shutdownOutput() }
                done()
            } catch (_: Exception) {
                release(client, remote)
            }
        }

        try {
            val first = readFirstPayload(client, cin)
            if (first != null) {
                Desync.send(rout, first, config.desync)
                pipe(cin, rout)
            }
            runCatching { remote.shutdownOutput() }
            done()
        } catch (_: Exception) {
            release(client, remote)
        }
    }

    /** Reads the first client payload, waiting briefly for a full TLS record. */
    private fun readFirstPayload(client: Socket, cin: InputStream): ByteArray? {
        val buf = ByteArray(BUFFER_SIZE)
        var n = cin.read(buf)
        if (n <= 0) return null
        val recLen = Desync.tlsRecordLength(buf.copyOf(minOf(n, 5)))
        if (recLen in (n + 1)..BUFFER_SIZE) {
            client.soTimeout = 300
            try {
                while (n < recLen) {
                    val r = cin.read(buf, n, recLen - n)
                    if (r <= 0) break
                    n += r
                }
            } catch (_: SocketTimeoutException) {
            } finally {
                client.soTimeout = 0
            }
        }
        return buf.copyOf(n)
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buf = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n > 0) {
                output.write(buf, 0, n)
                output.flush()
            }
        }
    }

    // ------------------------------------------------------ UDP ASSOCIATE

    private fun udpAssociate(client: Socket, cin: InputStream, cout: OutputStream) {
        val local = DatagramSocket(InetSocketAddress(LOOPBACK, 0))
        val remote = DatagramSocket()
        track(local); track(remote)
        val peer = AtomicReference<InetSocketAddress?>(null)

        // BND.ADDR / BND.PORT of the relay
        val p = local.localPort
        cout.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, (p ushr 8).toByte(), (p and 0xff).toByte()))
        cout.flush()

        fun sendToPeer(from: InetSocketAddress, data: ByteArray, off: Int, len: Int) {
            val to = peer.get() ?: return
            val header = socksUdpHeader(from)
            val out = ByteArray(header.size + len)
            System.arraycopy(header, 0, out, 0, header.size)
            System.arraycopy(data, off, out, header.size, len)
            try {
                local.send(DatagramPacket(out, out.size, to))
            } catch (_: Exception) {
            }
        }

        // tunnel -> internet
        pool.execute {
            val buf = ByteArray(UDP_BUFFER_SIZE)
            try {
                while (true) {
                    val pkt = DatagramPacket(buf, buf.size)
                    local.receive(pkt)
                    peer.set(pkt.socketAddress as InetSocketAddress)
                    val parsed = parseSocksUdp(buf, pkt.length) ?: continue
                    val (dest, off) = parsed
                    val len = pkt.length - off
                    when {
                        dest.port == 53 -> {
                            val query = buf.copyOfRange(off, off + len)
                            dnsPool.execute {
                                config.dns.resolve(query)?.let { sendToPeer(dest, it, 0, it.size) }
                            }
                        }
                        dest.port == 443 && config.blockQuic -> Unit
                        else -> remote.send(DatagramPacket(buf, off, len, dest))
                    }
                }
            } catch (_: Exception) {
            }
            release(client, local, remote)
        }

        // internet -> tunnel
        pool.execute {
            val buf = ByteArray(UDP_BUFFER_SIZE)
            try {
                while (true) {
                    val pkt = DatagramPacket(buf, buf.size)
                    remote.receive(pkt)
                    sendToPeer(pkt.socketAddress as InetSocketAddress, buf, 0, pkt.length)
                }
            } catch (_: Exception) {
            }
            release(client, local, remote)
        }

        // The association lives as long as the control connection.
        try {
            while (cin.read() >= 0) Unit
        } catch (_: Exception) {
        }
        release(client, local, remote)
    }

    private fun parseSocksUdp(d: ByteArray, len: Int): Pair<InetSocketAddress, Int>? {
        if (len < 10 || d[2].toInt() != 0) return null // fragmentation unsupported
        return try {
            when (d[3].toInt()) {
                1 -> InetSocketAddress(InetAddress.getByAddress(d.copyOfRange(4, 8)), u16(d, 8)) to 10
                4 -> if (len < 22) null else
                    InetSocketAddress(InetAddress.getByAddress(d.copyOfRange(4, 20)), u16(d, 20)) to 22
                3 -> {
                    val l = d[4].toInt() and 0xff
                    if (len < 7 + l) null else {
                        val host = String(d, 5, l, Charsets.US_ASCII)
                        InetSocketAddress(InetAddress.getByName(host), u16(d, 5 + l)) to 7 + l
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad udp header", e)
            null
        }
    }

    private fun socksUdpHeader(a: InetSocketAddress): ByteArray {
        val ip = a.address.address
        val atyp: Byte = if (a.address is Inet4Address) 1 else 4
        val h = ByteArray(4 + ip.size + 2)
        h[3] = atyp
        System.arraycopy(ip, 0, h, 4, ip.size)
        h[4 + ip.size] = (a.port ushr 8).toByte()
        h[5 + ip.size] = (a.port and 0xff).toByte()
        return h
    }

    // ------------------------------------------------------------ helpers

    private fun reply(out: OutputStream, code: Int) {
        try {
            out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
            out.flush()
        } catch (_: Exception) {
        }
    }

    private fun u16(d: ByteArray, p: Int) = ((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff)

    private fun closeQuietly(c: Closeable?) {
        try {
            c?.close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "SocksServer"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val BUFFER_SIZE = 16 * 1024 + 5
        const val UDP_BUFFER_SIZE = 65_535
        val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    }
}
