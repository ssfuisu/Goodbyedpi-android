package io.github.ssfuisu.goodbyedpi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ssfuisu.goodbyedpi.BuildConfig
import io.github.ssfuisu.goodbyedpi.R
import io.github.ssfuisu.goodbyedpi.data.AppSettings
import io.github.ssfuisu.goodbyedpi.data.DNS_PRESETS
import io.github.ssfuisu.goodbyedpi.data.ThemeMode
import io.github.ssfuisu.goodbyedpi.data.isValidIpv4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var customDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Appearance
            SectionTitle(stringResource(R.string.section_theme))
            Card {
                val modes = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                )
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { onChange { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            icon = {},
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) { Text(stringResource(label)) }
                    }
                }
            }

            // DNS
            SectionTitle(stringResource(R.string.section_dns))
            Card {
                val isPreset = DNS_PRESETS.any { it.address == settings.dns }
                DNS_PRESETS.forEach { p ->
                    RadioRow(
                        title = p.name,
                        subtitle = p.address,
                        selected = settings.dns == p.address,
                        onClick = { onChange { it.copy(dns = p.address) } },
                    )
                }
                RadioRow(
                    title = stringResource(R.string.dns_custom),
                    subtitle = if (isPreset) stringResource(R.string.dns_custom_hint) else settings.dns,
                    selected = !isPreset,
                    onClick = { customDialog = true },
                )
                Divider()
                SwitchRow(
                    title = stringResource(R.string.doh),
                    subtitle = stringResource(R.string.doh_desc),
                    checked = settings.dnsOverHttps,
                    onChange = { v -> onChange { it.copy(dnsOverHttps = v) } },
                )
            }

            // Bypass
            SectionTitle(stringResource(R.string.section_bypass))
            Card {
                SwitchRow(
                    stringResource(R.string.split_tls), stringResource(R.string.split_tls_desc),
                    settings.splitTls,
                ) { v -> onChange { it.copy(splitTls = v) } }
                Divider()
                SwitchRow(
                    stringResource(R.string.split_record), stringResource(R.string.split_record_desc),
                    settings.splitTlsRecord,
                ) { v -> onChange { it.copy(splitTlsRecord = v) } }
                Divider()
                SwitchRow(
                    stringResource(R.string.http_tricks), stringResource(R.string.http_tricks_desc),
                    settings.httpTricks,
                ) { v -> onChange { it.copy(httpTricks = v) } }
                Divider()
                SwitchRow(
                    stringResource(R.string.block_quic), stringResource(R.string.block_quic_desc),
                    settings.blockQuic,
                ) { v -> onChange { it.copy(blockQuic = v) } }
            }

            Text(
                text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (customDialog) {
        CustomDnsDialog(
            initial = if (DNS_PRESETS.any { it.address == settings.dns }) "" else settings.dns,
            onDismiss = { customDialog = false },
            onSave = { ip ->
                onChange { it.copy(dns = ip) }
                customDialog = false
            },
        )
    }
}

@Composable
private fun CustomDnsDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val valid = isValidIpv4(text.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.dns_custom)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(15) },
                singleLine = true,
                placeholder = { Text("9.9.9.9") },
                isError = text.isNotEmpty() && !valid,
                supportingText = {
                    if (text.isNotEmpty() && !valid) Text(stringResource(R.string.dns_invalid))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = valid) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}
