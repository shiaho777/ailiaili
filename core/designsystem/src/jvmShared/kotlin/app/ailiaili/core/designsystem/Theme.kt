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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** 当前界面字体（自定义时非默认）。弹幕文本跟随它。 */
val LocalBiliFontFamily = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

@Composable
fun BiliTheme(
    mode: ThemeMode,
    accentHex: String = "",
    followSystem: Boolean = false,
    fontPath: String = "",
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
    // 深浅色/主题色切换：240ms 纯颜色渐变。不做窗口快照、不挖洞扩散——
    // 快照方案要同步渲染整窗，又慢又费内存；这里只插值颜色，零额外位图。
    val target = if (dark) night else light
    val animated = animateBiliColorScheme(target)
    val customFont = remember(fontPath) {
        if (fontPath.isBlank()) null else loadFontFamily(fontPath)
    }
    val typography = remember(customFont) {
        if (customFont == null) BiliTypography else BiliTypography.withFontFamily(customFont)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalBiliFontFamily provides customFont) {
        MaterialExpressiveTheme(
            colorScheme = animated,
            motionScheme = MotionScheme.expressive(),
            shapes = BiliShapes,
            typography = typography,
            content = content,
        )
    }
}

private fun Typography.withFontFamily(family: androidx.compose.ui.text.font.FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

@Composable
private fun animateBiliColorScheme(target: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    var previous by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target != previous) {
            progress.snapTo(0f)
            progress.animateTo(1f, androidx.compose.animation.core.tween(durationMillis = 240))
            previous = target
        }
    }
    if (progress.value >= 1f) return target
    val t = progress.value
    fun mix(a: Color, b: Color) = lerp(a, b, t)
    return target.copy(
        primary = mix(previous.primary, target.primary),
        onPrimary = mix(previous.onPrimary, target.onPrimary),
        primaryContainer = mix(previous.primaryContainer, target.primaryContainer),
        onPrimaryContainer = mix(previous.onPrimaryContainer, target.onPrimaryContainer),
        inversePrimary = mix(previous.inversePrimary, target.inversePrimary),
        secondary = mix(previous.secondary, target.secondary),
        onSecondary = mix(previous.onSecondary, target.onSecondary),
        secondaryContainer = mix(previous.secondaryContainer, target.secondaryContainer),
        onSecondaryContainer = mix(previous.onSecondaryContainer, target.onSecondaryContainer),
        tertiary = mix(previous.tertiary, target.tertiary),
        onTertiary = mix(previous.onTertiary, target.onTertiary),
        tertiaryContainer = mix(previous.tertiaryContainer, target.tertiaryContainer),
        onTertiaryContainer = mix(previous.onTertiaryContainer, target.onTertiaryContainer),
        background = mix(previous.background, target.background),
        onBackground = mix(previous.onBackground, target.onBackground),
        surface = mix(previous.surface, target.surface),
        onSurface = mix(previous.onSurface, target.onSurface),
        surfaceVariant = mix(previous.surfaceVariant, target.surfaceVariant),
        onSurfaceVariant = mix(previous.onSurfaceVariant, target.onSurfaceVariant),
        surfaceTint = mix(previous.surfaceTint, target.surfaceTint),
        inverseSurface = mix(previous.inverseSurface, target.inverseSurface),
        inverseOnSurface = mix(previous.inverseOnSurface, target.inverseOnSurface),
        error = mix(previous.error, target.error),
        onError = mix(previous.onError, target.onError),
        errorContainer = mix(previous.errorContainer, target.errorContainer),
        onErrorContainer = mix(previous.onErrorContainer, target.onErrorContainer),
        outline = mix(previous.outline, target.outline),
        outlineVariant = mix(previous.outlineVariant, target.outlineVariant),
        scrim = mix(previous.scrim, target.scrim),
        surfaceBright = mix(previous.surfaceBright, target.surfaceBright),
        surfaceDim = mix(previous.surfaceDim, target.surfaceDim),
        surfaceContainer = mix(previous.surfaceContainer, target.surfaceContainer),
        surfaceContainerHigh = mix(previous.surfaceContainerHigh, target.surfaceContainerHigh),
        surfaceContainerHighest = mix(previous.surfaceContainerHighest, target.surfaceContainerHighest),
        surfaceContainerLow = mix(previous.surfaceContainerLow, target.surfaceContainerLow),
        surfaceContainerLowest = mix(previous.surfaceContainerLowest, target.surfaceContainerLowest),
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
