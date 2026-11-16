package app.ailiaili.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 封面加载骨架：一层缓慢扫过的高光，替代死灰占位块。
 *
 * 克制原则：只在 loading=true 时组合动画与绘制；加载完成即回到零开销，
 * 不加布局层、不触发重组风暴（动画只影响 draw 阶段）。长列表里同屏
 * 骨架数量有限（LazyGrid 虚拟化），离屏卡片不持有动画。
 */
fun Modifier.coverShimmer(loading: Boolean): Modifier = composed {
    if (!loading) return@composed this
    val transition = rememberInfiniteTransition(label = "coverShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "coverShimmerProgress",
    )
    drawWithContent {
        drawContent()
        val sweep = size.width * 0.6f
        val start = -sweep + progress * (size.width + sweep * 2)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                start = Offset(start, 0f),
                end = Offset(start + sweep, size.height),
            ),
        )
    }
}
