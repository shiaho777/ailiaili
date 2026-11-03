package app.ailiaili.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 小状态标：直播中、清晰度、未读数、等级、私密、更新至第 N 话。
 * 和统计胶囊分开：Badge 只表达状态/短标签，不承载“数值 + 单位”的统计。
 */
enum class BiliBadgeTone { Accent, Neutral, Dark, Success, Warning }

enum class BiliBadgeStyle { Solid, Soft, Outline }

enum class BiliBadgeSize { Small, Medium }

private data class BadgePalette(
    val container: Color,
    val content: Color,
    val border: BorderStroke?,
)

@Composable
private fun badgePalette(tone: BiliBadgeTone, style: BiliBadgeStyle): BadgePalette {
    val accent = when (tone) {
        BiliBadgeTone.Accent -> MaterialTheme.colorScheme.primary
        BiliBadgeTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        BiliBadgeTone.Dark -> Color.White
        BiliBadgeTone.Success -> Color(0xFF81C784)
        BiliBadgeTone.Warning -> Color(0xFFFFB74D)
    }
    return when (style) {
        BiliBadgeStyle.Solid -> when (tone) {
            BiliBadgeTone.Dark -> BadgePalette(
                Color.Black.copy(alpha = 0.72f),
                Color.White,
                null,
            )
            BiliBadgeTone.Neutral -> BadgePalette(
                MaterialTheme.colorScheme.onSurface,
                MaterialTheme.colorScheme.surface,
                null,
            )
            else -> BadgePalette(accent, Color.White, null)
        }
        BiliBadgeStyle.Soft -> when (tone) {
            BiliBadgeTone.Dark -> BadgePalette(
                Color.Black.copy(alpha = 0.5f),
                Color.White,
                null,
            )
            BiliBadgeTone.Neutral -> BadgePalette(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.onSurfaceVariant,
                null,
            )
            else -> BadgePalette(accent.copy(alpha = 0.16f), accent, null)
        }
        BiliBadgeStyle.Outline -> BadgePalette(
            Color.Transparent,
            accent,
            BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        )
    }
}

@Composable
fun BiliBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BiliBadgeTone = BiliBadgeTone.Neutral,
    style: BiliBadgeStyle = BiliBadgeStyle.Soft,
    size: BiliBadgeSize = BiliBadgeSize.Small,
) {
    if (text.isBlank()) return
    val palette = badgePalette(tone, style)
    val small = size == BiliBadgeSize.Small
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (small) 5.dp else 7.dp),
        color = palette.container,
        contentColor = palette.content,
        border = palette.border,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (small) 5.dp else 8.dp,
                vertical = if (small) 2.dp else 4.dp,
            ),
            color = palette.content,
            fontSize = if (small) 10.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
