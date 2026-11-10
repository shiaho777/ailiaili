package app.ailiaili.core.network

import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.CommentEmote
import app.ailiaili.core.model.CommentPicture
import app.ailiaili.core.model.Episode
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.Quality
import app.ailiaili.core.model.VideoDetail
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.VideoPage
import app.ailiaili.core.model.formatSeconds
import app.ailiaili.core.model.UrlParser
import app.ailiaili.core.model.normalizeUrl
import app.ailiaili.core.model.formatMonthDayHour
import app.ailiaili.core.model.parseMentions
import app.ailiaili.core.model.qnLabel

internal fun parseVideo(e: JSONObject): VideoItem? {
    val bvid = e.str("bvid")
    if (bvid.isBlank()) return null
    val owner = e.obj("owner") ?: e.obj("author")
    val authorName = owner?.str("name").orEmpty().ifBlank {
        if (e.obj("author") == null) e.str("author") else ""
    }
    val stat = e.obj("stat")
    val duration = e.int("duration")
    return VideoItem(
        bvid = bvid,
        aid = e.lng("aid").takeIf { it > 0 } ?: e.lng("id"),
        cid = e.cidOrHistory(),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("pic").ifBlank { e.str("cover") }),
        author = authorName,
        ownerMid = owner?.lng("mid") ?: e.lng("mid"),
        ownerFace = normalizeUrl(owner?.str("face").orEmpty()),
        durationText = if (duration > 0) formatSeconds(duration) else e.str("duration").ifBlank { e.str("length") },
        durationSec = duration,
        viewCount = stat?.lng("view") ?: e.lng("play"),
        danmakuCount = stat?.lng("danmaku") ?: 0,
        likeCount = stat?.lng("like") ?: 0,
        coinCount = stat?.lng("coin") ?: 0,
        favoriteCount = stat?.lng("favorite") ?: 0,
        pubdate = e.lng("pubdate"),
        reason = e.reasonText(),
    )
}

/** 推荐流条目：过滤非视频卡片，并解析「已关注」标记。 */
internal fun parseRecommend(e: JSONObject): VideoItem? {
    if (e.str("goto").isNotEmpty() && e.str("goto") != "av") return null
    val item = parseVideo(e) ?: return null
    val followed = e.int("is_followed") == 1 ||
        e.obj("rcmd_reason")?.int("reason_type") == 1
    return if (followed) item.copy(authorFollowed = true) else item
}

/** App 端推荐流：goto=av 的卡片，字段在 args / player_args 里。 */
internal fun parseAppRecommend(e: JSONObject): VideoItem? {
    if (e.str("goto") != "av" && e.str("card_goto") != "av") return null
    val args = e.obj("args")
    val player = e.obj("player_args")
    val aid = args?.lng("aid") ?: e.str("param").toLongOrNull() ?: 0L
    if (aid <= 0) return null
    val bvid = UrlParser.avToBv(aid)
    if (bvid.isBlank()) return null
    return VideoItem(
        bvid = bvid,
        aid = aid,
        cid = player?.lng("cid") ?: 0,
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("cover")),
        author = args?.str("up_name").orEmpty(),
        ownerMid = args?.lng("up_id") ?: 0,
        durationText = e.str("cover_right_text"),
        durationSec = player?.int("duration") ?: 0,
        viewCount = parseCountText(e.str("cover_left_text_1")),
        danmakuCount = parseCountText(e.str("cover_left_text_2")),
    )
}

/** "26.8万" / "1.2亿" / "217" → 数字。解析不了就当 0。 */
internal fun parseCountText(raw: String): Long {
    val text = raw.trim()
    if (text.isEmpty()) return 0
    val unit = when {
        text.endsWith("万") -> 10_000L
        text.endsWith("亿") -> 100_000_000L
        else -> 1L
    }
    val number = text.removeSuffix("万").removeSuffix("亿").toDoubleOrNull() ?: return 0
    return (number * unit).toLong()
}

internal fun parseSearch(e: JSONObject): VideoItem? {
    val bvid = e.str("bvid")
    if (bvid.isBlank()) return null
    return VideoItem(
        bvid = bvid,
        aid = e.lng("aid"),
        cid = e.cidOrHistory(),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("pic")),
        author = e.str("author"),
        ownerMid = e.lng("mid"),
        durationText = e.str("duration"),
        durationSec = e.int("duration"),
        viewCount = e.lng("play"),
        danmakuCount = e.lng("video_review"),
        pubdate = e.lng("pubdate"),
    )
}

internal fun parseLive(e: JSONObject): VideoItem? = parseLiveRoom(e)

internal fun parseLiveRoom(e: JSONObject): VideoItem? {
    val room = e.lng("roomid").takeIf { it > 0 } ?: e.lng("room_id")
    if (room <= 0) return null
    val cover = listOf("cover", "user_cover", "system_cover", "keyframe")
        .firstNotNullOfOrNull { e.str(it).takeIf(String::isNotBlank) }
        .orEmpty()
    return VideoItem(
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(cover),
        author = e.str("uname").ifBlank { e.str("uname") },
        ownerMid = e.lng("uid"),
        viewCount = e.lng("online"),
        roomId = room,
    )
}

