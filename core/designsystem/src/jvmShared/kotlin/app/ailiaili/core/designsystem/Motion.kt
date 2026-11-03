package app.ailiaili.core.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 统一的按压反馈：按下时轻微缩小，松开回弹。
 *
 * 只给可点击的统计胶囊 / 区块用；纯展示组件不要加，避免无意义的重组。
 * 动画走 Material 的 spring，短、稳、不弹过头。
 */
@Composable
fun Modifier.biliPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy,
        ),
        label = "biliPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
