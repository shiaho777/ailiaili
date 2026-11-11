package app.ailiaili.core.network

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.AudioQuality
import app.ailiaili.core.model.BangumiDetail
import app.ailiaili.core.model.BangumiItem
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.CommentPage
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.DynamicPage
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.FollowUser
import app.ailiaili.core.model.ItemPage
import app.ailiaili.core.model.LivePlayInfo
import app.ailiaili.core.model.LiveQuality
import app.ailiaili.core.model.LiveRoomInfo
import app.ailiaili.core.model.MsgItem
import app.ailiaili.core.model.MsgSession
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.audioQnLabel
import app.ailiaili.core.model.QrStatus
import app.ailiaili.core.model.RecommendSource
import app.ailiaili.core.model.QrTicket
import app.ailiaili.core.model.SearchPage
import app.ailiaili.core.model.Session
import app.ailiaili.core.model.SpaceCollection
import app.ailiaili.core.model.SpaceFeed
import app.ailiaili.core.model.SpaceInfo
import app.ailiaili.core.model.SpacePost
import app.ailiaili.core.model.SponsorSegment
import app.ailiaili.core.model.UserState
import app.ailiaili.core.model.VideoDetail
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.VideoShot
import app.ailiaili.core.model.ViewChapter
import app.ailiaili.core.model.normalizeUrl
import app.ailiaili.core.model.epochMs
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.model.searchReferer
import app.ailiaili.core.model.searchType
import app.ailiaili.core.model.signed
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

object CdnStatus {
    val report = MutableStateFlow("还没有测速。播放视频时会自动测一次。")
}

