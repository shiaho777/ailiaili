package app.ailiaili.core.model

/** 正文按 @ 提及切出来的一段。isMention=true 时 text 含 @ 号本身。 */
data class MentionSegment(val text: String, val isMention: Boolean)

private const val MAX_MENTION_LENGTH = 20

/**
 * 扫出正文里 @ 到的昵称。
 *
 * B 站评论的 `content.message` 是纯文本，@ 没有结构化字段，只能按网页版的
 * 规则自己扫：@ 后面跟一段非空白/非标点字符，长度不超过 20，且不能是邮箱
 * 的一部分（邮箱的 @ 前面紧贴字母/数字/下划线）。
 */
fun parseMentions(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val at = text.indexOf('@', i)
        if (at < 0) break
        var end = at + 1
        while (end < text.length && !isMentionSeparator(text[end])) end++
        val name = text.substring(at + 1, end)
        if (name.isNotEmpty() &&
            name.length <= MAX_MENTION_LENGTH &&
            !name.contains('@') &&
            !isEmailPart(text, at)
        ) {
            result += name
        }
        i = at + 1
    }
    return result
}

/**
 * 把正文切成“普通文本 / @提及”段，供 UI 逐段上色。
 * 提及段包含 @ 号本身，和 B 站网页版一致。
 */
fun splitMentions(text: String): List<MentionSegment> {
    if (text.isEmpty()) return emptyList()
    val result = mutableListOf<MentionSegment>()
    var i = 0
    var plainStart = 0
    while (i < text.length) {
        val at = text.indexOf('@', i)
        if (at < 0) break
        var end = at + 1
        while (end < text.length && !isMentionSeparator(text[end])) end++
        val name = text.substring(at + 1, end)
        if (name.isEmpty() ||
            name.length > MAX_MENTION_LENGTH ||
            name.contains('@') ||
            isEmailPart(text, at)
        ) {
            i = at + 1
            continue
        }
        if (at > plainStart) result += MentionSegment(text.substring(plainStart, at), false)
        result += MentionSegment(text.substring(at, end), true)
        i = end
        plainStart = end
    }
    if (plainStart < text.length) result += MentionSegment(text.substring(plainStart), false)
    return result
}

private fun isEmailPart(text: String, at: Int): Boolean {
    if (at <= 0) return false
    val prev = text[at - 1]
    return prev.isLetterOrDigit() || prev == '_'
}

private fun isMentionSeparator(ch: Char): Boolean {
    if (ch.isWhitespace()) return true
    return ch in ",，.。;；:：!！?？()（）[]【】\"“”'’"
}
