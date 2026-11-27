package app.ailiaili.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

object AliColors {
    val LightAccent = Color(0xFF4D657D)
    val DarkAccent = Color(0xFFA8C7FA)
    val Ink = Color(0xFF23272D)
    val StageInk = Color(0xFFE3E3E3)
    val StageMedia = Color(0xFF131314)
    val StagePanel = Color(0xFF303030)
    val StageMuted = Color(0xFFC7C7C7)
}

enum class AliMaterial { Panel, Inset, Floating, Playback, Accent }

internal fun ColorScheme.fillFor(material: AliMaterial): Color = when (material) {
    AliMaterial.Panel -> surface
    AliMaterial.Inset -> surfaceContainer
    AliMaterial.Floating -> if (background.luminance() < .36f) surfaceContainerHigh else surfaceBright
    AliMaterial.Playback -> AliColors.StagePanel.copy(alpha = .94f)
    AliMaterial.Accent -> primaryContainer
}

@Composable
fun Modifier.aliMaterial(
    material: AliMaterial,
    shape: Shape = RectangleShape,
    elevated: Boolean = false,
    tinted: Boolean = false,
    opacity: Float = 1f,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .36f
    val media = material == AliMaterial.Playback
    val base = colors.fillFor(material)
    val edge = when {
        tinted && media -> aliPlaybackAccent().copy(alpha = .60f)
        tinted -> colors.outline
        media -> AliColors.StageInk.copy(alpha = .12f)
        material == AliMaterial.Floating -> colors.outlineVariant.copy(alpha = .76f)
        else -> Color.Transparent
    }
    val shadow = if (elevated) Modifier.dropShadow(
        shape = shape,
        shadow = Shadow(
            radius = 6.dp,
            offset = DpOffset(0.dp, 2.dp),
            color = if (dark || media) Color.Black.copy(alpha = .22f) else Color.Black.copy(alpha = .08f),
        ),
    ) else Modifier

    return then(shadow).clip(shape)
        .background(base.copy(alpha = base.alpha * opacity))
        .then(if (edge.alpha > 0f) Modifier.border(if (tinted) 1.5.dp else 1.dp, edge, shape) else Modifier)
}

@Composable
fun Modifier.aliAmbient(onMedia: Boolean = false): Modifier =
    background(if (onMedia) AliColors.StageMedia else MaterialTheme.colorScheme.background)

@Composable
fun aliPlaybackAccent(): Color {
    val colors = MaterialTheme.colorScheme
    return remember(colors) { colors.playbackAccent() }
}

internal fun ColorScheme.playbackAccent(): Color {
    val accent = if (background.luminance() < .36f) primary else lerp(primary, Color.White, .50f)
    return readableAccent(accent, AliColors.StagePanel, dark = true)
}

internal fun contrastRatio(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

internal fun readableAccent(accent: Color, background: Color, dark: Boolean): Color {
    if (contrastRatio(accent, background) >= 4.5f) return accent
    val fallback = if (dark) AliColors.StageInk else AliColors.Ink
    var low = 0f
    var high = 1f
    repeat(12) {
        val middle = (low + high) / 2f
        if (contrastRatio(lerp(accent, fallback, middle), background) >= 4.5f) {
            high = middle
        } else {
            low = middle
        }
    }
    val adjusted = lerp(accent, fallback, high)
    return if (contrastRatio(adjusted, background) >= 4.5f) adjusted else fallback
}
