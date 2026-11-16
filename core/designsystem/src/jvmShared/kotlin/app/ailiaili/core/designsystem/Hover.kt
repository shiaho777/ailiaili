package app.ailiaili.core.designsystem

import androidx.compose.ui.Modifier

/**
 * 指针进入/离开时回调（桌面鼠标悬停）。
 * Android 没有悬停语义，实现为空修饰符——悬浮入口在移动端走长按/右键菜单。
 */
expect fun Modifier.hoverAware(onHover: (Boolean) -> Unit): Modifier
