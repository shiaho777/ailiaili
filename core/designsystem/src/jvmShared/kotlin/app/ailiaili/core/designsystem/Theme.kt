package app.ailiaili.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.model.ThemeMode

private val Accent = Color(0xFFFB7299)
private val Ink = Color(0xFF1A1A1A)
private val InkMuted = Color(0xFF616161)
private val InkFaint = Color(0xFF909090)
private val InkDark = Color(0xFFF2F2F2)
private val InkMutedDark = Color(0xFFB0B0B0)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF5C1130),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E4),
    onSecondaryContainer = Color(0xFF5C1130),
    background = Color(0xFFF3F3F3),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE4E4E4),
    outline = Color(0x22000000),
    outlineVariant = Color(0x14000000),
    scrim = Color(0x99000000),
    tertiary = InkFaint,
    onTertiary = Ink,
)

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C2438),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFFF8FB0),
    onSecondary = Color(0xFF3B1020),
    secondaryContainer = Color(0xFF5C2438),
    onSecondaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF151517),
    onBackground = InkDark,
    surface = Color(0xFF1D1D20),
    onSurface = InkDark,
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = InkMutedDark,
    surfaceContainerLowest = Color(0xFF101012),
    surfaceContainerLow = Color(0xFF1D1D20),
    surfaceContainer = Color(0xFF232327),
    surfaceContainerHigh = Color(0xFF2B2B30),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0x2EFFFFFF),
    outlineVariant = Color(0x1AFFFFFF),
    scrim = Color(0xCC000000),
    tertiary = Color(0xFF8A8A8A),
    onTertiary = InkDark,
)

@Composable
fun BiliTheme(
    mode: ThemeMode,
    accentHex: String = "",
    followSystem: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val accent = platformDynamicAccent(dark, followSystem) ?: accentColor(accentHex)
    val light = if (!followSystem && accent == Accent) LightScheme else LightScheme.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.White, 0.78f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.72f),
        inversePrimary = accent,
    )
    val night = if (!followSystem && accent == Accent) DarkScheme else DarkScheme.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = lerp(accent, Color.Black, 0.62f),
        onPrimaryContainer = lerp(accent, Color.White, 0.82f),
        inversePrimary = accent,
    )
    PlatformThemeEffects(dark)
    MaterialExpressiveTheme(
        colorScheme = if (dark) night else light,
        motionScheme = MotionScheme.expressive(),
        shapes = BiliShapes,
        typography = BiliTypography,
        content = content,
    )
}

private val BiliShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BiliTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )
}

private fun accentColor(hex: String): Color {
    val raw = hex.trim().removePrefix("#")
    if (raw.length != 6) return Accent
    val value = raw.toLongOrNull(16) ?: return Accent
    return Color(0xFF000000 or value)
}
