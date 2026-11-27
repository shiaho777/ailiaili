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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate

/**
 * 封面加载骨架：一层缓慢扫过的高光，替代死灰占位块。
 *
 * 克制原则：只在 loading=true 时组合动画与绘制；加载完成即回到零开销，
 * 不加布局层、不触发重组风暴（动画只影响 draw 阶段）。长列表里同屏
 * 骨架数量有限（LazyGrid 虚拟化），离屏卡片不持有动画。
 */
fun Modifier.coverShimmer(loading: Boolean): Modifier = composed {
    if (!loading || LocalAliReducedMotion.current) return@composed this
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
    drawWithCache {
        val sweep = size.width * 0.6f
        val travel = size.width + sweep
        val area = Size(sweep, size.height)
        val brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                AliColors.StageInk.copy(alpha = 0.16f),
                Color.Transparent,
            ),
            endX = sweep,
        )
        onDrawWithContent {
            drawContent()
            clipRect {
                translate(left = -sweep + progress * travel) {
                    drawRect(brush, size = area)
                }
            }
        }
    }
}
