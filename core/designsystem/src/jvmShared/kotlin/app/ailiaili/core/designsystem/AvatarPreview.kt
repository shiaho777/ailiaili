package app.ailiaili.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 *
 * [urls] 提供多图时支持左右切换：触屏横滑、桌面左右箭头按钮与 ←/→ 键；
 * 放大状态下横滑先平移图片，缩回 1x 才翻页，两套手势不打架。
 */
@Composable
fun AvatarPreviewDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    urls: List<String> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val pages = remember(urls, url) { urls.ifEmpty { listOf(url) } }
    var index by remember(pages) {
        mutableStateOf(pages.indexOf(url).coerceAtLeast(0))
    }
    val current = pages.getOrElse(index) { url }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    var swipeAccum by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(current) {
        scale = 1f
        offset = Offset.Zero
        swipeAccum = 0f
    }

    fun turn(delta: Int) {
        val next = (index + delta).coerceIn(0, pages.lastIndex)
        if (next != index) index = next
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
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(
            Modifier
                .fillMaxSize()
                .background(AliColors.StageMedia)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || pages.size < 2) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { turn(-1); true }
                        Key.DirectionRight -> { turn(1); true }
                        else -> false
                    }
                },
        ) {
            AsyncImage(
                model = current,
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
                    .pointerInput(pages.size, index) {
                        detectTapGestures(
                            onDoubleTap = {
                                applyScale(if (scale > 1f) 1f else 2f)
                                if (scale == 1f) offset = Offset.Zero
                            },
                        )
                    }
                    .pointerInput(pages.size, scale, index) {
                        // 1x 时横滑翻页；放大后让位给平移手势。
                        if (pages.size < 2 || scale > 1.01f) return@pointerInput
                        detectHorizontalDragGestures { change, drag ->
                            change.consume()
                            swipeAccum += drag
                            if (kotlin.math.abs(swipeAccum) > 96f) {
                                turn(if (swipeAccum < 0f) 1 else -1)
                                swipeAccum = 0f
                            }
                        }
                    }
                    .zoomOnScroll { dy ->
                        applyScale(scale * if (dy < 0f) 1.12f else 0.89f)
                    }
                    .transformable(transformable),
                contentScale = ContentScale.Fit,
            )
            if (pages.size > 1) {
                if (index > 0) {
                    AliStageIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack, "上一张", { turn(-1) },
                        modifier = Modifier.align(Alignment.CenterStart).padding(8.dp), onMedia = true,
                    )
                }
                if (index < pages.lastIndex) {
                    AliStageIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward, "下一张", { turn(1) },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp), onMedia = true,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .aliVideoScrim(top = true)
                    .systemBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(name.ifBlank { "图片预览" })
                        if (pages.size > 1) append(" · ${index + 1}/${pages.size}")
                    },
                    color = AliColors.StageInk,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                AliStageIconButton(Icons.Filled.Download, "保存图片", onClick = {
                    scope.launch {
                        val message = saveImage(current, name)
                        onSaved(message)
                        onDismiss()
                    }
                }, onMedia = true)
                AliStageIconButton(Icons.Filled.Close, "关闭预览", onDismiss, onMedia = true)
            }
            Text(
                text = when {
                    scale > 1f -> "双击还原 · 拖动平移"
                    pages.size > 1 -> "左右滑动切换 · 双击放大 · 双指/滚轮缩放"
                    else -> "双击放大 · 双指/滚轮缩放"
                },
                color = AliColors.StageMuted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .aliVideoScrim(top = false)
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
