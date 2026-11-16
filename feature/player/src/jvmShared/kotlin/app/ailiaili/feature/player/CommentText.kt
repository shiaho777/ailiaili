package app.ailiaili.feature.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.CommentEmote
import app.ailiaili.core.model.splitMentions
import coil3.compose.AsyncImage

private sealed interface RichToken {
    data class Plain(val text: String) : RichToken
    data class Mention(val text: String) : RichToken
    data class Emote(val key: String, val emote: CommentEmote) : RichToken
    data class Link(val text: String, val url: String, val bvid: String?) : RichToken
}

private const val LINK_TAG = "link"

/**
 * 评论正文渲染：@ 提及高亮 + 自定义表情内联 + 链接识别。
 *
 * 表情是 B 站的自定义图片，没有 Unicode 对应字符；接口在 content.emote 里
 * 用短代码做 key，所以这里按“最长 key 优先”扫描正文，命中就换成内联图片。
 * 扫描是 O(n × 表情数)，评论长度很短，不需要更复杂的自动机。
 *
 * 链接（URL / 裸 BV 号）高亮可点：BV 走应用内播放器，其余用系统浏览器打开。
 */
@Composable
fun CommentRichText(
    comment: Comment,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    accent: Color = MaterialTheme.colorScheme.primary,
    onOpenLink: ((url: String, bvid: String?) -> Unit)? = null,
) {
    val tokens = remember(comment.message, comment.emotes) { tokenize(comment) }
    val (text, inline) = remember(tokens, accent) { buildRich(tokens, accent) }
    val layout = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    val latestClick = androidx.compose.runtime.rememberUpdatedState(onOpenLink)
    val clickable = if (onOpenLink == null) {
        modifier
    } else {
        modifier.pointerInput(text) {
            detectTapGestures { pos ->
                val offset = layout.value?.getOffsetForPosition(pos) ?: return@detectTapGestures
                val hit = text.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()
                    ?: return@detectTapGestures
                val parts = hit.item.split('\u0000', limit = 2)
                latestClick.value?.invoke(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
            }
        }
    }
    BasicText(
        text = text,
        modifier = clickable,
        style = LocalTextStyle.current.merge(
            TextStyle(color = color, fontSize = 15.sp, lineHeight = 22.sp),
        ),
        inlineContent = inline,
        onTextLayout = { layout.value = it },
    )
}

private fun buildRich(
    tokens: List<RichToken>,
    accent: Color,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = mutableMapOf<String, InlineTextContent>()
    val text = buildAnnotatedString {
        tokens.forEachIndexed { index, token ->
            when (token) {
                is RichToken.Plain -> append(token.text)
                is RichToken.Mention -> withStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.SemiBold),
                ) { append(token.text) }
                is RichToken.Link -> {
                    val start = length
                    withStyle(
                        SpanStyle(
                            color = accent,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                    ) { append(token.text) }
                    addStringAnnotation(
                        tag = LINK_TAG,
                        annotation = token.url + "\u0000" + token.bvid.orEmpty(),
                        start = start,
                        end = length,
                    )
                }
                is RichToken.Emote -> {
                    val id = "emote-$index"
                    // 第二个参数是找不到内联内容时的替代文本，也是读屏念出的内容。
                    appendInlineContent(id, token.key)
                    val big = token.emote.size >= 2
                    val size = if (big) 28.sp else 20.sp
                    inline[id] = InlineTextContent(
                        Placeholder(size, size, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        AsyncImage(
                            model = token.emote.url,
                            contentDescription = token.key,
                            modifier = Modifier.size(if (big) 28.dp else 20.dp),
                        )
                    }
                }
            }
        }
    }
    return text to inline
}

private fun tokenize(comment: Comment): List<RichToken> {
    if (comment.message.isEmpty()) return emptyList()
    val result = mutableListOf<RichToken>()
    val emoteKeys = comment.emotes.keys.filter { it.isNotBlank() }.sortedByDescending { it.length }
    for (segment in splitMentions(comment.message)) {
        if (segment.isMention) {
            result += RichToken.Mention(segment.text)
        } else {
            appendPlainWithEmotes(result, segment.text, emoteKeys, comment.emotes)
        }
    }
    return result
}

private fun appendPlainWithEmotes(
    out: MutableList<RichToken>,
    text: String,
    keys: List<String>,
    emotes: Map<String, CommentEmote>,
) {
    if (keys.isEmpty()) {
        appendPlainWithLinks(out, text)
        return
    }
    var i = 0
    var plainStart = 0
    while (i < text.length) {
        val key = keys.firstOrNull { text.startsWith(it, i) }
        if (key == null) {
            i++
            continue
        }
        if (i > plainStart) appendPlainWithLinks(out, text.substring(plainStart, i))
        out += RichToken.Emote(key, emotes.getValue(key))
        i += key.length
        plainStart = i
    }
    if (plainStart < text.length) appendPlainWithLinks(out, text.substring(plainStart))
}

/** 纯文本段里再切出 URL / 裸 BV 号；其余保持原样。 */
private fun appendPlainWithLinks(out: MutableList<RichToken>, text: String) {
    if (text.isEmpty()) return
    val links = app.ailiaili.core.model.scanTextLinks(text)
    if (links.isEmpty()) {
        out += RichToken.Plain(text)
        return
    }
    var cursor = 0
    for (link in links) {
        if (link.start > cursor) out += RichToken.Plain(text.substring(cursor, link.start))
        out += RichToken.Link(text.substring(link.start, link.end), link.url, link.bvid)
        cursor = link.end
    }
    if (cursor < text.length) out += RichToken.Plain(text.substring(cursor))
}