internal fun parseDetail(bvid: String, d: JSONObject): VideoDetail {
    val owner = d.obj("owner")
    val stat = d.obj("stat")
    val pages = d.arr("pages")?.objects().orEmpty().map {
        VideoPage(it.lng("cid"), it.int("page"), it.str("part"), it.int("duration"))
    }
    val season = d.obj("ugc_season")
    val episodes = buildList {
        var index = 0
        val sections = season?.arr("sections")?.objects().orEmpty()
        for (section in sections) {
            val sectionTitle = section.str("title")
            for (ep in section.arr("episodes")?.objects().orEmpty()) {
                val bv = ep.str("bvid")
                if (bv.isBlank()) continue
                index++
                add(
                    Episode(
                        bvid = bv,
                        cid = ep.lng("cid"),
                        title = ep.str("title"),
                        cover = normalizeUrl(ep.str("arc_cover").ifBlank { ep.str("cover") }),
                        section = sectionTitle,
                        index = index,
                    ),
                )
            }
        }
    }
    return VideoDetail(
        bvid = bvid,
        aid = d.lng("aid"),
        cid = d.lng("cid"),
        title = d.str("title"),
        pic = normalizeUrl(d.str("pic")),
        owner = owner?.str("name").orEmpty(),
        ownerMid = owner?.lng("mid") ?: 0,
        ownerFace = normalizeUrl(owner?.str("face").orEmpty()),
        desc = d.str("desc"),
        viewCount = stat?.lng("view") ?: 0,
        likeCount = stat?.lng("like") ?: 0,
        danmakuCount = stat?.lng("danmaku") ?: 0,
        coinCount = stat?.lng("coin") ?: 0,
        favoriteCount = stat?.lng("favorite") ?: 0,
        shareCount = stat?.lng("share") ?: 0,
        durationSec = d.int("duration"),
        pages = pages,
        episodes = episodes,
        seasonTitle = season?.str("title").orEmpty(),
        pubdate = d.lng("pubdate"),
        coined = d.int("coin") == 1,
    )
}

internal fun parseComment(e: JSONObject, upperMid: Long = 0, withReplies: Boolean = true): Comment {
    val member = e.obj("member")
    val content = e.obj("content")
    val control = e.obj("reply_control")
    val mid = member?.lng("mid") ?: 0
    val message = content?.str("message").orEmpty()
    val replies = if (withReplies) {
        e.arr("replies")?.objects().orEmpty().map { parseComment(it, upperMid, withReplies = false) }
    } else {
        emptyList()
    }
    val location = control?.str("location").orEmpty()
        .removePrefix("IP属地：")
        .removePrefix("IP属地:")
        .trim()
    val action = e.int("action")
    val emotes = buildMap {
        val emoteObj = content?.obj("emote") ?: return@buildMap
        val keys = emoteObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // 只收正文里真的用到的表情，避免白存一堆用不上的地址。
            if (key.isBlank() || !message.contains(key)) continue
            val v = emoteObj.obj(key) ?: continue
            val url = normalizeUrl(v.str("url").ifBlank { v.str("webp_url") })
            if (url.isBlank()) continue
            val size = v.obj("meta")?.int("size")?.takeIf { it > 1 } ?: 1
            put(key, CommentEmote(url, size))
        }
    }
    val pictures = content?.arr("pictures")?.objects().orEmpty().mapNotNull { p ->
        val url = normalizeUrl(p.str("img_src"))
        if (url.isBlank()) null else CommentPicture(url, p.int("img_width"), p.int("img_height"))
    }
    return Comment(
        rpid = e.lng("rpid"),
        name = member?.str("uname").orEmpty(),
        face = normalizeUrl(member?.str("avatar").orEmpty()),
        message = message,
        like = e.int("like"),
        timeText = control?.str("time_desc").orEmpty().ifBlank { commentTime(e.lng("ctime")) },
        replyCount = e.int("rcount"),
        mid = mid,
        location = location,
        level = member?.obj("level_info")?.int("current_level") ?: 0,
        up = upperMid > 0 && mid == upperMid,
        liked = action == 1,
        disliked = action == 2,
        replies = replies,
        mentions = parseMentions(message),
        emotes = emotes,
        pictures = pictures,
    )
}

/**
 * 解析 durl（音视频合流单流）响应。
 * 多段切片不能只播第一段，否则播到一半就没了，所以直接判为不可用。
 */
internal fun decodeDurl(data: JSONObject): PlayInfo? {
    val segs = data.arr("durl")?.objects().orEmpty()
        .map { it.str("url") }
        .filter { it.isNotBlank() }
    if (segs.size != 1) return null
    val actual = data.int("quality")
    val qualities = data.arr("accept_quality")?.let { arr ->
        buildList {
            for (i in 0 until arr.length()) add(arr.optInt(i))
        }
    }.orEmpty().filter { it > 0 }.distinct().sortedDescending()
        .map { Quality(it, qnLabel(it)) }
    return PlayInfo(
        videoUrl = segs[0],
        audioUrl = null,
        backupUrls = emptyList(),
        durationMs = data.lng("timelength"),
        qualities = qualities,
        actualQn = actual,
    )
}

