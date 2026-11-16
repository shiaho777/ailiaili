package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun platformDynamicAccent(dark: Boolean, enabled: Boolean): Color?

@Composable
expect fun PlatformThemeEffects(dark: Boolean)

expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?

/** 从字体文件（ttf/otf）加载 Compose 字体；失败返回 null，界面回退默认字体。 */
expect fun loadFontFamily(path: String): androidx.compose.ui.text.font.FontFamily?

expect fun argbImage(width: Int, height: Int, pixels: IntArray): ImageBitmap
