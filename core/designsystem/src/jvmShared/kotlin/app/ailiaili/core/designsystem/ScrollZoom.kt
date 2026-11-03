package app.ailiaili.core.designsystem

import androidx.compose.ui.Modifier

/**
 * 桌面端鼠标滚轮缩放。回调参数是滚轮增量：负数向上（放大），正数向下（缩小）。
 * Android 用双指缩放，actual 直接返回原 Modifier。
 */
expect fun Modifier.zoomOnScroll(onZoom: (Float) -> Unit): Modifier
