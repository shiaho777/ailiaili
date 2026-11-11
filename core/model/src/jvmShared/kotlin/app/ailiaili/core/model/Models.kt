package app.ailiaili.core.model

/**
 * 所有出站请求共用一份 UA。
 *
 * Chrome/126 已被 B 站列入拦截名单：游客态请求 `/x/web-interface/view`
 * 会直接返回 HTTP 412，表现为未登录时打不开视频。升级到 131 即可恢复。
 * 以后若再出现“游客态全量 412”，先扫 UA 版本，不要先改业务代码。
 */
const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

fun normalizeUrl(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://" + url.removePrefix("http://")
    else -> url
}

fun formatCount(n: Long): String = when {
    n >= 100_000_000 -> "${oneDecimal(n / 100_000_000.0)}亿".replace(".0亿", "亿")
    n >= 10_000 -> "${oneDecimal(n / 10_000.0)}万".replace(".0万", "万")
    n > 0 -> n.toString()
    else -> ""
}

/** 发布日期。epoch 秒，没有时返回空串。 */
fun formatDay(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val date = localDateTimeParts(epochSec)
    return "${date.year}-${pad2(date.month)}-${pad2(date.day)}"
}

fun formatMonthDayHour(epochSec: Long): String {
    if (epochSec <= 0) return ""
    val date = localDateTimeParts(epochSec)
    return "${pad2(date.month)}-${pad2(date.day)} ${pad2(date.hour)}:${pad2(date.minute)}"
}

/** 动态页「最近更新」用的相对时间；超过一周回落到月-日 时:分。 */
fun formatRelativeTime(epochSec: Long, nowSec: Long = epochMs() / 1000): String {
    if (epochSec <= 0) return ""
    val diff = (nowSec - epochSec).coerceAtLeast(0)
    return when {
        diff < 60 -> "刚刚"
        diff < 3_600 -> "${diff / 60} 分钟前"
        diff < 86_400 -> "${diff / 3_600} 小时前"
        diff < 7 * 86_400 -> "${diff / 86_400} 天前"
        else -> formatMonthDayHour(epochSec)
    }
}

private fun oneDecimal(n: Double): String {
    val rounded = kotlin.math.round(n * 10.0) / 10.0
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) asLong.toString() else rounded.toString()
}

/** 操作按钮上的「点赞 1.2万」。计数为 0 时只留动作名。 */
fun countLabel(name: String, n: Long): String {
    val count = formatCount(n)
    return if (count.isBlank()) name else "$name $count"
}

fun formatSeconds(seconds: Int): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

fun formatClock(ms: Long): String {
    val total = (ms / 1000).toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

fun qnLabel(qn: Int): String = when (qn) {
    127 -> "杜比视界"
    126 -> "杜比全景声"
    125 -> "HDR"
    120 -> "4K"
    116 -> "1080P60"
    112 -> "1080P+"
    100 -> "智能修复"
    80 -> "1080P"
    74 -> "720P60"
    64 -> "720P"
    32 -> "480P"
    16 -> "360P"
    else -> "${qn}P"
}

data class VideoItem(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val author: String = "",
    val ownerMid: Long = 0,
    val ownerFace: String = "",
    val durationText: String = "",
    val durationSec: Int = 0,
    val viewCount: Long = 0,
    val danmakuCount: Long = 0,
    val likeCount: Long = 0,
    val coinCount: Long = 0,
    val favoriteCount: Long = 0,
    val pubdate: Long = 0,
    val reason: String = "",
    /** 推荐流里该 UP 是否已被当前账号关注。 */
    val authorFollowed: Boolean = false,
    val roomId: Long = 0,
    /** 空间列表先只有播放数。补齐弹幕和点赞之前不显示这一行，避免数字闪一下。 */
    val statsReady: Boolean = true,
) {
    val isLive: Boolean get() = roomId > 0
    val stableKey: String get() = if (isLive) "live-$roomId" else if (bvid.isNotEmpty()) bvid else "aid-$aid"
}

data class VideoPage(
    val cid: Long,
    val page: Int,
    val part: String,
    val durationSec: Int,
)

data class Episode(
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String = "",
    val section: String = "",
    val index: Int = 0,
)

data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val cid: Long,
    val title: String,
    val pic: String,
    val owner: String,
    val ownerMid: Long,
    val ownerFace: String,
    val desc: String,
    val viewCount: Long,
    val likeCount: Long,
    val danmakuCount: Long,
    val coinCount: Long,
    val favoriteCount: Long,
    val shareCount: Long,
    val durationSec: Int,
    val pages: List<VideoPage> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val seasonTitle: String = "",
    val pubdate: Long = 0,
    /** 当前登录用户是否已经给这个稿件投过币。详情接口顶层 coin==1。 */
    val coined: Boolean = false,
)

