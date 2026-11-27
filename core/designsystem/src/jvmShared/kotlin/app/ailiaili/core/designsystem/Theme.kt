package app.ailiaili.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.designsystem.resources.Res
import app.ailiaili.core.designsystem.resources.misans_regular
import app.ailiaili.core.model.ThemeMode
import org.jetbrains.compose.resources.Font

internal val LightScheme = lightColorScheme(
    primary = AliColors.LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E9F3),
    onPrimaryContainer = Color(0xFF334B63),
    secondary = Color(0xFF555C65),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9ECF0),
    onSecondaryContainer = AliColors.Ink,
    tertiary = Color(0xFF386744),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFEEE0),
    onTertiaryContainer = Color(0xFF244F31),
    background = Color(0xFFF4F6F8),
    onBackground = AliColors.Ink,
    surface = Color(0xFFFCFDFE),
    onSurface = AliColors.Ink,
    surfaceVariant = Color(0xFFE9ECF0),
    onSurfaceVariant = Color(0xFF555C65),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE1E4E9),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF1F4),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE8EBEF),
    surfaceContainerHighest = Color(0xFFE4E7EC),
    outline = Color(0xFF737C87),
    outlineVariant = Color(0xFFD9DEE4),
    inversePrimary = Color(0xFFA7B9CE),
    inverseSurface = Color(0xFF2B2D31),
    inverseOnSurface = Color(0xFFF2F4F6),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

private val DarkCanvas = Color(0xFF1F1F1F)

internal val DarkScheme = darkColorScheme(
    primary = AliColors.DarkAccent,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = AliColors.DarkAccent.copy(alpha = 60f / 255f).compositeOver(DarkCanvas),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC7C7C7),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF353535),
    onSecondaryContainer = Color(0xFFE3E3E3),
    tertiary = Color(0xFFA6CAA9),
    onTertiary = Color(0xFF1E3523),
    tertiaryContainer = Color(0xFF2B3C2D),
    onTertiaryContainer = Color(0xFFC9E4CA),
    error = Color(0xFFF2AFA6),
    onError = Color(0xFF411B17),
    errorContainer = Color(0xFF49302B),
    onErrorContainer = Color(0xFFFFCFC6),
    background = DarkCanvas,
    onBackground = AliColors.StageInk,
    surface = Color(0xFF282828),
    onSurface = AliColors.StageInk,
    surfaceVariant = Color(0xFF353535),
    onSurfaceVariant = AliColors.StageMuted,
    surfaceBright = Color(0xFF3C3C3C),
    surfaceDim = DarkCanvas,
    surfaceContainerLowest = Color(0xFF181818),
    surfaceContainerLow = Color(0xFF1F2020),
    surfaceContainer = Color(0xFF303030),
    surfaceContainerHigh = Color(0xFF3C3C3C),
    surfaceContainerHighest = Color(0xFF474747),
    outline = Color(0xFF8F8F8F),
    outlineVariant = Color(0xFF474747),
    inversePrimary = AliColors.LightAccent,
    inverseSurface = Color(0xFFE9ECF0),
    inverseOnSurface = AliColors.Ink,
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)

const val SystemUiFontPath = ":system"

val LocalBiliFontFamily = androidx.compose.runtime.compositionLocalOf<FontFamily?> { null }

private val AliRippleConfiguration = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = .08f, focusedAlpha = .15f, hoveredAlpha = .05f, pressedAlpha = 0f),
)

