package io.github.ssfuisu.goodbyedpi.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class DnsPreset(val name: String, val address: String)

val DNS_PRESETS = listOf(
    DnsPreset("Quad9", "9.9.9.9"),
    DnsPreset("Cloudflare", "1.1.1.1"),
    DnsPreset("Google", "8.8.8.8"),
    DnsPreset("AdGuard", "94.140.14.14"),
)

const val DEFAULT_DNS = "9.9.9.9"

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dns: String = DEFAULT_DNS,
    val dnsOverHttps: Boolean = true,
    val splitTls: Boolean = true,
    val splitTlsRecord: Boolean = true,
    val httpTricks: Boolean = true,
    val blockQuic: Boolean = true,
)

object Settings {
    private const val FILE = "settings"
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _state.value = read()
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", d.theme.name)!!) }
                .getOrDefault(d.theme),
            dns = prefs.getString("dns", d.dns)?.takeIf { isValidIpv4(it) } ?: d.dns,
            dnsOverHttps = prefs.getBoolean("doh", d.dnsOverHttps),
            splitTls = prefs.getBoolean("split_tls", d.splitTls),
            splitTlsRecord = prefs.getBoolean("split_tls_record", d.splitTlsRecord),
            httpTricks = prefs.getBoolean("http_tricks", d.httpTricks),
            blockQuic = prefs.getBoolean("block_quic", d.blockQuic),
        )
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("dns", s.dns)
            .putBoolean("doh", s.dnsOverHttps)
            .putBoolean("split_tls", s.splitTls)
            .putBoolean("split_tls_record", s.splitTlsRecord)
            .putBoolean("http_tricks", s.httpTricks)
            .putBoolean("block_quic", s.blockQuic)
            .apply()
    }
}

fun isValidIpv4(value: String): Boolean {
    val parts = value.trim().split(".")
    if (parts.size != 4) return false
    return parts.all { p ->
        p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && p.toInt() in 0..255 &&
            !(p.length > 1 && p.startsWith("0"))
    }
}
