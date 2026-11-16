package app.ailiaili.core.designsystem

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.ailiaili.core.model.scanTextLinks

private const val TAG = "link"

/**
 * 带链接识别的正文文本：URL / 裸 BV 号高亮下划线，点击回调。
 * 简介、图文动态正文共用；不认识的文本保持普通 Text 的观感。
 * 保留 maxLines 截断（简介折叠态），点不到被截掉的部分是预期行为。
 */
@Composable
fun LinkifiedText(
    text: String,
    onOpenLink: (url: String, bvid: String?) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp,
    maxLines: Int = Int.MAX_VALUE,
) {
    val annotated = remember(text, accent) {
        buildAnnotatedString {
            val links = scanTextLinks(text)
            var cursor = 0
            for (link in links) {
                if (link.start > cursor) append(text.substring(cursor, link.start))
                val start = length
                pushStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = accent,
                        textDecoration = TextDecoration.Underline,
                    ),
                )
                append(text.substring(link.start, link.end))
                pop()
                addStringAnnotation(TAG, link.url + "\u0000" + link.bvid.orEmpty(), start, length)
                cursor = link.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val latest = rememberUpdatedState(onOpenLink)
    BasicText(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val offset = layout.value?.getOffsetForPosition(pos) ?: return@detectTapGestures
                val hit = annotated.getStringAnnotations(TAG, offset, offset).firstOrNull()
                    ?: return@detectTapGestures
                val parts = hit.item.split('\u0000', limit = 2)
                latest.value.invoke(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
            }
        },
        style = LocalTextStyle.current.merge(
            TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout.value = it },
    )
}
