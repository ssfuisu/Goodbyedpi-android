package io.github.ssfuisu.goodbyedpi.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.ssfuisu.goodbyedpi.MainActivity

class ToggleTileService : TileService() {

    companion object {
        fun refresh(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    requestListeningState(context, ComponentName(context, ToggleTileService::class.java))
                }
            }
        }
    }

    override fun onStartListening() {
        update()
    }

    override fun onClick() {
        if (DpiVpnService.status.value != VpnStatus.STOPPED) {
            DpiVpnService.stop(this)
        } else if (VpnService.prepare(this) == null) {
            DpiVpnService.start(this)
        } else {
            openApp()
            return
        }
        qsTile?.apply {
            state = if (state == Tile.STATE_ACTIVE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun update() {
        qsTile?.apply {
            state = if (DpiVpnService.status.value == VpnStatus.STOPPED) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }
}
