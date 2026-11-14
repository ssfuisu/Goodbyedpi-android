package io.github.ssfuisu.goodbyedpi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ssfuisu.goodbyedpi.data.AppSettings
import io.github.ssfuisu.goodbyedpi.data.Settings
import io.github.ssfuisu.goodbyedpi.ui.AppTheme
import io.github.ssfuisu.goodbyedpi.ui.HomeScreen
import io.github.ssfuisu.goodbyedpi.ui.SettingsScreen
import io.github.ssfuisu.goodbyedpi.ui.isDark
import io.github.ssfuisu.goodbyedpi.vpn.DpiVpnService
import io.github.ssfuisu.goodbyedpi.vpn.VpnStatus

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)

        setContent {
            val settings by Settings.state.collectAsStateWithLifecycle()
            val status by DpiVpnService.status.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            var screen by rememberSaveable { mutableStateOf(SCREEN_HOME) }

            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            val vpnPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == RESULT_OK) DpiVpnService.start(this)
            }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(Unit) { askNotifications { notificationPermission.launch(it) } }

            val toggle = {
                if (status == VpnStatus.STOPPED) {
                    val prepare = VpnService.prepare(this)
                    if (prepare != null) vpnPermission.launch(prepare) else DpiVpnService.start(this)
                } else {
                    DpiVpnService.stop(this)
                }
            }

            AppTheme(dark) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { target ->
                    if (target == SCREEN_SETTINGS) {
                        BackHandler { screen = SCREEN_HOME }
                        RestartOnLeave(settings)
                        SettingsScreen(
                            settings = settings,
                            onChange = Settings::update,
                            onBack = { screen = SCREEN_HOME },
                        )
                    } else {
                        HomeScreen(
                            status = status,
                            settings = settings,
                            onToggle = toggle,
                            onOpenSettings = { screen = SCREEN_SETTINGS },
                        )
                    }
                }
            }
        }
    }

    /** Applies changed network settings to a running VPN when leaving the settings screen. */
    @androidx.compose.runtime.Composable
    private fun RestartOnLeave(settings: AppSettings) {
        val initial = rememberSaveable { networkKey(settings) }
        val latest = androidx.compose.runtime.rememberUpdatedState(settings)
        DisposableEffect(Unit) {
            onDispose {
                if (networkKey(latest.value) != initial) DpiVpnService.restart(this@MainActivity)
            }
        }
    }

    private fun networkKey(s: AppSettings): String =
        listOf(s.dns, s.dnsOverHttps, s.splitTls, s.splitTlsRecord, s.httpTricks, s.blockQuic).joinToString()

    private fun askNotifications(request: (String) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            request(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val SCREEN_HOME = "home"
        const val SCREEN_SETTINGS = "settings"
    }
}