data class Quality(val qn: Int, val label: String)

/**
 * 720P 及以下优先走 durl 合流单流。
 *
 * 桌面端 LibVLC 对 DASH 双流 + input-slave 的远距离 seek 不可靠：拖进度条
 * 可能跳回开头、画面卡死或无限缓冲。durl 是音视频已合流的单个 MP4，seek
 * 一次到位。1080P 及以上仍走 DASH，避免画质被 durl 的 720P 上限锁死。
 *
 * qn=0 是“自动/最高可用”的旧语义，不能当成 360P，所以显式排除。
 */
fun shouldPreferDurl(qn: Int): Boolean = qn in 1..64

data class LiveQuality(
    val qn: Int,
    val label: String,
)

data class LivePlayInfo(
    val url: String,
    val currentQn: Int,
    val qualities: List<LiveQuality> = emptyList(),
    val liveStartSec: Long = 0,
    val ownerMid: Long = 0,
)

data class LiveRoomInfo(
    val title: String,
    val ownerName: String,
    val ownerFace: String,
    val ownerMid: Long,
    val online: Long,
    val attention: Long,
    val area: String,
)

data class AudioQuality(
    val id: Int,
    val label: String,
    val url: String,
    val bandwidth: Long = 0,
)

fun audioQnLabel(id: Int): String = when (id) {
    30216 -> "64K"
    30232 -> "132K"
    30280 -> "192K"
    30250 -> "杜比全景声"
    30251 -> "Hi-Res 无损"
    else -> if (id > 0) "音质 $id" else "音质"
}

data class PlayInfo(
    val videoUrl: String,
    val audioUrl: String?,
    val backupUrls: List<String>,
    val durationMs: Long,
    val qualities: List<Quality>,
    val actualQn: Int,
    val width: Int = 0,
    val height: Int = 0,
    /** 当前选中的音轨 id（30216/30232/30280/30250/30251）。 */
    val audioQn: Int = 0,
    /** 接口返回的全部可用音轨，按音质从低到高。 */
    val audioQualities: List<AudioQuality> = emptyList(),
)

/** 播放器 v2 的章节。type 2 才是进度条上的高能进度。 */
data class ViewChapter(
    val fromSec: Int,
    val toSec: Int,
    val title: String,
)

/**
 * `/x/player/videoshot` 的雪碧图。
 * [index] 是每一帧对应的秒。一张图里按 [columns] × [rows] 排。
 */
data class VideoShot(
    val images: List<String>,
    val index: List<Int>,
    val columns: Int,
    val rows: Int,
    val cellWidth: Int,
    val cellHeight: Int,
) {
    fun frameAt(seconds: Int): ShotFrame? {
        if (images.isEmpty() || index.isEmpty() || columns <= 0 || rows <= 0) return null
        var count = 0
        for (t in index) if (t <= seconds) count++
        val idx = (count - 2).coerceAtLeast(0).coerceAtMost(index.lastIndex)
        val per = columns * rows
        val page = idx / per
        if (page !in images.indices) return null
        val align = idx % per
        return ShotFrame(
            url = images[page],
            column = align % columns,
            row = align / columns,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            columns = columns,
            rows = rows,
        )
    }
}

data class ShotFrame(
    val url: String,
    val column: Int,
    val row: Int,
    val cellWidth: Int,
    val cellHeight: Int,
    val columns: Int,
    val rows: Int,
)

data class Danmaku(
    val timeSec: Double,
    val text: String,
    val mode: Int,
    val color: Int,
)

