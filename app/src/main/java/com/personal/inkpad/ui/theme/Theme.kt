package com.personal.inkpad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personal.inkpad.R

val InkDisplay = FontFamily(
    Font(R.font.syne, FontWeight.Normal),
    Font(R.font.syne, FontWeight.Medium),
    Font(R.font.syne, FontWeight.SemiBold),
    Font(R.font.syne, FontWeight.Bold),
    Font(R.font.syne, FontWeight.ExtraBold)
)

val InkBody = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Light),
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val InkOcean = Color(0xFF0E7C7B)
val InkInk = Color(0xFF102A43)
val InkMist = Color(0xFFE7EEF4)
val InkFog = Color(0xFFF4F7FA)
val InkPaper = Color(0xFFFFFBFF)
val InkAccent = Color(0xFF2BBBAD)
val InkCoral = Color(0xFFD85A4A)
val InkMuted = Color(0xFF627D98)

private val LightColors = lightColorScheme(
    primary = InkOcean,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4F3F0),
    onPrimaryContainer = InkInk,
    secondary = InkInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2EC),
    onSecondaryContainer = InkInk,
    tertiary = InkCoral,
    onTertiary = Color.White,
    background = InkFog,
    onBackground = InkInk,
    surface = Color.White,
    onSurface = InkInk,
    surfaceVariant = InkMist,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFBCCCDC),
    outlineVariant = Color(0xFFD9E2EC)
)

private val DarkColors = darkColorScheme(
    primary = InkAccent,
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF0B5554),
    onPrimaryContainer = Color(0xFFD4F3F0),
    secondary = Color(0xFF9FB3C8),
    onSecondary = Color(0xFF0B1C2C),
    secondaryContainer = Color(0xFF243B53),
    onSecondaryContainer = Color(0xFFD9E2EC),
    tertiary = Color(0xFFFF8A7A),
    onTertiary = Color(0xFF3B0A05),
    background = Color(0xFF0B1520),
    onBackground = Color(0xFFE0E7EF),
    surface = Color(0xFF132337),
    onSurface = Color(0xFFE0E7EF),
    surfaceVariant = Color(0xFF1F3347),
    onSurfaceVariant = Color(0xFF9FB3C8),
    outline = Color(0xFF486581),
    outlineVariant = Color(0xFF243B53)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = InkDisplay, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = (-1).sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = InkDisplay, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = InkDisplay, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = InkBody, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun InkPadTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalDarkTheme provides dark
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}

val isAppDarkTheme: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalDarkTheme.current

fun libraryBackdrop(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(listOf(Color(0xFF0B1520), Color(0xFF132337), Color(0xFF0F2740)))
} else {
    Brush.verticalGradient(listOf(Color(0xFFDDE7F0), Color(0xFFF4F7FA), Color(0xFFEAF3F2)))
}

val CoverPalette = listOf(
    0xFF0E7C7BL, 0xFF102A43L, 0xFF2BBBADL, 0xFF486581L, 0xFFD85A4AL,
    0xFF334E68L, 0xFF14919BL, 0xFF627D98L, 0xFF9B2335L, 0xFF243B53L
)