/**
 * 解析 `/xlive/web-ucenter/v1/xfetter/GetWebList` 的 data。
 * 只收 live_status == 1（直播中），0=未开播、2=轮播都不算。
 *
 * `hit_ab=true` 时接口把 online/live_time 置 0，头像用 face 比房间封面更像「人」。
 * rooms 与 list 内容相同，rooms 缺失时退回 list。
 */
internal fun parseFollowingLive(data: JSONObject): List<VideoItem> {
    val rooms = data.arr("rooms") ?: data.arr("list") ?: return emptyList()
    val seenRooms = mutableSetOf<Long>()
    val items = rooms.objects().mapNotNull { e ->
        if (e.int("live_status") != 1) return@mapNotNull null
        var roomId = e.lng("room_id")
        if (roomId <= 0) roomId = e.lng("roomid")
        if (roomId <= 0) roomId = e.lng("short_id")
        if (roomId <= 0 || !seenRooms.add(roomId)) return@mapNotNull null
        val cover = e.str("face")
            .ifBlank { e.str("cover_from_user") }
            .ifBlank { e.str("keyframe") }
            .ifBlank { e.str("room_cover") }
        VideoItem(
            roomId = roomId,
            title = e.str("title").stripHtml(),
            author = e.str("uname").ifBlank { e.str("nickname") },
            ownerMid = e.lng("uid"),
            cover = normalizeUrl(cover),
            durationText = "直播中",
            viewCount = e.lng("online"),
        )
    }
    // hit_ab=true 时 online 恒为 0，此时保持接口原顺序。
    return if (items.any { it.viewCount > 0 }) items.sortedByDescending { it.viewCount } else items
}

internal fun parseDynamic(e: JSONObject): VideoItem? {
    val typeNum = e.int("type")
    if (typeNum == 1) {
        val orig = e.obj("orig") ?: return null
        return parseDynamic(orig)
    }
    val flatVideo = typeNum == 8 || (typeNum == 0 && e.str("title").isNotBlank() && e.str("cover").isNotBlank())
    if (flatVideo) {
        var bvid = bvidFromJump(e.str("jump_url"))
        if (bvid.isBlank()) bvid = e.str("bvid")
        if (bvid.isBlank()) return null
        val author = e.obj("author")
        return VideoItem(
            bvid = bvid,
            aid = e.str("rid").toLongOrNull() ?: 0,
            cid = e.cidOrHistory(),
            title = e.str("title").stripHtml(),
            cover = normalizeUrl(e.str("cover")),
            author = author?.str("name").orEmpty(),
            ownerMid = author?.lng("mid") ?: e.lng("uid"),
            durationText = e.str("duration_text"),
            durationSec = e.int("duration"),
            pubdate = e.lng("pubdate").takeIf { it > 0 } ?: e.lng("ctime"),
        )
    }
    val major = e.obj("modules")?.obj("module_dynamic")?.obj("major") ?: return null
    val archive = when (major.str("type")) {
        "MAJOR_TYPE_ARCHIVE" -> major.obj("archive")
        "MAJOR_TYPE_FORWARD" -> major.obj("orig")?.let { parseDynamic(it); null }
        else -> null
    } ?: return null
    val bvid = archive.str("bvid")
    if (bvid.isBlank()) return null
    val author = e.obj("modules")?.obj("module_author")
    val stat = archive.obj("stat")
    return VideoItem(
        bvid = bvid,
        aid = archive.lng("aid"),
        cid = archive.cidOrHistory(),
        title = archive.str("title").stripHtml(),
        cover = normalizeUrl(archive.str("cover")),
        author = author?.str("name").orEmpty(),
        ownerMid = author?.lng("mid") ?: 0,
        durationText = archive.str("duration_text"),
        durationSec = archive.int("duration"),
        pubdate = author?.lng("pub_ts")?.takeIf { it > 0 } ?: archive.lng("pubdate"),
        viewCount = stat?.lng("view") ?: 0,
        danmakuCount = stat?.lng("danmaku") ?: 0,
    )
}

internal fun JSONObject.reasonText(): String {
    val raw = opt("rcmd_reason") ?: return ""
    return when (raw) {
        is JSONObject -> raw.str("content")
        is String -> raw
        else -> ""
    }
}

internal fun JSONObject.cidOrHistory(): Long {
    val direct = lng("cid")
    if (direct > 0) return direct
    return obj("history")?.lng("cid") ?: 0L
}

private fun bvidFromJump(url: String): String {
    val i = url.indexOf("/video/")
    if (i < 0) return ""
    val rest = url.substring(i + "/video/".length)
    return rest.substringBefore('?').substringBefore('/')
}

private fun commentTime(epoch: Long): String = formatMonthDayHour(epoch)