class BiliClient(
    private val session: () -> Session,
    private val updateSession: (Session) -> Unit,
) {
    private val http = SharedHttp.api

    private var wbiImg: String? = null
    private var wbiSub: String? = null
    private var laterAids: Set<Long>? = null
    private var laterAt: Long = 0L

    /**
     * 免登录 1080P 的“设备指纹”参数必须在一台设备/一个进程内保持稳定。
     *
     * 以前每次 playUrl 都重新随机；同一个 buvid 却不断更换硬件特征，长驻进程
     * 会被服务端判成爬虫并稳定返回 -412。这里只生成一次，进程内复用。
     */
    private val dmImgStr = randomBase64(16, 64)
    private val dmCoverImgStr = randomBase64(32, 128)

    suspend fun ensureBuvid() = withContext(ioDispatcher) {
        val cur = session()
        var next = cur
        if (next.buvid3.isNullOrBlank() || next.buvid4.isNullOrBlank()) {
            val data = get("https://api.bilibili.com/x/frontend/finger/spi", emptyMap()).data
            if (data != null) {
                if (next.buvid3.isNullOrBlank()) data.str("b_3").takeIf { it.isNotBlank() }?.let { next = next.copy(buvid3 = it) }
                if (next.buvid4.isNullOrBlank()) data.str("b_4").takeIf { it.isNotBlank() }?.let { next = next.copy(buvid4 = it) }
            }
        }
        if (next.bNut.isNullOrBlank()) {
            next = next.copy(bNut = (epochMs() / 1000).toString())
        }
        if (next != cur) updateSession(next)
    }

    suspend fun userState(): Result<UserState> = api {
        val data = get("https://api.bilibili.com/x/web-interface/nav", emptyMap()).data
            ?: return@api UserState(false)
        val logged = data.optBoolean("isLogin", false)
        val level = data.obj("level_info")
        UserState(
            loggedIn = logged,
            mid = data.lng("mid"),
            name = data.str("uname"),
            face = app.ailiaili.core.model.normalizeUrl(data.str("face")),
            level = level?.int("current_level") ?: 0,
            coins = data.dbl("money"),
            expCurrent = level?.lng("current_exp") ?: 0,
            expNext = level?.lng("next_exp") ?: 0,
        )
    }

    suspend fun navStat(): Result<app.ailiaili.core.model.NavStat> = api {
        if (!session().loggedIn) error("需要登录")
        val data = get("https://api.bilibili.com/x/web-interface/nav/stat", emptyMap()).data
            ?: error("计数获取失败")
        app.ailiaili.core.model.NavStat(
            following = data.lng("following"),
            follower = data.lng("follower"),
            dynamicCount = data.lng("dynamic_count"),
        )
    }

    suspend fun relationStat(mid: Long): Result<Pair<Long, Long>> = api {
        val data = get(
            "https://api.bilibili.com/x/relation/stat",
            mapOf("vmid" to mid.toString()),
            referer = "https://space.bilibili.com/$mid/",
        ).data ?: error("关系数获取失败")
        data.lng("following") to data.lng("follower")
    }

    suspend fun popular(page: Int): Result<List<VideoItem>> = listOf(
        "https://api.bilibili.com/x/web-interface/popular",
        mapOf("pn" to page.toString(), "ps" to "20"),
        "list",
    )

    suspend fun ranking(): Result<List<VideoItem>> = listOf(
        "https://api.bilibili.com/x/web-interface/ranking/v2",
        mapOf("rid" to "0", "type" to "all"),
        "list",
    )

    suspend fun regionRank(rid: Int): Result<List<VideoItem>> = listOf(
        "https://api.bilibili.com/x/web-interface/ranking/v2",
        mapOf("rid" to rid.toString(), "type" to "all"),
        "list",
    )

    suspend fun regionFeed(tid: Int, displayId: Int): Result<RegionFeedDecode> = api {
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/web-interface/region/feed/rcmd",
            mapOf(
                "display_id" to displayId.coerceAtLeast(1).toString(),
                "request_cnt" to "20",
                "from_region" to tid.toString(),
                "device" to "web",
                "plat" to "30",
            ),
        )
        val page = decodeRegionFeed(resp.code, resp.message, resp.data)
        if (!page.ok) error(page.error ?: "加载失败")
        page
    }

    suspend fun recommend(
        brush: Int = 1,
        source: RecommendSource = RecommendSource.Web,
    ): Result<List<VideoItem>> = api {
        ensureBuvid()
        if (source == RecommendSource.App) return@api recommendApp(brush)
        val cursor = brush.coerceAtLeast(1).toString()
        val params = mutableMapOf(
            "fresh_type" to "4",
            "ps" to "30",
            "fresh_idx" to cursor,
            "fresh_idx_1h" to cursor,
            "brush" to cursor,
            "web_location" to "1430650",
        )
        val data = get(
            "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd",
            params,
            sign = true,
        ).data ?: error("推荐为空")
        val items = data.arr("item")?.objects().orEmpty().mapNotNull(::parseRecommend)
        if (items.isEmpty()) error("推荐为空")
        items
    }.recoverCatching { popular(1).getOrThrow() }

    private suspend fun recommendApp(brush: Int): List<VideoItem> {
        val data = get(
            "https://app.bilibili.com/x/v2/feed/index",
            mapOf(
                "build" to "1",
                "mobi_app" to "android",
                "platform" to "android",
                "idx" to brush.coerceAtLeast(1).toString(),
                "pull" to "0",
                "login_event" to "0",
                "network" to "wifi",
                "style" to "2",
                "av" to "0",
            ),
            referer = "https://www.bilibili.com/",
        ).data ?: error("推荐为空")
        val items = data.arr("items")?.objects().orEmpty().mapNotNull(::parseAppRecommend)
        if (items.isEmpty()) error("推荐为空")
        return items
    }

    suspend fun search(
        keyword: String,
        page: Int,
        kind: ContentKind = ContentKind.Video,
    ): Result<SearchPage> = api {
        ensureBuvid()
        var extraFt = if (kind == ContentKind.Bangumi) mediaFt(keyword, page) else emptyList()
        val params = mutableMapOf(
            "search_type" to kind.searchType(),
            "keyword" to keyword,
            "page" to page.toString(),
        )

        // 一次搜索尝试。风控统一折成 blocked=true，由外层决定是否走降级链。
        suspend fun attempt(signed: Boolean, referer: String): SearchPage {
            val url = if (signed) {
                "https://api.bilibili.com/x/web-interface/wbi/search/type"
            } else {
                "https://api.bilibili.com/x/web-interface/search/type"
            }
            val resp = get(url, params, sign = signed, referer = referer)
            if (resp.code != 0) {
                if (app.ailiaili.core.model.isSearchRiskCode(resp.code)) {
                    return SearchPage(emptyList(), emptyList(), false, blocked = true)
                }
                error(resp.message ?: "搜索失败")
            }
            // 游客态最常见的拦截形态不是错误码，而是 code=0 + v_voucher 挑战。
            if (resp.data?.has("v_voucher") == true) {
                return SearchPage(emptyList(), emptyList(), false, blocked = true)
            }
            return decodeSearchSuccess(kind, page, resp.data, extraFt)
        }

        val primary = attempt(kind.signed(), kind.searchReferer() ?: REFERER)
        if (!primary.blocked) return@api primary

        // ① 裸端点：不带 WBI 签名。游客态经常是“签名端点被盯上，裸端点反而放行”。
        if (kind.signed()) {
            val bare = attempt(signed = false, referer = "https://search.bilibili.com/")
            if (!bare.blocked) return@api bare
        }

        // ② 换一对全新 buvid 再试一次。只轮换一次：每次失败都换身份反而更像脚本。
        rotateBuvid()
        // 番剧的 media_ft 是补充结果，换指纹后重新问一次，避免纪录片被旧指纹的失败静默丢掉。
        if (kind == ContentKind.Bangumi) extraFt = mediaFt(keyword, page)
        attempt(kind.signed(), kind.searchReferer() ?: REFERER)
    }

    /**
     * 风控专用：从 spi 领一对全新 buvid 并强制覆盖内存/磁盘。
     *
     * 与 [ensureBuvid] 的区别是“覆盖”而不是“补缺”。常规路径必须保持一对
     * buvid 用到底（推荐系统靠它认设备）；只有搜索被风控时才允许换身份。
     */
    private suspend fun rotateBuvid() {
        val data = get("https://api.bilibili.com/x/frontend/finger/spi", emptyMap()).data ?: return
        val b3 = data.str("b_3")
        val b4 = data.str("b_4")
        if (b3.isBlank() && b4.isBlank()) return
        val cur = session()
        updateSession(
            cur.copy(
                buvid3 = b3.ifBlank { cur.buvid3.orEmpty() }.takeIf { it.isNotBlank() },
                buvid4 = b4.ifBlank { cur.buvid4.orEmpty() }.takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun bangumiDetail(seasonId: Long): Result<BangumiDetail> = api {
        if (seasonId <= 0) error("没有番剧号")
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/pgc/view/web/season",
            mapOf("season_id" to seasonId.toString()),
            bodyKey = "result",
        )
        if (resp.code != 0 || resp.data == null) error(resp.message ?: "加载选集失败")
        parseBangumiDetail(seasonId, resp.data)
    }

    private suspend fun mediaFt(keyword: String, page: Int): List<BangumiItem> = runCatching {
        val resp = get(
            "https://api.bilibili.com/x/web-interface/search/type",
            mutableMapOf(
                "search_type" to "media_ft",
                "keyword" to keyword,
                "page" to page.toString(),
            ),
            sign = false,
            referer = "https://search.bilibili.com/",
        )
        if (resp.code != 0) emptyList()
        else resp.data?.arr("result")?.objects().orEmpty().mapNotNull(::parseBangumiItem)
    }.getOrDefault(emptyList())

    suspend fun hotSearch(): Result<List<String>> = api {
        val data = get(
            "https://api.bilibili.com/x/web-interface/search/square",
            mapOf("limit" to "10"),
        ).data
        val list = data?.obj("trending")?.arr("list")?.objects().orEmpty()
        list.map { it.str("keyword") }.filter { it.isNotBlank() }
    }

    suspend fun video(bvid: String): Result<VideoDetail> = api {
        ensureBuvid()
        if (bvid.isBlank()) error("没有视频号")
        val resp = get(
            "https://api.bilibili.com/x/web-interface/view",
            mapOf("bvid" to bvid),
        )
        val data = resp.data
        if (resp.code != 0 || data == null) {
            error(friendly(resp.code, resp.message ?: "获取视频信息失败"))
        }
        parseDetail(bvid, data)
    }

    /** 简介。view 被风控时这条仍可用，data 是纯字符串。 */
    suspend fun description(bvid: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            ensureBuvid()
            val body = text("https://api.bilibili.com/x/web-interface/archive/desc?bvid=$bvid", REFERER)
                ?: error("简介获取失败")
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{")) error("简介获取失败")
            val root = JSONObject(trimmed)
            val code = root.optInt("code", -999)
            if (code != 0) error(friendly(code, root.optString("message").ifBlank { "简介获取失败" }))
            root.optString("data")
        }
    }

    suspend fun related(bvid: String): Result<List<VideoItem>> = api {
        ensureBuvid()
        val resp = get("https://api.bilibili.com/x/web-interface/archive/related", mapOf("bvid" to bvid))
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "相关视频加载失败"))
        resp.data?.arr("list")?.objects().orEmpty().mapNotNull { parseVideo(it) }
    }

    suspend fun tags(bvid: String): Result<List<String>> = api {
        ensureBuvid()
        val resp = get("https://api.bilibili.com/x/tag/archive/tags", mapOf("bvid" to bvid))
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "标签加载失败"))
        resp.data?.arr("list")?.objects().orEmpty().map { it.str("tag_name") }.filter { it.isNotBlank() }
    }

    /** 分 P 列表。view 被风控（-412）时仍通常可用，用来拿到 cid 再去要播放地址。 */
    suspend fun pages(bvid: String): Result<List<app.ailiaili.core.model.VideoPage>> = api {
        ensureBuvid()
        if (bvid.isBlank()) error("没有视频号")
        val resp = get("https://api.bilibili.com/x/player/pagelist", mapOf("bvid" to bvid))
        val data = resp.data
        if (resp.code != 0 || data == null) error(friendly(resp.code, resp.message ?: "分P信息获取失败"))
        data.arr("list")?.objects().orEmpty().map {
            app.ailiaili.core.model.VideoPage(it.lng("cid"), it.int("page"), it.str("part"), it.int("duration"))
        }.filter { it.cid > 0 }
    }

    suspend fun playUrl(
        bvid: String,
        cid: Long,
        settings: AppSettings,
        forceDash: Boolean = false,
        qnOverride: Int? = null,
    ): Result<PlayInfo> = api {
        ensureBuvid()
        val qn = qnOverride?.coerceAtLeast(0)?.takeIf { it > 0 } ?: settings.defaultQn

        // 720P 及以下优先走 durl 合流单流：桌面 VLC 对 DASH 双流的远距离
        // seek 不可靠，而 durl 是普通 MP4，拖动进度条一次到位。
        // 1080P 及以上继续走 DASH，避免被 durl 的 720P 上限锁死画质。
        // 听视频模式需要独立音轨，必须强制 DASH（durl 没有 audioUrl）。
        if (!forceDash && app.ailiaili.core.model.shouldPreferDurl(qn)) {
            parseDurl(bvid, cid, qn)?.let { return@api it }
            // durl 取不到（多段切片/大会员/该档无源）时回落到 DASH，行为与旧版一致。
        }

        val ps = mutableMapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
        )
        if (!session().loggedIn && settings.noLogin1080) {
            ps["try_look"] = "1"
            ps["dm_img_list"] = "[]"
            ps["dm_img_str"] = dmImgStr
            ps["dm_cover_img_str"] = dmCoverImgStr
            ps["dm_img_inter"] = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
        }
        val resp = get("https://api.bilibili.com/x/player/wbi/playurl", ps, sign = true)
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "获取播放地址失败"))
        val data = resp.data
        val dash = data?.obj("dash")
        if (dash != null) {
            val parsed = parseDash(dash, data.lng("timelength"), qn, settings)
            if (parsed != null) return@api parsed
        }
        parseDurl(bvid, cid, qn) ?: error(resp.message ?: "没有可播放的地址，可能是大会员或付费专属")
    }

    /** 弹幕按 6 分钟一段。segmentIndex 从 1 开始。304 表示这一段是空的。 */
    suspend fun danmakuSegment(cid: Long, segmentIndex: Int): Result<List<Danmaku>> = withContext(ioDispatcher) {
        runCatching {
            ensureBuvid()
            if (cid <= 0 || segmentIndex < 1) return@runCatching emptyList()
            val url = "https://api.bilibili.com/x/v2/dm/web/seg.so?type=1&oid=$cid&segment_index=$segmentIndex"
            val bytes = danmakuBytes(url) ?: error("弹幕段加载失败")
            if (bytes.isEmpty()) emptyList() else parseDanmakuSeg(bytes).sortedBy { it.timeSec }
        }
    }

    suspend fun chapters(bvid: String, cid: Long): Result<List<ViewChapter>> = api {
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/player/wbi/v2",
            mutableMapOf("bvid" to bvid, "cid" to cid.toString()),
            sign = true,
        ).data ?: return@api emptyList()
        data.arr("view_points")?.objects().orEmpty().mapNotNull { point ->
            if (point.int("type") != 2) return@mapNotNull null
            val title = point.str("content")
            if (title.isBlank()) return@mapNotNull null
            ViewChapter(point.int("from"), point.int("to"), title)
        }
    }

    suspend fun videoShot(bvid: String, cid: Long): Result<VideoShot?> = api {
        val data = get(
            "https://api.bilibili.com/x/player/videoshot",
            mapOf("bvid" to bvid, "cid" to cid.toString(), "index" to "1"),
        ).data ?: return@api null
        val images = data.arr("image")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { add(normalizeUrl(it)) }
                }
            }
        }.orEmpty()
        val index = data.arr("index")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) add(arr.optInt(i))
            }
        }.orEmpty()
        if (images.isEmpty() || index.isEmpty()) return@api null
        VideoShot(
            images = images,
            index = index,
            columns = data.int("img_x_len").coerceAtLeast(1),
            rows = data.int("img_y_len").coerceAtLeast(1),
            cellWidth = data.int("img_x_size"),
            cellHeight = data.int("img_y_size"),
        )
    }

    suspend fun heartbeat(bvid: String, cid: Long, progressSec: Long): Result<Unit> {
        if (!session().loggedIn || bvid.isBlank() || cid <= 0) return Result.success(Unit)
        return postOk(
            "https://api.bilibili.com/x/click-interface/web/heartbeat",
            mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "played_time" to progressSec.coerceAtLeast(0).toString(),
                "type" to "3",
            ),
        )
    }

    /**
     * 评论。mode 3 热门，2 时间，与网页一致。
     * 置顶在 data.top，不在 replies 里；首页会把它插到最前。
     */
    suspend fun comments(aid: Long, page: Int, mode: Int = 3): Result<CommentPage> = api {
        val resp = get(
            "https://api.bilibili.com/x/v2/reply/main",
            mapOf(
                "oid" to aid.toString(),
                "type" to "1",
                "mode" to mode.toString(),
                "pn" to page.toString(),
                "ps" to "20",
            ),
        )
        val data = resp.data
        if (resp.code != 0 || data == null) error(friendly(resp.code, resp.message ?: "评论加载失败"))
        val total = data.obj("cursor")?.int("all_count") ?: 0
        val upperMid = data.obj("upper")?.lng("mid") ?: 0
        val items = mutableListOf<Comment>()
        if (page <= 1) {
            val top = data.obj("top")
            val pinned = top?.obj("upper") ?: top?.obj("admin")
            if (pinned != null) items += parseComment(pinned, upperMid).copy(pinned = true)
        }
        val roots = data.arr("replies")?.objects().orEmpty()
        items += roots.map { parseComment(it, upperMid) }
        CommentPage(
            items = items.distinctBy { it.rpid },
            total = total,
            page = page,
            hasMore = roots.size >= 20 && (total <= 0 || page * 20 < total),
        )
    }

    /** 某条评论下的楼中楼。首页只内联前几条。 */
    suspend fun replies(aid: Long, root: Long, page: Int): Result<List<Comment>> = api {
        val resp = get(
            "https://api.bilibili.com/x/v2/reply/reply",
            mapOf(
                "oid" to aid.toString(),
                "type" to "1",
                "root" to root.toString(),
                "pn" to page.toString(),
                "ps" to "20",
            ),
        )
        val data = resp.data
        if (resp.code != 0 || data == null) error(friendly(resp.code, resp.message ?: "回复加载失败"))
        val upperMid = data.obj("upper")?.lng("mid") ?: 0
        data.arr("replies")?.objects().orEmpty().map { parseComment(it, upperMid, withReplies = false) }
    }

    /** 正在看的人数。失败时播放页不展示。 */
    suspend fun online(bvid: String, cid: Long): Result<String> = api {
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/player/online/total",
            mapOf("bvid" to bvid, "cid" to cid.toString()),
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "在线人数获取失败"))
        val data = resp.data ?: error("在线人数获取失败")
        data.str("total").ifBlank { data.str("count") }.ifBlank { error("在线人数获取失败") }
    }

    suspend fun postComment(aid: Long, message: String, root: Long = 0, parent: Long = 0): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val text = message.trim()
                if (aid <= 0) error("没拿到稿件号，暂时不能评论")
                if (text.isEmpty()) error("评论内容不能为空")
                val form = mutableMapOf(
                    "oid" to aid.toString(),
                    "type" to "1",
                    "message" to text.take(1000),
                    "plat" to "1",
                    "ordering" to "heat",
                )
                if (root > 0) {
                    form["root"] = root.toString()
                    form["parent"] = (if (parent > 0) parent else root).toString()
                }
                val (code, msg) = postRaw("https://api.bilibili.com/x/v2/reply/add", form, REFERER)
                if (code != 0) error(commentFriendly(code, msg))
            }
        }

    suspend fun commentLike(aid: Long, rpid: Long, like: Boolean): Result<Unit> =
        commentReact("https://api.bilibili.com/x/v2/reply/action", aid, rpid, like)

    suspend fun commentDislike(aid: Long, rpid: Long, dislike: Boolean): Result<Unit> =
        commentReact("https://api.bilibili.com/x/v2/reply/hate", aid, rpid, dislike)

    private suspend fun commentReact(url: String, aid: Long, rpid: Long, on: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                if (aid <= 0 || rpid <= 0) error("参数不完整")
                val (code, msg) = postRaw(
                    url,
                    mapOf(
                        "oid" to aid.toString(),
                        "type" to "1",
                        "rpid" to rpid.toString(),
                        "action" to if (on) "1" else "0",
                    ),
                    REFERER,
                )
                if (code != 0) error(commentFriendly(code, msg))
            }
        }

    suspend fun dynamics(offset: String?): Result<DynamicPage> = api {
        ensureBuvid()
        if (!session().loggedIn) error("请先登录后查看动态")
        val ps = mutableMapOf<String, String>()
        if (!offset.isNullOrBlank()) ps["offset"] = offset
        val data = get(
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all",
            ps,
            sign = true,
            referer = "https://t.bilibili.com/",
        ).let { resp ->
            if (resp.code == -101) error("请先登录后查看动态")
            resp.data ?: error(resp.message ?: "动态加载失败")
        }
        val items = data.arr("items")?.objects().orEmpty().mapNotNull { parseDynamic(it) }
        DynamicPage(
            items = items,
            nextOffset = data.str("offset").ifBlank { null },
            hasMore = data.optBoolean("has_more") || data.int("has_more") == 1,
        )
    }

    suspend fun space(mid: Long): Result<SpaceInfo> = api {
        ensureBuvid()
        val referer = "https://space.bilibili.com/$mid/"
        val (wbi, cardResp) = coroutineScope {
            val infoCall = async {
                get(
                    "https://api.bilibili.com/x/space/wbi/acc/info",
                    mutableMapOf("mid" to mid.toString()),
                    sign = true,
                    referer = referer,
                )
            }
            val cardCall = async {
                get(
                    "https://api.bilibili.com/x/web-interface/card",
                    mapOf("mid" to mid.toString()),
                    referer = referer,
                )
            }
            infoCall.await() to cardCall.await()
        }
        val acc = wbi.data?.takeIf { wbi.code == 0 }
        val cardRoot = cardResp.data?.takeIf { cardResp.code == 0 }
        val card = cardRoot?.obj("card")
        val legacy = if (acc == null && card == null) {
            val old = get(
                "https://api.bilibili.com/x/space/acc/info",
                mapOf("mid" to mid.toString()),
                referer = referer,
            )
            old.data?.takeIf { old.code == 0 }
        } else {
            null
        }
        val src = acc ?: card ?: legacy ?: error(wbi.message ?: cardResp.message ?: "空间信息获取失败")
        val level = acc?.int("level")?.takeIf { it > 0 }
            ?: card?.obj("level_info")?.int("current_level")?.takeIf { it > 0 }
            ?: card?.int("level")?.takeIf { it > 0 }
            ?: legacy?.int("level")
            ?: 0
        SpaceInfo(
            mid = mid,
            name = acc?.str("name").orEmpty().ifBlank { card?.str("name").orEmpty() }.ifBlank { src.str("name") }.ifBlank { src.str("uname") },
            face = app.ailiaili.core.model.normalizeUrl(
                acc?.str("face").orEmpty().ifBlank { card?.str("face").orEmpty() }.ifBlank { src.str("face") },
            ),
            sign = acc?.str("sign").orEmpty().ifBlank { card?.str("sign").orEmpty() }.ifBlank { src.str("sign") },
            follower = listOf(
                cardRoot?.lng("follower") ?: 0,
                card?.lng("fans") ?: 0,
                acc?.lng("fans") ?: 0,
                acc?.lng("follower") ?: 0,
            ).firstOrNull { it > 0 } ?: 0,
            following = listOf(
                cardRoot?.lng("following") ?: 0,
                card?.lng("attention") ?: 0,
                acc?.lng("attention") ?: 0,
                acc?.lng("following") ?: 0,
            ).firstOrNull { it > 0 } ?: 0,
            level = level,
            videoCount = cardRoot?.lng("archive_count")?.takeIf { it > 0 } ?: 0,
        )
    }

    suspend fun spaceVideos(mid: Long, page: Int, order: String = "pubdate"): Result<ItemPage<VideoItem>> = api {
        ensureBuvid()
        val referer = "https://space.bilibili.com/$mid/video"
        val sort = when (order) {
            "click", "stow" -> order
            else -> "pubdate"
        }
        val signed = get(
            "https://api.bilibili.com/x/space/wbi/arc/search",
            mutableMapOf(
                "mid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to "30",
                "tid" to "0",
                "keyword" to "",
                "order" to sort,
                "platform" to "web",
            ).apply {
                if (sort == "pubdate") {
                    put("web_location", "1550101")
                    put("order_avoided", "true")
                }
            },
            sign = true,
            referer = referer,
        )
        val fromSearch = if (signed.code == 0) {
            signed.data?.obj("list")?.arr("vlist")?.objects().orEmpty().mapNotNull { parseSpaceVideo(it, mid) }
        } else {
            emptyList()
        }
        if (fromSearch.isNotEmpty()) {
            val total = signed.data?.obj("page")?.lng("count") ?: 0
            return@api ItemPage(
                items = fromSearch,
                hasMore = if (total > 0) page * 30L < total else fromSearch.size >= 30,
                total = total,
            )
        }
        if (sort != "pubdate") error(friendly(signed.code, signed.message ?: "这个排序暂时拿不到"))
        val fallback = get(
            "https://api.bilibili.com/x/series/recArchivesByKeywords",
            mapOf(
                "mid" to mid.toString(),
                "keywords" to "",
                "pn" to page.toString(),
                "ps" to "30",
            ),
            referer = referer,
        )
        if (fallback.code != 0) {
            val code = if (fallback.code == -999) signed.code else fallback.code
            error(friendly(code, "投稿加载失败"))
        }
        val fallbackItems = fallback.data?.arr("archives")?.objects().orEmpty()
            .mapNotNull { parseSpaceVideo(it, mid) }
        ItemPage(
            items = fallbackItems,
            hasMore = fallbackItems.size >= 30,
            total = fallback.data?.lng("total") ?: 0,
        )
    }

    /** 空间列表往往只有播放数。首页卡片还要弹幕和点赞，这里补一次详情里的 stat。 */
    suspend fun videoStats(bvid: String): Result<Triple<Long, Long, Long>> = api {
        if (bvid.isBlank()) error("稿件不存在")
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/web-interface/view",
            mapOf("bvid" to bvid),
            referer = "https://www.bilibili.com/video/$bvid",
        ).data ?: error("播放数据加载失败")
        val stat = data.obj("stat")
        Triple(
            stat.countOf("view", "play").takeIf { it > 0 } ?: data.countOf("play", "view"),
            stat.countOf("danmaku").takeIf { it > 0 } ?: data.countOf("danmaku", "video_review"),
            stat.countOf("like"),
        )
    }

    private fun parseSpaceVideo(e: JSONObject, mid: Long): VideoItem? {
        val bvid = e.str("bvid")
        if (bvid.isBlank()) return null
        val stat = e.obj("stat")
        val owner = e.obj("owner")
        val seconds = e.int("duration")
        val authorObject = e.obj("author")
        return VideoItem(
            bvid = bvid,
            aid = e.lng("aid"),
            cid = e.cidOrHistory(),
            title = e.str("title").stripHtml(),
            cover = app.ailiaili.core.model.normalizeUrl(e.str("pic").ifBlank { e.str("cover") }),
            author = owner?.str("name").orEmpty()
                .ifBlank { e.str("author") }
                .ifBlank { authorObject?.str("name").orEmpty() },
            ownerMid = owner?.lng("mid")?.takeIf { it > 0 }
                ?: authorObject?.lng("mid")?.takeIf { it > 0 }
                ?: mid,
            durationText = e.str("length").ifBlank { e.str("duration_text") }
                .ifBlank { app.ailiaili.core.model.formatSeconds(seconds) },
            durationSec = seconds,
            viewCount = stat.countOf("view", "play").takeIf { it > 0 } ?: e.countOf("play", "view"),
            danmakuCount = stat.countOf("danmaku", "video_review").takeIf { it > 0 }
                ?: e.countOf("video_review", "danmaku"),
            likeCount = stat.countOf("like").takeIf { it > 0 } ?: e.countOf("like"),
            pubdate = e.lng("pubdate").takeIf { it > 0 } ?: e.lng("created"),
            statsReady = (stat.countOf("danmaku", "video_review") > 0 || e.countOf("video_review", "danmaku") > 0
                || stat.countOf("like") > 0 || e.countOf("like") > 0),
        )
    }

    suspend fun spaceDynamics(mid: Long, offset: String?): Result<SpaceFeed> = api {
        ensureBuvid()
        val params = mutableMapOf(
            "host_mid" to mid.toString(),
            "timezone_offset" to "-480",
            "platform" to "web",
            "features" to "itemOpusStyle",
        )
        if (!offset.isNullOrBlank()) params["offset"] = offset
        val resp = get(
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space",
            params,
            sign = true,
            referer = "https://space.bilibili.com/$mid/dynamic",
        )
        if (resp.code != 0 || resp.data == null) error(resp.message ?: "动态加载失败")
        val data = resp.data!!
        val posts = data.arr("items")?.objects().orEmpty().mapIndexedNotNull { index, item ->
            parseSpacePost(item, mid)?.let { post ->
                if (post.id.isBlank()) post.copy(id = "post-$index-${post.video?.bvid.orEmpty()}") else post
            }
        }
        SpaceFeed(
            posts = posts,
            nextOffset = data.str("offset").ifBlank { null },
            hasMore = data.optBoolean("has_more") || data.int("has_more") == 1,
        )
    }

    suspend fun spaceCollections(mid: Long, page: Int = 1): Result<ItemPage<SpaceCollection>> = api {
        ensureBuvid()
        val pageSize = 20
        val resp = get(
            "https://api.bilibili.com/x/polymer/web-space/seasons_series_list",
            mapOf("mid" to mid.toString(), "page_num" to page.toString(), "page_size" to pageSize.toString()),
            referer = "https://space.bilibili.com/$mid/lists",
        )
        if (resp.code != 0) error(resp.message ?: "合集加载失败")
        val lists = resp.data?.obj("items_lists")
        if (lists == null) return@api ItemPage(emptyList(), false)
        val seasons = lists.arr("seasons_list")?.objects().orEmpty().mapNotNull { parseSpaceCollection(it, series = false) }
        val series = lists.arr("series_list")?.objects().orEmpty().mapNotNull { parseSpaceCollection(it, series = true) }
        val items = seasons + series
        val total = lists.obj("page")?.int("total") ?: 0
        val hasMore = if (total > 0) page * pageSize < total else items.size >= pageSize
        ItemPage(items, hasMore)
    }

    suspend fun spaceCollectionVideos(mid: Long, id: Long, series: Boolean, page: Int): Result<List<VideoItem>> = api {
        ensureBuvid()
        val referer = "https://space.bilibili.com/$mid/lists"
        val resp = if (series) {
            get(
                "https://api.bilibili.com/x/series/archives",
                mapOf(
                    "mid" to mid.toString(),
                    "series_id" to id.toString(),
                    "only_normal" to "true",
                    "sort" to "desc",
                    "pn" to page.toString(),
                    "ps" to "30",
                ),
                referer = referer,
            )
        } else {
            get(
                "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list",
                mapOf(
                    "mid" to mid.toString(),
                    "season_id" to id.toString(),
                    "page_num" to page.toString(),
                    "page_size" to "30",
                    "sort_reverse" to "false",
                ),
                referer = referer,
            )
        }
        if (resp.code != 0 || resp.data == null) error(resp.message ?: "合集加载失败")
        resp.data!!.arr("archives")?.objects().orEmpty().mapNotNull { parseSpaceVideo(it, mid) }
    }

    suspend fun spaceFolders(mid: Long, page: Int = 1): Result<ItemPage<FavFolder>> = api {
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/v3/fav/folder/created/list",
            mapOf("up_mid" to mid.toString(), "pn" to page.toString(), "ps" to "20"),
            referer = "https://space.bilibili.com/$mid/favlist",
        )
        if (resp.code != 0) error(resp.message ?: "收藏夹加载失败")
        val data = resp.data
        val folders = data?.arr("list")?.objects().orEmpty().map { folder ->
            FavFolder(
                id = folder.lng("id"),
                title = folder.str("title"),
                count = folder.int("media_count"),
                cover = app.ailiaili.core.model.normalizeUrl(folder.str("cover")),
                privateFolder = (folder.lng("attr") and 2L) != 0L,
            )
        }.filter { !it.privateFolder && it.id > 0 }
        val hasMore = when {
            data == null -> false
            data.has("has_more") -> data.int("has_more") == 1 || data.optBoolean("has_more")
            else -> folders.size >= 20
        }
        ItemPage(folders, hasMore)
    }

    suspend fun followings(mid: Long, page: Int): Result<ItemPage<FollowUser>> = api {
        if (!session().loggedIn) error("请先登录")
        ensureBuvid()
        val ps = 30
        val resp = get(
            "https://api.bilibili.com/x/relation/followings",
            mapOf(
                "vmid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to ps.toString(),
                "order" to "desc",
            ),
            referer = "https://space.bilibili.com/$mid/fans/follow",
        )
        if (resp.code == -101) error("请先登录")
        if (resp.code != 0) error(resp.message ?: "关注列表加载失败")
        val data = resp.data
        val users = data?.arr("list")?.objects().orEmpty().mapNotNull { user ->
            val id = user.lng("mid")
            val name = user.str("uname")
            if (id <= 0 || name.isBlank()) null
            else FollowUser(
                mid = id,
                name = name,
                face = app.ailiaili.core.model.normalizeUrl(user.str("face")),
                sign = user.str("sign"),
            )
        }
        val total = data?.int("total") ?: 0
        ItemPage(users, if (total > 0) page * ps < total else users.size >= ps)
    }

    /**
     * 我关注的主播里正在直播的列表。
     *
     * 直接走官方 PC 直播页左栏「关注」面板的接口，一次请求拿回全部正在直播的
     * 主播，不再受关注列表第一页 30 人的限制。
     *
     * 旧的两段式（先 /x/relation/followings 再 POST
     * /room/v1/Room/get_status_info_by_uids）有两个问题：
     * 状态接口只认 form 编码，JSON 请求会返回 invalid params；
     * 而且只查关注列表第一页，关注数超过 30 时后面的人开播永远看不到。
     */
    suspend fun followingLive(): Result<List<VideoItem>> = api {
        ensureBuvid()
        val resp = get(
            "https://api.live.bilibili.com/xlive/web-ucenter/v1/xfetter/GetWebList",
            mapOf("hit_ab" to "true"),
            referer = "https://live.bilibili.com/",
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "查询关注直播失败"))
        // code=0 但没有 data 当作「没人在播」：失败分支会保留上一次列表，
        // 主播下播后看板会一直挂着旧条目。
        parseFollowingLive(resp.data ?: return@api emptyList())
    }

    private fun parseSpaceCollection(e: JSONObject, series: Boolean): SpaceCollection? {
        val meta = e.obj("meta") ?: return null
        val id = if (series) meta.lng("series_id") else meta.lng("season_id")
        if (id <= 0) return null
        val owner = meta.lng("mid")
        val videos = e.arr("archives")?.objects().orEmpty().mapNotNull { parseSpaceVideo(it, owner) }
        return SpaceCollection(
            id = id,
            title = meta.str("title").ifBlank { meta.str("name") },
            cover = app.ailiaili.core.model.normalizeUrl(meta.str("cover")),
            count = meta.int("total").takeIf { it > 0 } ?: videos.size,
            series = series,
            videos = videos,
        )
    }

    private fun parseSpacePost(e: JSONObject, mid: Long): SpacePost? {
        val modules = e.obj("modules")
        if (modules == null) {
            val video = parseDynamic(e) ?: return null
            return SpacePost(id = video.bvid, text = "", timeText = "", video = video)
        }
        val dynamic = modules.obj("module_dynamic")
        var text = dynamicText(dynamic?.obj("desc"))
        val major = dynamic?.obj("major")
        var video: VideoItem? = null
        when (major?.str("type")) {
            "MAJOR_TYPE_ARCHIVE" -> video = major.obj("archive")?.let { archive ->
                parseSpaceVideo(archive, mid)?.let { item ->
                    val name = modules.obj("module_author")?.str("name").orEmpty()
                    if (item.author.isBlank() && name.isNotBlank()) item.copy(author = name) else item
                }
            }
            "MAJOR_TYPE_OPUS" -> {
                val opus = major.obj("opus")
                val title = opus?.str("title").orEmpty()
                val summary = dynamicText(opus?.obj("summary"))
                text = listOf(title, text, summary).filter { it.isNotBlank() }.distinct().joinToString("\n")
            }
            "MAJOR_TYPE_ARTICLE" -> {
                val title = major.obj("article")?.str("title").orEmpty()
                text = listOf(title, text).filter { it.isNotBlank() }.distinct().joinToString("\n")
            }
        }
        if (text.isBlank() && video == null) {
            val orig = e.obj("orig") ?: return null
            val nested = parseSpacePost(orig, mid) ?: return null
            return nested.copy(id = e.str("id_str").ifBlank { nested.id })
        }
        return SpacePost(
            id = e.str("id_str").ifBlank { e.lng("id").takeIf { it > 0 }?.toString().orEmpty() },
            text = text,
            timeText = modules.obj("module_author")?.str("pub_time").orEmpty(),
            video = video,
        )
    }

    private fun JSONObject?.countOf(vararg keys: String): Long {
        if (this == null) return 0
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val n = lng(key)
            if (n > 0) return n
        }
        return 0
    }

    private fun dynamicText(node: JSONObject?): String {
        if (node == null) return ""
        val plain = node.str("text")
        if (plain.isNotBlank()) return plain
        return node.arr("rich_text_nodes")?.objects().orEmpty().joinToString("") { it.str("text") }.trim()
    }

    private var liveHistoryViewAt = 0L
    private var liveHistoryMax = 0L

    suspend fun liveHistory(page: Int): Result<List<VideoItem>> = api {
        if (!session().loggedIn) error("云端历史需要登录")
        ensureBuvid()
        if (page <= 1) {
            liveHistoryViewAt = 0
            liveHistoryMax = 0
        }
        val ps = mutableMapOf(
            "type" to "live",
            "ps" to "20",
        )
        if (page > 1 && liveHistoryViewAt > 0) {
            ps["view_at"] = liveHistoryViewAt.toString()
            ps["max"] = liveHistoryMax.toString()
        }
        val data = get(
            "https://api.bilibili.com/x/web-interface/history/cursor",
            ps,
            referer = "https://www.bilibili.com/account/history",
        ).data ?: error("直播历史加载失败")
        val cursor = data.obj("cursor")
        liveHistoryViewAt = cursor?.lng("view_at") ?: 0
        liveHistoryMax = cursor?.lng("max") ?: 0
        data.arr("list")?.objects().orEmpty().mapNotNull { h ->
            var roomId = h.lng("kid")
            if (roomId <= 0) roomId = h.obj("history")?.lng("oid") ?: 0
            if (roomId <= 0) return@mapNotNull null
            val covers = h.arr("covers")
            val cover = h.str("cover").ifBlank {
                if (covers != null && covers.length() > 0) covers.optString(0) else ""
            }
            var author = h.str("author_name")
            var ownerMid = h.lng("author_mid")
            h.obj("owner")?.let { owner ->
                if (author.isBlank()) author = owner.str("name")
                if (ownerMid <= 0) ownerMid = owner.lng("mid")
            }
            VideoItem(
                roomId = roomId,
                title = h.str("title").stripHtml(),
                cover = normalizeUrl(cover),
                author = author,
                ownerMid = ownerMid,
                durationText = if (h.int("live_status") == 1) "直播中" else "未开播",
                pubdate = h.lng("view_at"),
            )
        }
    }

    suspend fun history(page: Int): Result<List<VideoItem>> = api {
        if (!session().loggedIn) error("云端历史需要登录")
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/v2/history",
            mapOf("pn" to page.toString(), "ps" to "30"),
            sign = true,
            referer = "https://www.bilibili.com/account/history",
        ).data ?: error("历史加载失败")
        val arr = when (data) {
            else -> data.arr("list") ?: if (data.opt("list") == null && data.has("length")) null else null
        }
        val raw = arr?.objects() ?: emptyList()
        raw.mapNotNull { h ->
            val bvid = h.str("bvid").ifBlank { h.obj("history")?.str("bvid").orEmpty() }
            if (bvid.isBlank()) return@mapNotNull null
            val cover = h.str("pic").ifBlank { h.str("cover") }
            VideoItem(
                bvid = bvid,
                aid = h.lng("aid").takeIf { it > 0 } ?: h.obj("history")?.lng("oid") ?: 0,
                cid = h.cidOrHistory(),
                title = h.str("title").stripHtml(),
                cover = app.ailiaili.core.model.normalizeUrl(cover),
                author = h.str("author_name").ifBlank { h.obj("owner")?.str("name").orEmpty() },
                ownerMid = h.lng("author_mid").takeIf { it > 0 } ?: h.obj("owner")?.lng("mid") ?: 0,
                durationText = app.ailiaili.core.model.formatSeconds(h.int("duration")),
                durationSec = h.int("duration"),
                viewCount = h.lng("view"),
                pubdate = h.lng("view_at"),
            )
        }
    }

    suspend fun deleteHistory(id: Long, live: Boolean = false): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v2/history/delete",
        mapOf("kid" to "${if (live) "live" else "archive"}_$id"),
        "https://www.bilibili.com/account/history",
    )

    suspend fun clearHistory(): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v2/history/clear",
        emptyMap(),
        "https://www.bilibili.com/account/history",
    )

    suspend fun favFolders(): Result<List<FavFolder>> = api {
        if (!session().loggedIn) error("收藏需要登录")
        val mid = session().dedeUserId ?: error("缺少用户 id")
        val data = get(
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
            mapOf("up_mid" to mid),
            referer = "https://space.bilibili.com/$mid/favlist",
        ).data ?: error("收藏夹加载失败")
        data.arr("list")?.objects().orEmpty().map {
            FavFolder(it.lng("id"), it.str("title"), it.int("media_count"))
        }
    }

    /** 「我的」页的收藏夹卡片。created/list 才带封面，私密藏在 attr 的第二位。 */
    suspend fun favFolderCards(): Result<List<FavFolder>> = api {
        if (!session().loggedIn) error("收藏需要登录")
        val mid = session().dedeUserId ?: error("缺少用户 id")
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/v3/fav/folder/created/list",
            mapOf("up_mid" to mid, "pn" to "1", "ps" to "50"),
            sign = true,
            referer = "https://space.bilibili.com/$mid/favlist",
        )
        if (resp.code != 0) error(resp.message ?: "收藏夹加载失败")
        val data = resp.data ?: error("收藏夹加载失败")
        data.arr("list")?.objects().orEmpty().map { folder ->
            FavFolder(
                id = folder.lng("id"),
                title = folder.str("title"),
                count = folder.int("media_count"),
                cover = app.ailiaili.core.model.normalizeUrl(folder.str("cover")),
                privateFolder = (folder.lng("attr") and 2L) != 0L,
            )
        }
    }

    suspend fun favVideos(mediaId: Long, page: Int): Result<List<VideoItem>> = api {
        val mid = session().dedeUserId.orEmpty()
        val data = get(
            "https://api.bilibili.com/x/v3/fav/resource/list",
            mapOf("media_id" to mediaId.toString(), "pn" to page.toString(), "ps" to "20"),
            referer = "https://space.bilibili.com/$mid/favlist?fid=$mediaId",
        ).data ?: error("收藏加载失败")
        data.arr("medias")?.objects().orEmpty().mapNotNull { m ->
            val bvid = m.str("bvid")
            if (bvid.isBlank()) return@mapNotNull null
            VideoItem(
                bvid = bvid,
                aid = m.lng("id"),
                cid = m.cidOrHistory(),
                title = m.str("title").stripHtml(),
                cover = app.ailiaili.core.model.normalizeUrl(m.str("cover")),
                author = m.obj("upper")?.str("name").orEmpty(),
                ownerMid = m.obj("upper")?.lng("mid") ?: 0,
                durationText = app.ailiaili.core.model.formatSeconds(m.int("duration")),
                durationSec = m.int("duration"),
                viewCount = m.obj("cnt_info")?.lng("play") ?: 0,
            )
        }
    }

    suspend fun videoFavFolders(aid: Long): Result<List<FavFolder>> = api {
        if (aid <= 0) error("缺少稿件号")
        if (!session().loggedIn) error("收藏需要登录")
        val mid = session().dedeUserId ?: error("无法获取用户 id")
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
            mapOf(
                "up_mid" to mid,
                "type" to "2",
                "rid" to aid.toString(),
            ),
            sign = true,
            referer = "https://www.bilibili.com/video/",
        )
        if (resp.code != 0) error(resp.message ?: "收藏夹加载失败")
        val data = resp.data ?: error("收藏夹加载失败")
        data.arr("list")?.objects().orEmpty().map {
            FavFolder(it.lng("id"), it.str("title"), it.int("media_count"), it.int("fav_state") == 1)
        }
    }

    suspend fun dealFavorite(aid: Long, add: Collection<Long>, remove: Collection<Long>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val addIds = add.filter { it > 0 }.distinct()
                val delIds = remove.filter { it > 0 }.distinct()
                if (aid <= 0) error("缺少稿件号")
                if (addIds.isEmpty() && delIds.isEmpty()) error("未指定收藏夹")
                val form = mutableMapOf("rid" to aid.toString(), "type" to "2", "platform" to "web")
                if (addIds.isNotEmpty()) form["add_media_ids"] = addIds.joinToString(",")
                if (delIds.isNotEmpty()) form["del_media_ids"] = delIds.joinToString(",")
                val (code, msg) = postRaw(
                    "https://api.bilibili.com/x/v3/fav/resource/deal",
                    form,
                    "https://space.bilibili.com/${session().dedeUserId}/favlist",
                )
                if (code !in setOf(0, 11201, 11202)) error(msg ?: "收藏失败 ($code)")
            }
        }

    suspend fun hasLiked(aid: Long): Result<Boolean> = api {
        if (aid <= 0) error("缺少稿件号")
        val resp = get(
            "https://api.bilibili.com/x/web-interface/archive/has/like",
            mapOf("aid" to aid.toString()),
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "查询点赞状态失败"))
        resp.scalar == 1L
    }

    suspend fun inWatchLater(aid: Long): Result<Boolean> = api {
        if (aid <= 0) error("参数不完整")
        if (!session().loggedIn) error("未登录")
        val now = epochMs()
        val cached = laterAids?.takeIf { now - laterAt < 60_000 }
        val set = cached ?: watchLater().getOrElse { error(it.message ?: "查询稍后再看失败") }
            .map { it.aid }
            .filter { it > 0 }
            .toSet()
            .also {
                laterAids = it
                laterAt = now
            }
        aid in set
    }

    suspend fun watchLater(): Result<List<VideoItem>> = api {
        if (!session().loggedIn) error("稍后再看需要登录")
        val data = get(
            "https://api.bilibili.com/x/v2/history/toview",
            emptyMap(),
            referer = "https://www.bilibili.com/watchlater/list",
        ).data ?: error("稍后再看加载失败")
        data.arr("list")?.objects().orEmpty().mapNotNull { parseVideo(it) }
    }

    suspend fun addWatchLater(aid: Long): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v2/history/toview/add",
        mapOf("aid" to aid.toString()),
    ).also { if (it.isSuccess) dropLaterCache() }

    suspend fun removeWatchLater(aid: Long): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v2/history/toview/del",
        mapOf("aid" to aid.toString()),
        "https://www.bilibili.com/watchlater/list",
    ).also { if (it.isSuccess) dropLaterCache() }

    suspend fun clearWatchLater(): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v2/history/toview/clear",
        emptyMap(),
        "https://www.bilibili.com/watchlater/list",
    ).also { if (it.isSuccess) dropLaterCache() }

    suspend fun batchRemoveFav(mediaId: Long, aids: Collection<Long>): Result<Unit> {
        val resources = aids.filter { it > 0 }.joinToString(",") { "$it:2" }
        if (mediaId <= 0 || resources.isBlank()) return Result.failure(IllegalStateException("没有可移出的内容"))
        return postOk(
            "https://api.bilibili.com/x/v3/fav/resource/batch-del",
            mapOf("media_id" to mediaId.toString(), "resources" to resources),
            "https://space.bilibili.com/",
        )
    }

    private fun dropLaterCache() {
        laterAids = null
        laterAt = 0L
    }

    suspend fun messages(): Result<List<MsgSession>> = api {
        if (!session().loggedIn) error("私信需要登录")
        ensureBuvid()
        val referer = "https://message.bilibili.com/"
        val data = get(
            "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions",
            mapOf(
                "session_type" to "1",
                "sort_rule" to "2",
                "size" to "40",
                "build" to "0",
                "mobi_app" to "web",
            ),
            referer = referer,
        ).data ?: error("私信加载失败")
        val sessions = data.arr("session_list")?.objects().orEmpty().mapNotNull { s ->
            val id = s.lng("talker_id")
            if (id <= 0) return@mapNotNull null
            MsgSession(
                talkerId = id,
                name = "",
                face = "",
                preview = previewOf(s.obj("last_msg")),
                unread = s.int("unread_count"),
                timeText = clock(s.lng("at_time")),
            )
        }
        fillNames(sessions, referer)
    }

    suspend fun msgHistory(talkerId: Long): Result<List<MsgItem>> = api {
        if (!session().loggedIn) error("私信需要登录")
        val me = session().dedeUserId?.toLongOrNull() ?: 0L
        val data = get(
            "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs",
            mapOf(
                "sender_device_id" to "1",
                "talker_id" to talkerId.toString(),
                "session_type" to "1",
                "size" to "30",
                "build" to "0",
                "mobi_app" to "web",
            ),
            referer = "https://message.bilibili.com/",
        ).data ?: error("消息加载失败")
        data.arr("messages")?.objects().orEmpty().map { m ->
            val uid = m.lng("sender_uid")
            val type = m.int("msg_type")
            val content = m.str("content")
            MsgItem(
                senderUid = uid,
                mine = uid == me && me > 0,
                text = if (type == 2) "[图片]" else decodeMsg(content),
                imageUrl = if (type == 2) extractImage(content) else "",
                timeText = clock(m.lng("timestamp")),
                seqno = m.lng("msg_seqno"),
            )
        }.sortedBy { it.seqno }
    }

    suspend fun sendMsg(receiverId: Long, text: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val me = session().dedeUserId ?: error("无法获取自己的 UID")
            val ts = (epochMs() / 1000).toString()
            val content = JSONObject().put("content", text).toString()
            val (code, msg) = postRaw(
                "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
                mapOf(
                    "msg[sender_uid]" to me,
                    "msg[receiver_id]" to receiverId.toString(),
                    "msg[receiver_type]" to "1",
                    "msg[msg_type]" to "1",
                    "msg[msg_status]" to "0",
                    "msg[content]" to content,
                    "msg[timestamp]" to ts,
                    "msg[dev_id]" to randomHex(32),
                    "csrf_token" to session().biliJct.orEmpty(),
                    "build" to "0",
                    "mobi_app" to "web",
                ),
                "https://message.bilibili.com/",
            )
            if (code != 0) error(friendly(code, msg ?: "发送失败"))
        }
    }

    suspend fun liveList(page: Int): Result<List<VideoItem>> = api {
        ensureBuvid()
        var last = "直播列表为空"
        for (area in intArrayOf(0, 1, 2)) {
            val resp = get(
                "https://api.live.bilibili.com/room/v1/Area/getRoomList",
                mapOf(
                    "platform" to "web",
                    "parent_area_id" to area.toString(),
                    "cate_id" to "0",
                    "area_id" to "0",
                    "sort_type" to "online",
                    "page" to page.toString(),
                    "page_size" to "20",
                ),
                referer = "https://live.bilibili.com/",
            )
            val items = resp.data?.arr("list")?.objects().orEmpty().mapNotNull { parseLiveRoom(it) }
            if (items.isNotEmpty()) return@api items
            last = resp.message ?: last
        }
        error(last)
    }

    suspend fun livePlay(roomId: Long, qn: Int = 0): Result<LivePlayInfo> = api {
        ensureBuvid()
        val data = get(
            "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo",
            mapOf(
                "room_id" to roomId.toString(),
                "no_playurl" to "0",
                "mask" to "1",
                "qn" to qn.coerceAtLeast(0).toString(),
                "platform" to "web",
                "protocol" to "0,1",
                "format" to "0,1,2",
                "codec" to "0,1",
            ),
            referer = "https://live.bilibili.com/$roomId",
        ).data ?: error("直播地址获取失败")
        if (data.int("live_status") == 0) error("主播还没有开播")
        val url = pickLiveUrl(data) ?: error("没有可用的直播流")
        val playurl = data.obj("playurl_info")?.obj("playurl")
        val labels = playurl?.arr("g_qn_desc")?.objects().orEmpty()
            .associate { it.int("qn") to it.str("desc") }
        val accepted = linkedSetOf<Int>()
        playurl?.arr("stream")?.objects().orEmpty().forEach { stream ->
            stream.arr("format")?.objects().orEmpty().forEach { format ->
                format.arr("codec")?.objects().orEmpty().forEach { codec ->
                    val arr = codec.arr("accept_qn") ?: return@forEach
                    for (i in 0 until arr.length()) arr.optInt(i).takeIf { it > 0 }?.let(accepted::add)
                }
            }
        }
        val current = playurl?.arr("stream")?.objects().orEmpty()
            .firstNotNullOfOrNull { stream ->
                stream.arr("format")?.objects().orEmpty()
                    .firstNotNullOfOrNull { format ->
                        format.arr("codec")?.objects().orEmpty()
                            .firstNotNullOfOrNull { codec -> codec.int("current_qn").takeIf { it > 0 } }
                    }
            } ?: qn
        val qualities = accepted.ifEmpty { labels.keys }
            .map { LiveQuality(it, labels[it] ?: "Q$it") }
            .sortedByDescending { it.qn }
        LivePlayInfo(
            url = url,
            currentQn = current,
            qualities = qualities,
            liveStartSec = data.lng("live_time"),
            ownerMid = data.lng("uid"),
        )
    }

    suspend fun liveDanmakuInfo(roomId: Long): Result<LiveDanmakuInfo> = api {
        ensureBuvid()
        val resp = get(
            "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo",
            mapOf("id" to roomId.toString(), "type" to "0"),
            sign = true,
            referer = "https://live.bilibili.com/$roomId",
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "弹幕服务器信息获取失败"))
        val data = resp.data ?: error("弹幕服务器信息获取失败")
        val host = data.arr("host_list")?.optJSONObject(0) ?: error("弹幕服务器列表为空")
        val name = host.str("host")
        if (name.isBlank()) error("弹幕服务器地址为空")
        LiveDanmakuInfo(
            host = name,
            port = host.int("wss_port").takeIf { it > 0 } ?: 443,
            token = data.str("token"),
        )
    }

    fun liveDanmaku(roomId: Long): Flow<LiveDanmaku> = flow {
        val info = liveDanmakuInfo(roomId).getOrThrow()
        val uid = session().dedeUserId?.toLongOrNull() ?: 0L
        emitAll(
            LiveDanmakuConnection(
                host = info.host,
                port = info.port,
                token = info.token,
                roomId = roomId,
                uid = uid,
                cookie = session().cookieHeader(),
            ).messages(),
        )
    }

    suspend fun liveRoomInfo(roomId: Long): Result<LiveRoomInfo> = api {
        ensureBuvid()
        val data = get(
            "https://api.live.bilibili.com/room/v1/Room/get_info",
            mapOf("room_id" to roomId.toString()),
            referer = "https://live.bilibili.com/$roomId",
        ).data ?: error("直播间信息获取失败")
        if (data.int("live_status") == 0) error("主播还没有开播")
        val anchor = runCatching {
            get(
                "https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room",
                mapOf("roomid" to roomId.toString()),
                referer = "https://live.bilibili.com/$roomId",
            ).data?.obj("info")
        }.getOrNull()
        LiveRoomInfo(
            title = data.str("title"),
            ownerName = anchor?.str("uname").orEmpty(),
            ownerFace = app.ailiaili.core.model.normalizeUrl(anchor?.str("face").orEmpty()),
            ownerMid = anchor?.lng("uid") ?: data.lng("uid"),
            online = data.lng("online"),
            attention = data.lng("attention"),
            area = listOf(data.str("parent_area_name"), data.str("area_name"))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
    }

    suspend fun like(bvid: String, liked: Boolean): Result<Unit> = postOk(
        "https://api.bilibili.com/x/web-interface/archive/like",
        mapOf("bvid" to bvid, "like" to if (liked) "1" else "2"),
        "https://www.bilibili.com/video/$bvid",
    )

    suspend fun coin(aid: Long, bvid: String, count: Int = 1): Result<Unit> = postOk(
        "https://api.bilibili.com/x/web-interface/coin/add",
        mapOf("aid" to aid.toString(), "multiply" to count.coerceIn(1, 2).toString(), "select_like" to "0"),
        "https://www.bilibili.com/video/$bvid",
    )

    suspend fun relation(mid: Long): Result<Boolean> = api {
        val data = get(
            "https://api.bilibili.com/x/relation",
            mapOf("fid" to mid.toString()),
            referer = "https://space.bilibili.com/$mid",
        ).data ?: return@api false
        data.int("attribute") > 0
    }

    suspend fun follow(mid: Long, follow: Boolean): Result<Unit> = postOk(
        "https://api.bilibili.com/x/relation/modify",
        mapOf("fid" to mid.toString(), "act" to if (follow) "1" else "2", "re_src" to "11"),
        "https://space.bilibili.com/$mid",
    )

    suspend fun reportHistory(aid: Long, cid: Long, progressSec: Long): Result<Unit> {
        if (!session().loggedIn) return Result.success(Unit)
        return postOk(
            "https://api.bilibili.com/x/v2/history/report",
            mapOf(
                "aid" to aid.toString(),
                "cid" to cid.toString(),
                "progress" to progressSec.coerceAtLeast(0).toString(),
                "type" to "3",
            ),
        )
    }

    suspend fun sponsor(bvid: String): Result<List<SponsorSegment>> = withContext(ioDispatcher) {
        runCatching {
            val resp = SharedHttp.probe.get(
                "https://www.bsbsb.top/api/skipSegments?videoID=${urlEncode(bvid)}",
                mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to app.ailiaili.core.model.USER_AGENT,
                ),
            )
            run {
                if (!resp.isSuccessful) return@run emptyList()
                val arr = JSONArray(resp.bodyText)
                buildList {
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        val seg = e.optJSONArray("segment") ?: continue
                        if (seg.length() < 2) continue
                        add(
                            SponsorSegment(
                                cid = e.str("cid").toLongOrNull() ?: 0,
                                category = e.str("category"),
                                startSec = seg.optDouble(0),
                                endSec = seg.optDouble(1),
                                actionType = e.str("actionType").ifBlank { "skip" },
                                votes = e.int("votes"),
                            ),
                        )
                    }
                }
            }
        }.recover { emptyList() }
    }

    suspend fun qrGenerate(): Result<QrTicket> = api {
        ensureBuvid()
        val data = get(
            "https://passport.bilibili.com/x/passport-login/web/qrcode/generate",
            emptyMap(),
            referer = "https://passport.bilibili.com/",
        ).data ?: error("二维码获取失败")
        QrTicket(data.str("url"), data.str("qrcode_key"))
    }

    suspend fun qrPoll(key: String): Result<QrStatus> = withContext(ioDispatcher) {
        runCatching {
            ensureBuvid()
            val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key"
            val resp = http.get(url, defaultHeaders("https://passport.bilibili.com/", session().cookieHeader()))
            val root = JSONObject(resp.bodyText)
            val data = root.optJSONObject("data")
            val code = data?.optInt("code", -1) ?: -1
            when (code) {
                0 -> {
                    absorbCookies(resp.headers("Set-Cookie"))
                    QrStatus.Success
                }
                86090 -> QrStatus.Scanned
                86038 -> QrStatus.Expired
                86101 -> QrStatus.Waiting
                else -> QrStatus.Waiting
            }
        }
    }

    suspend fun logout() {
        updateSession(session().copy(sessData = null, biliJct = null, dedeUserId = null, dedeUserIdCkMd5 = null))
    }

    private fun absorbCookies(headers: List<String>) {
        var next = session()
        for (raw in headers) {
            val pair = raw.substringBefore(';').trim()
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            next = when (name) {
                "SESSDATA" -> next.copy(sessData = value)
                "bili_jct" -> next.copy(biliJct = value)
                "DedeUserID" -> next.copy(dedeUserId = value)
                "DedeUserID__ckMd5" -> next.copy(dedeUserIdCkMd5 = value)
                else -> next
            }
        }
        if (next != session()) updateSession(next)
    }

    private suspend fun listOf(url: String, params: Map<String, String>, arrayKey: String): Result<List<VideoItem>> = api {
        ensureBuvid()
        val data = get(url, params).data ?: error("列表为空")
        data.arr(arrayKey)?.objects().orEmpty().mapNotNull { parseVideo(it) }
    }

    private suspend fun <T> api(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        runCatching { block() }.recoverCatching { e ->
            if (e is ApiException) throw e
            throw ApiException(e.message ?: "请求失败")
        }
    }

    private class ApiException(message: String) : RuntimeException(message)

    private data class Resp(
        val code: Int,
        val message: String?,
        val data: JSONObject?,
        val scalar: Long? = null,
    )

    private suspend fun get(
        url: String,
        params: Map<String, String>,
        sign: Boolean = false,
        referer: String = REFERER,
        retry: Boolean = true,
        bodyKey: String = "data",
    ): Resp {
        val ps = params.toMutableMap()
        if (sign) {
            val mixin = mixin() ?: return Resp(-400, "WBI 密钥获取失败", null)
            signParams(ps, mixin)
        }
        val full = if (ps.isEmpty()) url else url + "?" + ps.entries.joinToString("&") { "${it.key}=${wbiEnc(it.value)}" }
        val text = text(full, referer) ?: return Resp(-1, "网络请求失败", null)
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return Resp(-412, "请求被拦截，请稍后重试", null)
        }
        val root = runCatching { JSONObject(trimmed) }.getOrElse { return Resp(-2, "响应解析失败", null) }
        val code = root.optInt("code", -999)
        val msg = root.optString("message").ifBlank { null }
        if (sign && retry && code == -403) {
            wbiImg = null
            wbiSub = null
            return get(url, params, sign = true, referer = referer, retry = false, bodyKey = bodyKey)
        }
        val data = root.opt(bodyKey)
        val obj = when (data) {
            is JSONObject -> data
            is JSONArray -> JSONObject().put("list", data)
            else -> null
        }
        val scalar = when (data) {
            is Number -> data.toLong()
            is String -> data.toLongOrNull()
            else -> null
        }
        return Resp(code, msg, obj, scalar)
    }

    private fun text(url: String, referer: String): String? = runCatching {
        http.get(url, defaultHeaders(referer, session().cookieHeader())).bodyText
    }.getOrNull()

    /** 304 和 Bili-Status-Code -304 都是“这一段没有弹幕”，返回空数组。网络失败返回 null。 */
    private fun danmakuBytes(url: String): ByteArray? = runCatching {
        val resp = http.get(url, defaultHeaders(REFERER, session().cookieHeader()))
        if (resp.code == 304) return@runCatching ByteArray(0)
        if (resp.header("Bili-Status-Code")?.contains("-304") == true) return@runCatching ByteArray(0)
        if (!resp.isSuccessful) return@runCatching null
        resp.body
    }.getOrNull()

    private fun bytes(url: String): ByteArray? = runCatching {
        val resp = http.get(url, defaultHeaders(REFERER, session().cookieHeader()))
        if (resp.isSuccessful) resp.body else null
    }.getOrNull()

    private suspend fun mixin(): String? {
        val img = wbiImg
        val sub = wbiSub
        if (img != null && sub != null) return mixinKey(img, sub)
        val data = get("https://api.bilibili.com/x/web-interface/nav", emptyMap()).data ?: return null
        val wi = data.obj("wbi_img") ?: return null
        val i = fileNameOnly(wi.str("img_url"))
        val s = fileNameOnly(wi.str("sub_url"))
        if (i.isBlank() || s.isBlank()) return null
        wbiImg = i
        wbiSub = s
        return mixinKey(i, s)
    }

    private suspend fun postOk(url: String, form: Map<String, String>, referer: String = REFERER): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val (code, msg) = postRaw(url, form, referer)
                if (code != 0) error(friendly(code, msg ?: "操作失败"))
            }
        }

    private fun postRaw(url: String, form: Map<String, String>, referer: String): Pair<Int, String?> {
        val csrf = session().biliJct
        if (csrf.isNullOrBlank()) return -101 to "请先登录"
        val body = form.toMutableMap()
        if (!body.containsKey("csrf")) body["csrf"] = csrf
        val text = http.post(url, body, defaultHeaders(referer, session().cookieHeader())).bodyText
        val root = runCatching { JSONObject(text) }.getOrElse { return -2 to "响应解析失败" }
        val code = root.optInt("code", -999)
        val msg = root.optString("message").ifBlank { null }
        return code to msg
    }

    private fun commentFriendly(code: Int, msg: String?): String = when (code) {
        -101 -> "请先登录后再操作"
        -111 -> "登录状态已失效，请重新登录"
        -400 -> "内容不合规，或评论已被删除"
        -403 -> "当前账号没有评论权限"
        -404 -> "评论不存在或已被删除"
        -412, -509 -> "操作太频繁，请稍后再试"
        12002 -> "该视频的评论区已关闭"
        12004 -> "该评论禁止赞或踩"
        12006 -> "评论不存在或已被删除"
        12011 -> "服务端不认这个操作"
        12066 -> "评论内容不能为空"
        else -> msg?.takeIf { it.isNotBlank() } ?: friendly(code, "操作失败")
    }

    private fun friendly(code: Int, fallback: String): String = when (code) {
        -101 -> "请先登录"
        -111 -> "登录状态已失效，请重新登录"
        -403, -401 -> "B 站风控拦截：请在手机 App 或网页版用同一账号完成一次验证后重试"
        -352 -> "请求被 B 站风控拦截，请稍后重试或登录后再试"
        -412, -509 -> "操作太频繁，请稍后再试"
        else -> fallback
    }

    private fun parseDash(
        dash: JSONObject,
        durationMs: Long,
        qn: Int,
        settings: AppSettings,
    ): PlayInfo? {
        val videos = dash.arr("video")?.objects().orEmpty()
        if (videos.isEmpty()) return null
        val qualities = videos.map { it.int("id") }.filter { it > 0 }.distinct().sortedDescending()
            .map { app.ailiaili.core.model.Quality(it, app.ailiaili.core.model.qnLabel(it)) }
        var best: JSONObject? = null
        var bestId = 0
        var bestRank = -1
        fun consider(v: JSONObject, allowAbove: Boolean) {
            val id = v.int("id")
            if (id <= 0) return
            if (!allowAbove && id > qn) return
            val rank = codecRank(v.str("codecs"))
            if (best == null || id > bestId || (id == bestId && rank > bestRank)) {
                best = v
                bestId = id
                bestRank = rank
            }
        }
        videos.forEach { consider(it, false) }
        if (best == null) videos.forEach { consider(it, true) }
        val chosen = best ?: return null
        val videoCands = urlsOf(chosen)
        if (videoCands.isEmpty()) return null
        val videoUrl = CdnPicker.pickVideo(videoCands, settings)
        val audios = dash.arr("audio")?.objects().orEmpty()
        val audioQualities = audios.mapNotNull { track ->
            val urls = urlsOf(track)
            if (urls.isEmpty()) return@mapNotNull null
            val id = track.int("id")
            AudioQuality(
                id = id,
                label = audioQnLabel(id),
                url = CdnPicker.pickAudio(urls, videoUrl, settings),
                bandwidth = track.lng("bandwidth"),
            )
        }.distinctBy { it.id }.sortedBy { it.id }
        // 默认优先 AAC（兼容性最好），同档取最高码率；没有 AAC 才用杜比 / Hi-Res。
        val aac = audioQualities.filter { it.id in 30216..30280 }
        val selectedAudio = (aac.ifEmpty { audioQualities }).maxByOrNull { it.bandwidth }
        return PlayInfo(
            videoUrl = videoUrl,
            audioUrl = selectedAudio?.url,
            backupUrls = videoCands.filter { it != videoUrl },
            durationMs = durationMs,
            qualities = qualities,
            actualQn = bestId,
            width = chosen.int("width"),
            height = chosen.int("height"),
            audioQn = selectedAudio?.id ?: 0,
            audioQualities = audioQualities,
        )
    }

    private suspend fun parseDurl(bvid: String, cid: Long, qn: Int): PlayInfo? {
        val data = get(
            "https://api.bilibili.com/x/player/wbi/playurl",
            mutableMapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnval" to "1",
                "fnver" to "0",
                "fourk" to "1",
            ),
            sign = true,
        ).data ?: return null
        return decodeDurl(data)
    }

    private fun urlsOf(stream: JSONObject): List<String> = buildList {
        stream.str("baseUrl").takeIf { it.isNotBlank() }?.let { add(it) }
        stream.str("base_url").takeIf { it.isNotBlank() }?.let { add(it) }
        stream.arr("backupUrl")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
        }
        stream.arr("backup_url")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }.distinct()

    private fun codecRank(codec: String): Int {
        val c = codec.lowercase()
        return when {
            c.startsWith("hev") || c.startsWith("hvc") -> 3
            c.startsWith("avc") -> 2
            c.startsWith("av01") -> 1
            else -> 0
        }
    }

    private fun pickLiveUrl(data: JSONObject): String? {
        val streams = data.obj("playurl_info")?.obj("playurl")?.arr("stream")?.objects().orEmpty()
        for (stream in streams) {
            for (format in stream.arr("format")?.objects().orEmpty()) {
                for (codec in format.arr("codec")?.objects().orEmpty()) {
                    val base = codec.str("base_url")
                    val info = codec.arr("url_info")?.optJSONObject(0)
                    val host = info?.str("host").orEmpty()
                    val extra = info?.str("extra").orEmpty()
                    if (base.isNotBlank() && host.isNotBlank()) return host + base + extra
                }
            }
        }
        return null
    }

    private fun fillNames(sessions: List<MsgSession>, referer: String): List<MsgSession> {
        if (sessions.isEmpty()) return sessions
        val ids = sessions.map { it.talkerId }.distinct().joinToString(",")
        val data = runCatching {
            val url = "https://api.vc.bilibili.com/account/v1/user/cards?uids=$ids"
            val text = text(url, referer) ?: return sessions
            JSONObject(text).optJSONArray("data")
        }.getOrNull() ?: return sessions
        val names = mutableMapOf<Long, Pair<String, String>>()
        data.objects().forEach { c ->
            names[c.lng("mid")] = c.str("name") to app.ailiaili.core.model.normalizeUrl(c.str("face"))
        }
        return sessions.map { s ->
            val card = names[s.talkerId]
            if (card == null) s.copy(name = "UID ${s.talkerId}") else s.copy(name = card.first, face = card.second)
        }
    }
}

private fun error(message: String): Nothing = throw IllegalStateException(message)

private fun clock(epochSec: Long): String = app.ailiaili.core.model.formatMonthDayHour(epochSec)

private fun decodeMsg(content: String): String {
    if (content.isBlank()) return ""
    return runCatching { JSONObject(content).optString("content") }.getOrDefault(content).ifBlank { content }
}

private fun extractImage(content: String): String =
    runCatching { app.ailiaili.core.model.normalizeUrl(JSONObject(content).optString("url")) }.getOrDefault("")

private fun previewOf(last: JSONObject?): String {
    if (last == null) return ""
    val content = last.str("content")
    return decodeMsg(content).ifBlank { "[消息]" }
}
