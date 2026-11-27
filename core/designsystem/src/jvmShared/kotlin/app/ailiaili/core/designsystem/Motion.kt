package app.ailiaili.core.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.MotionScheme

val LocalAliReducedMotion = compositionLocalOf { false }

internal object ReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

enum class AliNavigationMotion { Tab, Open, Back }

private val routeEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

fun AnimatedContentTransitionScope<*>.aliScreenTransition(
    motion: AliNavigationMotion,
    reducedMotion: Boolean,
    offset: IntOffset = IntOffset.Zero,
): ContentTransform {
    if (reducedMotion) return EnterTransition.None togetherWith ExitTransition.None
    val shift = tween<IntOffset>(185, easing = routeEasing)
    return when (motion) {
        AliNavigationMotion.Tab -> fadeIn(tween(75)) togetherWith ExitTransition.KeepUntilTransitionsFinished
        AliNavigationMotion.Open -> (slideIn(shift) { offset } + fadeIn(tween(130))) togetherWith
            ExitTransition.KeepUntilTransitionsFinished
        AliNavigationMotion.Back -> (EnterTransition.None togetherWith
            (slideOut(shift) { offset } + fadeOut(tween(145)))).apply {
            targetContentZIndex = -1f
        }
    }
}

@Composable
fun Modifier.biliPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    if (LocalAliReducedMotion.current) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(if (pressed) 70 else 135, easing = routeEasing),
        label = "biliPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
