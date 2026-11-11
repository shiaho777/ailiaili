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
    var remaining = 0f
    var job: Job? = null
}

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.smoothWheelScroll(state: ScrollableState, speedPercent: Int): Modifier = composed {
    val scope = rememberCoroutineScope()
    val wheel = remember { WheelState() }
    onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy == 0f) return@onPointerEvent
        event.changes.forEach { it.consume() }
        wheel.remaining += dy * 48f * (speedPercent.coerceIn(100, 500) / 100f)
        if (wheel.job?.isActive != true) {
            wheel.job = scope.launch {
                while (isActive) {
                    val pending = wheel.remaining
                    if (abs(pending) < 0.5f) {
                        wheel.remaining = 0f
                        break
                    }
                    val step = pending * 0.32f
                    var consumed = 0f
                    runCatching { state.scroll { consumed = scrollBy(step) } }
                    if (abs(consumed) < abs(step) * 0.5f) {
                        wheel.remaining = 0f
                        break
                    }
                    wheel.remaining = pending - consumed
                    withFrameNanos { }
                }
            }
        }
    }
}
