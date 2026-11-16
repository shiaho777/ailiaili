package app.ailiaili.core.model

/** 正文里的一段链接：字节区间 + 打开目标。 */
data class TextLink(
    val start: Int,
    val end: Int,
    val url: String,
    /** BV 号走应用内播放器，其余交给系统浏览器。 */
    val bvid: String? = null,
)

// URL 只吃 ASCII 可见字符（域名/路径/百分号编码都够用），中文与全角标点天然截断；
// 结尾的英文句读再单独剥掉。
private val urlRegex = Regex("""https?://[!-~]+""")
private val bvRegex = Regex("""\bBV1[0-9A-Za-z]{9}\b""")

/**
 * 扫描评论 / 简介 / 图文正文中的可点击链接。
 * URL 优先；不与 URL 重叠的裸 BV 号识别为站内视频。
 * 结尾的中文/英文句读不属于链接本体。
 */
fun scanTextLinks(text: String): List<TextLink> {
    if (text.isEmpty()) return emptyList()
    val links = mutableListOf<TextLink>()
    for (match in urlRegex.findAll(text)) {
        var end = match.range.last + 1
        while (end > match.range.first && text[end - 1] in ".,;:!?。，；：！？、…") end--
        val url = text.substring(match.range.first, end)
        if (url.length <= "https://".length) continue
        val bv = bvRegex.find(url)?.value
        links += TextLink(match.range.first, end, url, bvid = bv)
    }
    for (match in bvRegex.findAll(text)) {
        val overlaps = links.any { match.range.first < it.end && match.range.last + 1 > it.start }
        if (!overlaps) {
            links += TextLink(
                match.range.first,
                match.range.last + 1,
                "https://www.bilibili.com/video/${match.value}",
                bvid = match.value,
            )
        }
    }
    return links.sortedBy { it.start }
}
