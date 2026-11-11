package app.ailiaili.core.designsystem

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.ui.Modifier

actual fun Modifier.smoothWheelScroll(state: ScrollableState, speedPercent: Int): Modifier = this
