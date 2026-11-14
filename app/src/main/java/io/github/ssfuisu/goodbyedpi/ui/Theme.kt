package io.github.ssfuisu.goodbyedpi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.ssfuisu.goodbyedpi.data.ThemeMode

private val Light = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    secondary = Color(0xFF3F3F46),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF6B6B70),
    surfaceContainer = Color(0xFFF6F6F7),
    surfaceContainerHigh = Color(0xFFF0F0F2),
    surfaceContainerHighest = Color(0xFFEAEAEC),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF111111),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color(0xFF111111),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF1F1F23),
    surfaceContainerHighest = Color(0xFF27272A),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainerLowest = Color(0xFF0E0E10),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFF2F2F2),
)

/** Accent used only for the "connected" state. */
val LocalAccent = staticCompositionLocalOf { Color(0xFF16A34A) }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccent provides if (dark) Color(0xFF22C55E) else Color(0xFF16A34A)
    ) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            content = content,
        )
    }
}
