package io.github.ssfuisu.goodbyedpi.vpn

/** JNI bridge to hev-socks5-tunnel (tun -> SOCKS5). */
object TProxy {
    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()

    /** [txPackets, txBytes, rxPackets, rxBytes] */
    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}
