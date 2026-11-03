package app.ailiaili.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ailiaili.core.data.saveImageBytes
import app.ailiaili.core.model.USER_AGENT
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.network.SharedHttp
import app.ailiaili.core.network.get
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏图片预览：双指/滚轮缩放、拖动平移、双击在 1x / 2x 之间切换。
 *
 * 头像、评论图片、封面都可以复用。图片按 Fit 完整显示，不再出现
 * “只看到中间一小块”的问题。
 */
@Composable
fun AvatarPreviewDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(url) {
        scale = 1f
        offset = Offset.Zero
    }

    fun applyScale(nextRaw: Float) {
        val next = nextRaw.coerceIn(1f, 5f)
        val maxX = ((next - 1f) * container.width / 2f).coerceAtLeast(0f)
        val maxY = ((next - 1f) * container.height / 2f).coerceAtLeast(0f)
        scale = next
        offset = Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }

    val transformable = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        val maxX = ((next - 1f) * container.width / 2f).coerceAtLeast(0f)
        val maxY = ((next - 1f) * container.height / 2f).coerceAtLeast(0f)
        scale = next
        offset = Offset(
            x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { container = it }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                applyScale(if (scale > 1f) 1f else 2f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                        )
                    }
                    .zoomOnScroll { dy ->
                        applyScale(scale * if (dy < 0f) 1.12f else 0.89f)
                    }
                    .transformable(transformable),
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .systemBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name.ifBlank { "图片预览" },
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = {
                    scope.launch {
                        val message = saveImage(url, name)
                        onSaved(message)
                        onDismiss()
                    }
                }) { Text("保存", color = Color.White) }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
            Text(
                text = if (scale > 1f) "双击还原 · 拖动平移" else "双击放大 · 双指/滚轮缩放",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private suspend fun saveImage(url: String, name: String): String = withContext(ioDispatcher) {
    runCatching {
        val bytes = SharedHttp.default.get(
            url,
            mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to "https://www.bilibili.com/",
            ),
        ).body
        saveImageBytes(bytes, name.ifBlank { "image" })
    }.getOrElse { "保存失败：${it.message}" }
}