/** 评论里的一张图片。尺寸是接口给的原始像素，UI 按它算缩略图比例。 */
data class CommentPicture(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 评论里的一个自定义表情。
 * key 是正文里原样出现的短代码（如 `[微笑]`），size=2 表示大表情。
 */
data class CommentEmote(
    val url: String,
    val size: Int = 1,
)

data class Comment(
    val rpid: Long,
    val name: String,
    val face: String,
    val message: String,
    val like: Int,
    val timeText: String,
    val replyCount: Int,
    val mid: Long = 0,
    val location: String = "",
    val level: Int = 0,
    val pinned: Boolean = false,
    val up: Boolean = false,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val replies: List<Comment> = emptyList(),
    /** 正文里 @ 到的昵称，UI 据此把 @ 段染成强调色。 */
    val mentions: List<String> = emptyList(),
    /** 正文里用到的自定义表情，key 是短代码。 */
    val emotes: Map<String, CommentEmote> = emptyMap(),
    /** 正文下面挂的图片。 */
    val pictures: List<CommentPicture> = emptyList(),
)

data class CommentPage(
    val items: List<Comment>,
    val total: Int,
    val page: Int,
    val hasMore: Boolean,
)

data class UserState(
    val loggedIn: Boolean,
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val level: Int = 0,
    val coins: Double = 0.0,
    val expCurrent: Long = 0,
    val expNext: Long = 0,
)

data class FollowUser(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String = "",
)

data class ItemPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    /** 接口返回的总条数；未知时为 0。 */
    val total: Long = 0,
)

data class NavStat(
    val following: Long,
    val follower: Long,
    val dynamicCount: Long,
)

data class SpaceInfo(
    val mid: Long,
    val name: String,
    val face: String,
    val sign: String,
    val follower: Long,
    val following: Long,
    val level: Int = 0,
    val videoCount: Long = 0,
)

/** 空间里的合集或系列列表。series 为 true 时 id 是 series_id。 */
data class SpaceCollection(
    val id: Long,
    val title: String,
    val cover: String,
    val count: Int,
    val series: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
)

/** 空间动态。没有视频时 video 为空，只展示文字。 */
data class SpacePost(
    val id: String,
    val text: String,
    val timeText: String,
    val video: VideoItem? = null,
)

data class SpaceFeed(
    val posts: List<SpacePost>,
    val nextOffset: String?,
    val hasMore: Boolean,
)

data class FavFolder(
    val id: Long,
    val title: String,
    val count: Int,
    /** 当前稿件是否已经在这个收藏夹里。list-all 带 rid 时 fav_state==1。 */
    val favored: Boolean = false,
    val cover: String = "",
    /** attr 的第二位。true 表示私密收藏夹。 */
    val privateFolder: Boolean = false,
)

data class MsgSession(
    val talkerId: Long,
    val name: String,
    val face: String,
    val preview: String,
    val unread: Int,
    val timeText: String,
)

data class MsgItem(
    val senderUid: Long,
    val mine: Boolean,
    val text: String,
    val imageUrl: String,
    val timeText: String,
    val seqno: Long,
)

data class SponsorSegment(
    val cid: Long,
    val category: String,
    val startSec: Double,
    val endSec: Double,
    val actionType: String = "skip",
    val votes: Int = 0,
)

/**
 * 与 Windows 端同一张 SponsorBlock 类别表。id 是服务端原值，不能改。
 * 不收录 interaction：桌面端已明确不做这一类。
 */
object SponsorCategories {
    val all: List<Pair<String, String>> = listOf(
        "sponsor" to "赞助广告",
        "selfpromo" to "自我推广",
        "intro" to "开场动画",
        "outro" to "结尾动画",
        "preview" to "预告 / 回顾",
        "poi_highlight" to "高能时刻",
    )
    const val DEFAULT = "sponsor"

    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: id

    /** 只留认识的 id。一个都不剩时退回赞助广告，避免开关开着却什么都不跳。 */
    fun sanitize(csv: String?): String {
        val known = all.map { it.first }.toSet()
        val kept = csv.orEmpty()
            .split(',', '，', ';', '；')
            .map { it.trim() }
            .filter { it in known }
            .distinct()
        return if (kept.isEmpty()) DEFAULT else kept.joinToString(",")
    }

    fun select(raw: List<SponsorSegment>, cid: Long, csv: String?): List<SponsorSegment> {
        val want = sanitize(csv).split(',').toSet()
        return raw.filter { segment ->
            (segment.cid <= 0L || segment.cid == cid) &&
                segment.actionType.equals("skip", ignoreCase = true) &&
                segment.votes >= 0 &&
                segment.category in want &&
                segment.endSec > segment.startSec
        }.sortedBy { it.startSec }
    }
}

data class BangumiItem(
    val seasonId: Long,
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    val indexShow: String = "",
    val seasonTypeName: String = "",
)

data class BangumiEpisode(
    val epId: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val longTitle: String = "",
    val cover: String = "",
    val durationSec: Int = 0,
)

data class BangumiDetail(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val evaluate: String = "",
    val episodes: List<BangumiEpisode>,
)

fun bangumiEpisodeDisplayTitle(title: String, longTitle: String): String =
    listOf(title, longTitle).filter { it.isNotBlank() }.joinToString(" ")

