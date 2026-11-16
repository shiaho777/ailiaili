package app.ailiaili.core.designsystem

import androidx.compose.ui.Modifier

/** 触屏没有悬停：卡片上的稍后再看入口走长按/菜单，不做悬浮显示。 */
actual fun Modifier.hoverAware(onHover: (Boolean) -> Unit): Modifier = this
