package app.ailiaili.core.designsystem

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** 桌面端滚轮速度（百分比）。Android 不参与。 */
val LocalWheelSpeed = compositionLocalOf { 250 }

/**
 * 桌面端平滑滚轮：滚轮只累加目标距离，内容按帧指数跟随。
 * Android 上是空实现。
 */
expect fun Modifier.smoothWheelScroll(state: ScrollableState, speedPercent: Int): Modifier