fun bangumiPlayTitle(seasonTitle: String, title: String, longTitle: String): String =
    "$seasonTitle - ${bangumiEpisodeDisplayTitle(title, longTitle)}"

data class SearchUser(
    val mid: Long,
    val name: String,
    val face: String = "",
    val sign: String = "",
    val fans: Long = 0,
    val videos: Long = 0,
    val level: Int = 0,
    val official: String = "",
)

data class SearchPage(
    val items: List<VideoItem>,
    val bangumiItems: List<BangumiItem> = emptyList(),
    val hasMore: Boolean,
    val blocked: Boolean = false,
    val userItems: List<SearchUser> = emptyList(),
)

data class DynamicPage(
    val items: List<VideoItem>,
    val nextOffset: String?,
    val hasMore: Boolean,
)

data class QrTicket(val url: String, val key: String)

enum class QrStatus { Waiting, Scanned, Success, Expired, Failed }

data class Partition(val name: String, val tid: Int, val tint: Long = 0xFFFB7299) {
    val initial: String get() = name.take(1)
}

enum class ThemeMode { System, Light, Dark }

enum class CdnMode { Auto, Manual, Server }

data class CdnLine(val id: String, val name: String, val host: String) {
    companion object {
        val all: List<CdnLine> = listOf(
            CdnLine("ali", "阿里云", "upos-sz-mirrorali.bilivideo.com"),
            CdnLine("aliov", "阿里云海外", "upos-sz-mirroraliov.bilivideo.com"),
            CdnLine("cos", "腾讯云", "upos-sz-mirrorcos.bilivideo.com"),
            CdnLine("cosb", "腾讯云 VOD", "upos-sz-mirrorcosb.bilivideo.com"),
            CdnLine("hw", "华为云", "upos-sz-mirrorhw.bilivideo.com"),
            CdnLine("hwb", "华为云 B", "upos-sz-mirrorhwb.bilivideo.com"),
            CdnLine("08c", "华为云 08c", "upos-sz-mirror08c.bilivideo.com"),
            CdnLine("tf_hw", "华为云 TF", "upos-tf-all-hw.bilivideo.com"),
            CdnLine("tf_tx", "腾讯云 TF", "upos-tf-all-tx.bilivideo.com"),
        )

        fun byId(id: String): CdnLine? = all.firstOrNull { it.id == id }
    }
}

/** 壳层导航。Auto 按窗口宽高在底栏和侧栏之间切换。 */
enum class ShellLayout { Auto, Bottom, Rail }

/** 首页推荐来源。Web = 网页版推荐，App = 客户端推荐流。 */
enum class RecommendSource { Web, App }

/** 用户手动屏蔽的 UP 主。mid 用于精确匹配，name 用于没有 mid 的列表。 */
data class BlockedUp(
    val mid: Long = 0,
    val name: String = "",
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val shellLayout: ShellLayout = ShellLayout.Auto,
    val recommendSource: RecommendSource = RecommendSource.Web,
    val danmakuEnabled: Boolean = true,
    val danmakuAreaPercent: Int = 25,
    val danmakuSmartFilter: Boolean = true,
    val danmakuColorful: Boolean = true,
    /** 半透明圆角胶囊底衬：亮画面上也能看清白字。 */
    val danmakuPillBackground: Boolean = true,
    val danmakuBlockKeywords: String = "",
    /** 类型过滤。四个都开等于不过滤。关掉某一类才把它从画面去掉。 */
    val danmakuFilterScroll: Boolean = true,
    val danmakuFilterFixed: Boolean = true,
    val danmakuFilterColorful: Boolean = true,
    val danmakuFilterAdvanced: Boolean = true,
    val defaultQn: Int = 80,
    val noLogin1080: Boolean = true,
    val sponsorBlock: Boolean = false,
    /** 逗号分隔的 SponsorBlock category。默认只跳赞助广告。 */
    val sponsorCategories: String = SponsorCategories.DEFAULT,
    val cdnMode: CdnMode = CdnMode.Server,
    val cdnManualId: String = "ali",
    /** 关掉之后会使用带 mcdn 的地址。默认丢掉这类线路。 */
    val blockPcdn: Boolean = true,
    /** 桌面端缓冲策略：true=按 CDN 实测带宽自动挑预读深度，false=用固定值。 */
    val bufferAuto: Boolean = true,
    /** 手动缓冲值（毫秒）。自动档下只作为兜底参考。 */
    val networkCachingMs: Int = NETWORK_CACHING_DEFAULT_MS,
    /** 空字符串表示默认的 B 站粉。跟随系统时忽略。 */
    val accentHex: String = "",
    val accentFollowSystem: Boolean = false,
    val autoCheckUpdate: Boolean = true,
    /** 用户点过「跳过此版本」的 tag。自动检查对这个版本不再弹。 */
    val skippedVersion: String = "",
    /** 同意过的免责声明版本。小于 [DISCLAIMER_VERSION] 时启动要再确认。 */
    val acceptedDisclaimerVersion: Int = 0,
    /** 桌面端：点窗口关闭键时收进托盘。默认开。 */
    val closeToTray: Boolean = true,
    val playerVolume: Float = 0.8f,
    val lastSearchKind: ContentKind = ContentKind.Video,
    /** 桌面端下载保存目录；空字符串表示应用缓存。 */
    val downloadDir: String = "",
    /** 标题关键词屏蔽，多个词用空格/逗号分开。 */
    val filterTitleKeyword: String = "",
    /** UP 主名称关键词屏蔽。 */
    val filterUpKeyword: String = "",
    /** 一键屏蔽的 UP 主（精确规则）。 */
    val blockedUps: List<BlockedUp> = emptyList(),
    /** 一键屏蔽的视频标题（精确规则）。 */
    val blockedTitles: List<String> = emptyList(),
) {
    val disclaimerAccepted: Boolean get() = acceptedDisclaimerVersion >= DISCLAIMER_VERSION
}

