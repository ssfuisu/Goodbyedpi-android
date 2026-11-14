package io.github.ssfuisu.goodbyedpi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ssfuisu.goodbyedpi.R
import io.github.ssfuisu.goodbyedpi.data.AppSettings
import io.github.ssfuisu.goodbyedpi.vpn.DpiVpnService
import io.github.ssfuisu.goodbyedpi.vpn.VpnStatus
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    status: VpnStatus,
    settings: AppSettings,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PowerButton(status, onToggle)
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(
                    when (status) {
                        VpnStatus.RUNNING -> R.string.status_on
                        VpnStatus.CONNECTING -> R.string.status_connecting
                        VpnStatus.STOPPED -> R.string.status_off
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = if (status == VpnStatus.RUNNING) LocalAccent.current else MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(if (status == VpnStatus.RUNNING) R.string.hint_on else R.string.hint_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(48.dp))
            InfoCard(status, settings)
        }
    }
}

@Composable
private fun PowerButton(status: VpnStatus, onClick: () -> Unit) {
    val on = status == VpnStatus.RUNNING
    val bg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        label = "bg"
    )
    val fg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "fg"
    )
    Surface(
        onClick = onClick,
        enabled = status != VpnStatus.CONNECTING,
        shape = CircleShape,
        color = bg,
        border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(172.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The pulse animation only exists while connecting.
            val alpha = if (status == VpnStatus.CONNECTING) {
                rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 1f, targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha"
                ).value
            } else 1f
            Icon(
                painter = painterResource(R.drawable.ic_power),
                contentDescription = stringResource(if (on) R.string.disconnect else R.string.connect),
                tint = fg,
                modifier = Modifier
                    .size(64.dp)
                    .alpha(alpha),
            )
        }
    }
}

@Composable
private fun InfoCard(status: VpnStatus, settings: AppSettings) {
    var stats by remember { mutableStateOf<LongArray?>(null) }
    LaunchedEffect(status) {
        while (status == VpnStatus.RUNNING) {
            stats = DpiVpnService.stats()
            delay(1000)
        }
        stats = null
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(vertical = 18.dp, horizontal = 8.dp)) {
            InfoItem(
                label = stringResource(R.string.dns) + if (settings.dnsOverHttps) " · DoH" else "",
                value = settings.dns,
                modifier = Modifier.weight(1.3f),
            )
            InfoItem(
                label = stringResource(R.string.upload),
                value = stats?.let { formatBytes(it[1]) } ?: "—",
                modifier = Modifier.weight(1f),
            )
            InfoItem(
                label = stringResource(R.string.download),
                value = stats?.let { formatBytes(it[3]) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = b / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}
