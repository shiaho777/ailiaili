package app.ailiaili.core.designsystem

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private class WheelState {
    val filter = WheelFilter()
    var job: Job? = null
    var lastFrame = 0L
    /** 连续几帧几乎没滚动：到边界了，把事件放行给外层。 */
    var stalledFrames = 0
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.smoothWheelScroll(state: ScrollableState, speedPercent: Int): Modifier = composed {
    val scope = rememberCoroutineScope()
    val wheel = remember { WheelState() }
    onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy == 0f) return@onPointerEvent
        // 已经顶到边界（连续 3 帧几乎滚不动）时不拦截：让嵌套的外层列表接手，
        // 不再出现「内层滚到头后整个区域吞滚轮」。反向滚动立即恢复接管。
        val reversing = wheel.filter.remaining != 0f && dy * wheel.filter.remaining < 0f
        if (wheel.stalledFrames >= 3 && !reversing) return@onPointerEvent
        if (reversing) wheel.stalledFrames = 0
        event.changes.forEach { it.consume() }
        wheel.filter.feed(dy * 48f * (speedPercent.coerceIn(100, 500) / 100f))
        if (wheel.job?.isActive != true) {
            wheel.lastFrame = 0L
            wheel.job = scope.launch {
                while (isActive) {
                    if (wheel.filter.settled()) {
                        wheel.filter.reset()
                        break
                    }
                    val now = withFrameNanos { it }
                    val dt = if (wheel.lastFrame == 0L) 1f / 60f
                    else ((now - wheel.lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    wheel.lastFrame = now
                    val step = wheel.filter.step(dt)
                    var consumed = 0f
                    if (abs(step) >= 0.1f) {
                        runCatching { state.scroll { consumed = scrollBy(step) } }
                        wheel.filter.correct(step - consumed)
                    }
                    if (abs(step) >= 1f && abs(consumed) < abs(step) * 0.2f) {
                        wheel.stalledFrames++
                        if (wheel.stalledFrames >= 3) {
                            wheel.filter.reset()
                            break
                        }
                    } else if (abs(consumed) >= 0.1f) {
                        wheel.stalledFrames = 0
                    }
                }
            }
        }
    }
}