/** 改免责声明正文时 +1。 */
const val DISCLAIMER_VERSION = 2

/** 网络缓冲（VLC :network-caching）的夹取范围，与设置页档位一致。 */
const val NETWORK_CACHING_MIN_MS = 300
const val NETWORK_CACHING_MAX_MS = 3000
const val NETWORK_CACHING_DEFAULT_MS = 1000

data class Session(
    val sessData: String? = null,
    val biliJct: String? = null,
    val dedeUserId: String? = null,
    val dedeUserIdCkMd5: String? = null,
    val buvid3: String? = null,
    val buvid4: String? = null,
    /** 设备第一次出现的时间，秒。登录换 SESSDATA 时必须和 buvid 一起带上。 */
    val bNut: String? = null,
) {
    val loggedIn: Boolean get() = !sessData.isNullOrBlank()

    fun cookieHeader(): String {
        val parts = mutableListOf<String>()
        sessData?.takeIf { it.isNotBlank() }?.let { parts += "SESSDATA=$it" }
        biliJct?.takeIf { it.isNotBlank() }?.let { parts += "bili_jct=$it" }
        dedeUserId?.takeIf { it.isNotBlank() }?.let { parts += "DedeUserID=$it" }
        dedeUserIdCkMd5?.takeIf { it.isNotBlank() }?.let { parts += "DedeUserID__ckMd5=$it" }
        buvid3?.takeIf { it.isNotBlank() }?.let { parts += "buvid3=$it" }
        buvid4?.takeIf { it.isNotBlank() }?.let { parts += "buvid4=$it" }
        bNut?.takeIf { it.isNotBlank() }?.let { parts += "b_nut=$it" }
        return parts.joinToString("; ")
    }
}

data class CacheEntry(
    val bvid: String,
    val title: String,
    val cover: String,
    val videoPath: String,
    val audioPath: String?,
    val qn: Int,
)

object PartitionCatalog {
    val all: List<Partition> = listOf(
        Partition("动画", 1005, 0xFF6D69E4),
        Partition("游戏", 1008, 0xFF57D587),
        Partition("鬼畜", 1007, 0xFFFB813A),
        Partition("音乐", 1003),
        Partition("舞蹈", 1004),
        Partition("影视", 1001),
        Partition("娱乐", 1002),
        Partition("知识", 1010),
        Partition("科技数码", 1012),
        Partition("资讯", 1009),
        Partition("美食", 1020),
        Partition("小剧场", 1021),
        Partition("汽车", 1013),
        Partition("时尚美妆", 1014),
        Partition("体育运动", 1018),
        Partition("动物", 1024),
        Partition("vlog", 1029),
        Partition("绘画", 1006),
        Partition("人工智能", 1011),
        Partition("家装房产", 1015),
        Partition("户外潮流", 1016),
        Partition("健身", 1017),
        Partition("手工", 1019),
        Partition("旅游出行", 1022),
        Partition("三农", 1023),
        Partition("亲子", 1025),
        Partition("健康", 1026),
        Partition("情感", 1027),
        Partition("生活兴趣", 1030),
        Partition("生活经验", 1031),
    )
}