@Composable
fun BiliTheme(
    mode: ThemeMode,
    accentHex: String = "",
    followSystem: Boolean = false,
    fontPath: String = "",
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val accent = platformDynamicAccent(dark, followSystem) ?: accentColor(accentHex)
    val base = if (dark) DarkScheme else LightScheme
    val target = remember(base, accent, dark) {
        if (accent == null) base else themedWithAccent(base, accent, dark)
    }
    val fontFamily = if (fontPath == SystemUiFontPath) {
        null
    } else {
        val importedFont = remember(fontPath) {
            if (fontPath.isBlank()) null else loadFontFamily(fontPath)
        }
        importedFont ?: run {
            val regular = Font(Res.font.misans_regular)
            remember(regular) { FontFamily(regular) }
        }
    }
    val typography = remember(fontFamily) {
        fontFamily?.let { BiliTypography.withFontFamily(it) } ?: BiliTypography
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBiliFontFamily provides fontFamily,
        LocalAliReducedMotion provides reduceMotion,
        LocalRippleConfiguration provides AliRippleConfiguration,
    ) {
        MaterialExpressiveTheme(
            colorScheme = target,
            motionScheme = if (reduceMotion) ReducedMotionScheme else MotionScheme.standard(),
            shapes = BiliShapes,
            typography = typography,
            content = content,
        )
    }
}

internal fun themedWithAccent(base: ColorScheme, accent: Color, dark: Boolean): ColorScheme {
    val seed = accent.convert(ColorSpaces.Srgb)
    val softenedAccent = if (dark) lerp(seed, AliColors.StageInk, .36f) else seed
    val textBackground = base.surfaceContainerHighest
    val primary = readableAccent(softenedAccent, textBackground, dark)
    val selection = base.primaryContainer.tonedBy(seed, if (dark) .20f else .16f)
    return base.copy(
        primary = primary,
        onPrimary = if (contrastRatio(primary, Color.White) >= 4.5f) Color.White else Color.Black,
        primaryContainer = selection,
        onPrimaryContainer = readableAccent(base.onSurface, selection, dark),
    )
}

private fun Color.tonedBy(seed: Color, strength: Float): Color {
    val level = (red + green + blue) / 3f
    val mid = (seed.red + seed.green + seed.blue) / 3f
    return Color(
        (level + (seed.red - mid) * strength).coerceIn(0f, 1f),
        (level + (seed.green - mid) * strength).coerceIn(0f, 1f),
        (level + (seed.blue - mid) * strength).coerceIn(0f, 1f),
    )
}

private fun Typography.withFontFamily(family: FontFamily): Typography {
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.withFamily(),
        displayMedium = displayMedium.withFamily(),
        displaySmall = displaySmall.withFamily(),
        headlineLarge = headlineLarge.withFamily(),
        headlineMedium = headlineMedium.withFamily(),
        headlineSmall = headlineSmall.withFamily(),
        titleLarge = titleLarge.withFamily(),
        titleMedium = titleMedium.withFamily(),
        titleSmall = titleSmall.withFamily(),
        bodyLarge = bodyLarge.withFamily(),
        bodyMedium = bodyMedium.withFamily(),
        bodySmall = bodySmall.withFamily(),
        labelLarge = labelLarge.withFamily(),
        labelMedium = labelMedium.withFamily(),
        labelSmall = labelSmall.withFamily(),
        displayLargeEmphasized = displayLargeEmphasized.withFamily(),
        displayMediumEmphasized = displayMediumEmphasized.withFamily(),
        displaySmallEmphasized = displaySmallEmphasized.withFamily(),
        headlineLargeEmphasized = headlineLargeEmphasized.withFamily(),
        headlineMediumEmphasized = headlineMediumEmphasized.withFamily(),
        headlineSmallEmphasized = headlineSmallEmphasized.withFamily(),
        titleLargeEmphasized = titleLargeEmphasized.withFamily(),
        titleMediumEmphasized = titleMediumEmphasized.withFamily(),
        titleSmallEmphasized = titleSmallEmphasized.withFamily(),
        bodyLargeEmphasized = bodyLargeEmphasized.withFamily(),
        bodyMediumEmphasized = bodyMediumEmphasized.withFamily(),
        bodySmallEmphasized = bodySmallEmphasized.withFamily(),
        labelLargeEmphasized = labelLargeEmphasized.withFamily(),
        labelMediumEmphasized = labelMediumEmphasized.withFamily(),
        labelSmallEmphasized = labelSmallEmphasized.withFamily(),
    )
}

private val BiliShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val BiliTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
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

private fun accentColor(hex: String): Color? {
    val raw = hex.trim().removePrefix("#")
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
