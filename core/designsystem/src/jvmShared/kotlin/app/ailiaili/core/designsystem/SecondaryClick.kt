package app.ailiaili.core.designsystem

import androidx.compose.ui.Modifier

/**
 * 桌面端鼠标右键点击。Android 没有右键，actual 直接返回原 Modifier。
 * 用 expect/actual 隔离，避免把 PointerButton 这类桌面 API 带进 Android 编译。
 */
expect fun Modifier.secondaryClick(onClick: () -> Unit): Modifier
