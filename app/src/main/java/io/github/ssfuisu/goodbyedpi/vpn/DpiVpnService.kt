package io.github.ssfuisu.goodbyedpi.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.ssfuisu.goodbyedpi.MainActivity
import io.github.ssfuisu.goodbyedpi.R
import io.github.ssfuisu.goodbyedpi.data.Settings
import io.github.ssfuisu.goodbyedpi.proxy.DesyncOptions
import io.github.ssfuisu.goodbyedpi.proxy.DnsForwarder
import io.github.ssfuisu.goodbyedpi.proxy.ProxyConfig
import io.github.ssfuisu.goodbyedpi.proxy.SocksServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

enum class VpnStatus { STOPPED, CONNECTING, RUNNING }

class DpiVpnService : VpnService() {

    companion object {
        private const val TAG = "DpiVpnService"
        private const val ACTION_START = "io.github.ssfuisu.goodbyedpi.START"
        private const val ACTION_STOP = "io.github.ssfuisu.goodbyedpi.STOP"
        private const val ACTION_RESTART = "io.github.ssfuisu.goodbyedpi.RESTART"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 8500

        private val _status = MutableStateFlow(VpnStatus.STOPPED)
        val status: StateFlow<VpnStatus> = _status.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, DpiVpnService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            if (_status.value == VpnStatus.STOPPED) return
            context.startService(Intent(context, DpiVpnService::class.java).setAction(ACTION_STOP))
        }

        /** Re-applies settings when the VPN is running. */
        fun restart(context: Context) {
            if (_status.value == VpnStatus.STOPPED) return
            ContextCompat.startForegroundService(
                context, Intent(context, DpiVpnService::class.java).setAction(ACTION_RESTART)
            )
        }

        fun stats(): LongArray? =
            if (_status.value == VpnStatus.RUNNING) runCatching { TProxy.TProxyGetStats() }.getOrNull() else null
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var tun: ParcelFileDescriptor? = null
    private var socks: SocksServer? = null

    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            worker.execute { stopVpn() }
            return START_NOT_STICKY
        }
        // ACTION_START, always-on VPN or a restart by the system.
        showForeground()
        if (intent?.action == ACTION_RESTART) {
            worker.execute { teardown(); startVpn() }
        } else {
            worker.execute { startVpn() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        worker.execute { stopVpn() }
    }

    override fun onDestroy() {
        worker.execute { teardown() }
        worker.shutdown()
        super.onDestroy()
    }

    private fun startVpn() {
        if (tun != null) return
        _status.value = VpnStatus.CONNECTING
        try {
            val s = Settings.current
            val server = SocksServer(
                ProxyConfig(
                    desync = DesyncOptions(s.splitTls, s.splitTlsRecord, s.httpTricks),
                    dns = DnsForwarder(s.dns, s.dnsOverHttps),
                    blockQuic = s.blockQuic,
                )
            )
            val port = server.start()
            socks = server

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(MTU)
                .addAddress("10.10.10.10", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(s.dns)
                .addDisallowedApplication(packageName)
                .setConfigureIntent(mainIntent())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            val fd = builder.establish() ?: error("VPN permission missing")
            tun = fd

            val conf = File(cacheDir, "tunnel.yml")
            conf.writeText(
                """
                tunnel:
                  mtu: $MTU
                  ipv4: 10.10.10.10
                socks5:
                  port: $port
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 81920
                  connect-timeout: 10000
                  log-level: error
                """.trimIndent()
            )
            TProxy.TProxyStartService(conf.absolutePath, fd.fd)
            _status.value = VpnStatus.RUNNING
            ToggleTileService.refresh(this)
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            stopVpn()
        }
    }

    private fun teardown() {
        if (_status.value != VpnStatus.STOPPED || tun != null) {
            runCatching { TProxy.TProxyStopService() }
        }
        runCatching { tun?.close() }
        tun = null
        socks?.stop()
        socks = null
        _status.value = VpnStatus.STOPPED
        ToggleTileService.refresh(this)
    }

    private fun stopVpn() {
        teardown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun showForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, DpiVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text, Settings.current.dns))
            .setContentIntent(mainIntent())
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
