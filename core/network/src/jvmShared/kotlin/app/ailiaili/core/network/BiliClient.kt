package app.ailiaili.core.network

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.AudioQuality
import app.ailiaili.core.model.BangumiDetail
import app.ailiaili.core.model.BangumiItem
import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.CommentPage
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.DynamicPage
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.FollowUser
import app.ailiaili.core.model.HistoryCursor
import app.ailiaili.core.model.HistoryPage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

object CdnStatus {
    val report = MutableStateFlow("还没有测速。播放视频时会自动测一次。")
}

/** WBI 密钥按天轮换；20 小时主动刷新，长驻进程不等 -403 才发现。 */
private const val WBI_KEY_TTL_MS = 20L * 60 * 60 * 1000

class BiliClient(
    private val session: () -> Session,
    private val updateSession: (Session) -> Unit,
) {
    private val http = SharedHttp.api

    @Volatile private var wbiImg: String? = null
    @Volatile private var wbiSub: String? = null
    @Volatile private var wbiAt: Long = 0L
    private val wbiFlight = SingleFlight<String, String?>()
    private val buvidFlight = SingleFlight<String, Unit>()
    /**
     * 会话读-改-写必须串行：扫码吸收 Cookie、风控轮换 buvid、退出登录可能并发，
     * 各自基于旧快照 copy 再写回会互相覆盖（最坏丢 SESSDATA，登录静默失效）。
     * 锁内只做内存合并与落盘，网络请求一律在锁外。
     */
    private val sessionLock = kotlinx.coroutines.sync.Mutex()
    /** 同 URL 的并发 GET 合并成一次真实请求（快速切页/双栏同时打开不再重复打接口）。 */
    private val getFlight = SingleFlight<String, String?>()
    /** 低频变化的只读接口短缓存；播放地址/动态/评论等实时接口绝不进这里。 */
    private val readCache = ShortTtlCache<String, String>()
    private val followedSeasons = ShortTtlCache<String, ItemPage<BangumiItem>>()
    private val followRevision = java.util.concurrent.atomic.AtomicLong()
    /** 私信设备号：同一进程保持稳定，与免登录指纹同一风控原则。 */
    private val msgDevId = randomHex(32)
    private var laterAids: Set<Long>? = null
    private var laterAt: Long = 0L
    private var laterOwner: String? = null

    /**
     * 免登录 1080P 的“设备指纹”参数必须在一台设备/一个进程内保持稳定。
     *
     * 以前每次 playUrl 都重新随机；同一个 buvid 却不断更换硬件特征，长驻进程
     * 会被服务端判成爬虫并稳定返回 -412。这里只生成一次，进程内复用。
     */
    private val dmImgStr = randomBase64(16, 64)
    private val dmCoverImgStr = randomBase64(32, 128)

    suspend fun ensureBuvid() {
        val cur = session()
        // 绝大多数调用都已就绪：不进 IO 调度、不进锁，零开销返回。
        if (!cur.buvid3.isNullOrBlank() && !cur.buvid4.isNullOrBlank() && !cur.bNut.isNullOrBlank()) return
        // 首次启动多个页面并发进来时，finger/spi 只打一次。
        buvidFlight.run("buvid") {
            withContext(ioDispatcher) {
                var b3: String? = null
                var b4: String? = null
                if (cur.buvid3.isNullOrBlank() || cur.buvid4.isNullOrBlank()) {
                    val data = get("https://api.bilibili.com/x/frontend/finger/spi", emptyMap()).data
                    if (data != null) {
                        b3 = data.str("b_3").takeIf { it.isNotBlank() }
                        b4 = data.str("b_4").takeIf { it.isNotBlank() }
                    }
                }
                // 网络请求在锁外；合并基于锁内最新快照，只补缺不覆盖别的字段。
                sessionLock.withLock {
                    val now = session()
                    var next = now
                    if (next.buvid3.isNullOrBlank()) b3?.let { next = next.copy(buvid3 = it) }
                    if (next.buvid4.isNullOrBlank()) b4?.let { next = next.copy(buvid4 = it) }
                    if (next.bNut.isNullOrBlank()) {
                        next = next.copy(bNut = (epochMs() / 1000).toString())
                    }
                    if (next != now) updateSession(next)
                }
            }
        }
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

    // 排行榜按小时级更新：10 分钟内切页/返回不再重复请求。
    suspend fun ranking(): Result<List<VideoItem>> = listOf(
        "https://api.bilibili.com/x/web-interface/ranking/v2",
        mapOf("rid" to "0", "type" to "all"),
        "list",
        cacheTtlMs = 10 * 60_000,
    )

    suspend fun regionRank(rid: Int): Result<List<VideoItem>> = listOf(
        "https://api.bilibili.com/x/web-interface/ranking/v2",
        mapOf("rid" to rid.toString(), "type" to "all"),
        "list",
        cacheTtlMs = 10 * 60_000,
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
    }.recoverCatching { popular(brush).getOrThrow() }

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
        order: String = "",
    ): Result<SearchPage> = api {
        ensureBuvid()
        var extraFt = if (kind == ContentKind.Bangumi) mediaFt(keyword, page) else emptyList()
        val params = mutableMapOf(
            "search_type" to kind.searchType(),
            "keyword" to keyword,
            "page" to page.toString(),
        )
        // 排序只对视频搜索有意义：totalrank 综合 / click 播放 / pubdate 最新 / dm 弹幕。
        if (kind == ContentKind.Video && order.isNotBlank() && order != "totalrank") {
            params["order"] = order
        }

        // 一次搜索尝试。风控统一折成 blocked=true，由外层决定是否走降级链。
        suspend fun attempt(signed: Boolean, referer: String): SearchPage {
            val url = if (signed) {
                "https://api.bilibili.com/x/web-interface/wbi/search/type"
            } else {
                "https://api.bilibili.com/x/web-interface/search/type"
            }
            val resp = get(url, params, sign = signed, referer = referer, transportRetry = false)
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
        sessionLock.withLock {
            val cur = session()
            updateSession(
                cur.copy(
                    buvid3 = b3.ifBlank { cur.buvid3.orEmpty() }.takeIf { it.isNotBlank() },
                    buvid4 = b4.ifBlank { cur.buvid4.orEmpty() }.takeIf { it.isNotBlank() },
                ),
            )
        }
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

    suspend fun followedBangumi(page: Int, type: Int, fresh: Boolean = false): Result<ItemPage<BangumiItem>> = api {
        val uid = session().dedeUserId?.takeIf { session().loggedIn } ?: error("请先登录")
        val cacheKey = "$uid:$type:$page:${followRevision.get()}"
        if (!fresh) followedSeasons.get(cacheKey)?.let { return@api it }
        val response = get(
            "https://api.bilibili.com/x/space/bangumi/follow/list",
            mapOf("vmid" to uid, "pn" to page.toString(), "ps" to "30", "type" to type.toString()),
        )
        if (response.code != 0) error(friendly(response.code, response.message ?: "追番列表加载失败"))
        val data = response.data ?: error("追番列表加载失败")
        val rows = data.arr("list")?.objects().orEmpty()
        val total = data.lng("total")
        val result = ItemPage(
            items = rows.mapNotNull(::parseBangumiItem),
            hasMore = rows.isNotEmpty() && if (total > 0) page * 30L < total else rows.size >= 30,
            total = total,
        )
        if (session().dedeUserId != uid) error("账号已切换")
        followedSeasons.put(cacheKey, result, 3 * 60_000L)
        result
    }

    suspend fun bangumiFollowed(seasonId: Long, type: Int): Result<Boolean> = api {
        val uid = session().dedeUserId
        if (!session().loggedIn) return@api false
        var page = 1
        val seen = mutableSetOf<Long>()
        while (session().dedeUserId == uid) {
            val result = followedBangumi(page, type).getOrThrow()
            if (result.items.any { it.seasonId == seasonId }) return@api true
            if (!result.hasMore) return@api false
            val seenCount = seen.size
            result.items.forEach { seen.add(it.seasonId) }
            if (seen.size == seenCount) error("追番状态暂时无法确认")
            page++
        }
        error("账号已切换")
    }

    suspend fun setBangumiFollow(seasonId: Long, followed: Boolean): Result<Unit> = postOk(
        if (followed) "https://api.bilibili.com/pgc/web/follow/add" else "https://api.bilibili.com/pgc/web/follow/del",
        mapOf("season_id" to seasonId.toString()),
        referer = "https://www.bilibili.com/bangumi/play/ss$seasonId",
    ).onSuccess { followRevision.incrementAndGet() }

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
            transportRetry = false,
        )
        if (resp.code != 0) emptyList()
        else resp.data?.arr("result")?.objects().orEmpty().mapNotNull(::parseBangumiItem)
    }.getOrDefault(emptyList())

    suspend fun hotSearch(): Result<List<String>> = api {
        val data = get(
            "https://api.bilibili.com/x/web-interface/search/square",
            mapOf("limit" to "10"),
            cacheTtlMs = 5 * 60_000,
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
        val single = if (!forceDash && app.ailiaili.core.model.shouldPreferDurl(qn)) {
            parseDurl(bvid, cid, qn)
        } else {
            null
        }
        if (single != null && single.actualQn == qn) return@api single

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
        val resp = try {
            get("https://api.bilibili.com/x/player/wbi/playurl", ps, sign = true, transportRetry = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return@api single ?: throw failure
        }
        if (resp.code != 0) return@api single
            ?: error(friendly(resp.code, resp.message ?: "获取播放地址失败"))
        val data = resp.data
        val dash = data?.obj("dash")
        if (dash != null) {
            val parsed = parseDash(dash, data.lng("timelength"), qn, settings)
            if (parsed != null && (single == null || parsed.actualQn >= single.actualQn)) return@api parsed
        }
        single ?: parseDurl(bvid, cid, qn) ?: error(resp.message ?: "没有可播放的地址，可能是大会员或付费专属")
    }

    /**
     * 番剧/影视 PGC 取流（pgc/player/web/playurl，ep_id 链路）。
     * 与 UGC 同一套 DASH 解析/CDN 选择/不可播档过滤；PGC 的 durl 常为多段切片，
     * 单段限制会拒绝，因此只走 DASH（fnval=4048），失败交给上层回退 UGC bvid。
     * 大会员/付费专属的错误文案由 friendly 与接口 message 透传。
     */
    suspend fun pgcPlayUrl(
        epId: Long,
        aid: Long,
        cid: Long,
        settings: AppSettings,
        qnOverride: Int? = null,
    ): Result<PlayInfo> = api {
        ensureBuvid()
        if (epId <= 0) error("没有番剧集号")
        val qn = qnOverride?.coerceAtLeast(0)?.takeIf { it > 0 } ?: settings.defaultQn
        val ps = mutableMapOf(
            "ep_id" to epId.toString(),
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            "module" to "bangumi",
        )
        if (aid > 0) ps["avid"] = aid.toString()
        val resp = get(
            "https://api.bilibili.com/pgc/player/web/playurl",
            ps,
            referer = "https://www.bilibili.com/bangumi/play/ep$epId",
            bodyKey = "result",
            transportRetry = false,
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "获取番剧播放地址失败"))
        val data = resp.data ?: error("没有可播放的地址，可能是大会员或付费专属")
        val dash = data.obj("dash")
        if (dash != null) {
            parseDash(dash, data.lng("timelength"), qn, settings)?.let { return@api it }
        }
        // PGC 低清晰度可能只给 durl；单段才可用，多段切片拒绝后由上层回退。
        decodeDurl(data) ?: error(resp.message ?: "没有可播放的地址，可能是大会员或付费专属")
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

    // 章节与字幕轨来自同一个 player/wbi/v2 端点：两边都开缓存后，
    // 播放页并行发起的两次调用只会产生一次真实网络请求（缓存键不含签名时间戳）。
    suspend fun chapters(bvid: String, cid: Long): Result<List<ViewChapter>> = api {
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/player/wbi/v2",
            mutableMapOf("bvid" to bvid, "cid" to cid.toString()),
            sign = true,
            cacheTtlMs = 10 * 60_000,
        ).data ?: return@api emptyList()
        data.arr("view_points")?.objects().orEmpty().mapNotNull { point ->
            if (point.int("type") != 2) return@mapNotNull null
            val title = point.str("content")
            if (title.isBlank()) return@mapNotNull null
            ViewChapter(point.int("from"), point.int("to"), title)
        }
    }

    /** 字幕轨列表（CC + AI）。未登录时 AI 字幕通常不可见，接口给什么用什么。 */
    suspend fun subtitles(bvid: String, cid: Long): Result<List<app.ailiaili.core.model.SubtitleTrack>> = api {
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/player/wbi/v2",
            mutableMapOf("bvid" to bvid, "cid" to cid.toString()),
            sign = true,
            cacheTtlMs = 10 * 60_000,
        ).data ?: return@api emptyList()
        data.obj("subtitle")?.arr("subtitles")?.objects().orEmpty().mapNotNull { s ->
            val url = normalizeUrl(s.str("subtitle_url"))
            if (url.isBlank()) return@mapNotNull null
            app.ailiaili.core.model.SubtitleTrack(
                id = s.lng("id"),
                lan = s.str("lan"),
                label = s.str("lan_doc").ifBlank { s.str("lan") },
                url = url,
                ai = s.str("lan").startsWith("ai-") || s.int("ai_status") > 0,
            )
        }
    }

    private val cueCache = ShortTtlCache<String, List<app.ailiaili.core.model.SubtitleCue>>(maxEntries = 24)
    private val cueFlight = SingleFlight<String, List<app.ailiaili.core.model.SubtitleCue>>()

    /** 拉取并解析 JSON 字幕体。字幕内容不可变，缓存 30 分钟；并发同轨只下载一次。 */
    suspend fun subtitleCues(url: String): Result<List<app.ailiaili.core.model.SubtitleCue>> =
        withContext(ioDispatcher) {
            runCatching {
                cueCache.get(url)?.let { return@runCatching it }
                val cues = cueFlight.run(url) {
                    val body = text(url, REFERER) ?: error("字幕加载失败")
                    val root = JSONObject(body)
                    val parsed = root.arr("body")?.objects().orEmpty().mapNotNull { cue ->
                        val content = cue.str("content")
                        if (content.isBlank()) return@mapNotNull null
                        app.ailiaili.core.model.SubtitleCue(
                            from = cue.dbl("from"),
                            to = cue.dbl("to"),
                            text = content,
                        )
                    }.sortedBy { it.from }
                    // 只缓存成功解析出的非空字幕；失败回退的空列表不落缓存。
                    if (parsed.isNotEmpty()) cueCache.put(url, parsed, 30 * 60_000)
                    parsed
                }
                cues
            }
        }

    suspend fun videoShot(bvid: String, cid: Long): Result<VideoShot?> = api {
        val data = get(
            "https://api.bilibili.com/x/player/videoshot",
            mapOf("bvid" to bvid, "cid" to cid.toString(), "index" to "1"),
            cacheTtlMs = 10 * 60_000,
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
    suspend fun comments(aid: Long, page: Int, mode: Int = 3, type: Int = 1): Result<CommentPage> = api {
        val resp = get(
            "https://api.bilibili.com/x/v2/reply/main",
            mapOf(
                "oid" to aid.toString(),
                "type" to type.toString(),
                "mode" to mode.toString(),
                "pn" to page.toString(),
                "ps" to "20",
            ),
            transportRetry = false,
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
    suspend fun replies(aid: Long, root: Long, page: Int, type: Int = 1): Result<List<Comment>> = api {
        val resp = get(
            "https://api.bilibili.com/x/v2/reply/reply",
            mapOf(
                "oid" to aid.toString(),
                "type" to type.toString(),
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

    suspend fun postComment(aid: Long, message: String, root: Long = 0, parent: Long = 0, type: Int = 1): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val text = message.trim()
                if (aid <= 0) error("没拿到稿件号，暂时不能评论")
                if (text.isEmpty()) error("评论内容不能为空")
                val form = mutableMapOf(
                    "oid" to aid.toString(),
                    "type" to type.toString(),
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

    suspend fun commentLike(aid: Long, rpid: Long, like: Boolean, type: Int = 1): Result<Unit> =
        commentReact("https://api.bilibili.com/x/v2/reply/action", aid, rpid, like, type)

    suspend fun commentDislike(aid: Long, rpid: Long, dislike: Boolean, type: Int = 1): Result<Unit> =
        commentReact("https://api.bilibili.com/x/v2/reply/hate", aid, rpid, dislike, type)

    private suspend fun commentReact(url: String, aid: Long, rpid: Long, on: Boolean, type: Int = 1): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                if (aid <= 0 || rpid <= 0) error("参数不完整")
                val (code, msg) = postRaw(
                    url,
                    mapOf(
                        "oid" to aid.toString(),
                        "type" to type.toString(),
                        "rpid" to rpid.toString(),
                        "action" to if (on) "1" else "0",
                    ),
                    REFERER,
                )
                if (code != 0) error(commentFriendly(code, msg))
            }
        }

    /** 发送弹幕。写操作不重试；表单校验在模型层纯函数里，可单测。 */
    suspend fun sendDanmaku(aid: Long, cid: Long, text: String, progressMs: Long, mode: Int, color: Int): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val form = app.ailiaili.core.model.danmakuSendForm(aid, cid, text, progressMs, mode, color).getOrThrow()
                val (code, msg) = postRaw("https://api.bilibili.com/x/v2/dm/post", form, REFERER)
                if (code != 0) error(danmakuFriendly(code, msg))
            }
        }

    /**
     * 推荐流「不感兴趣」负反馈。需要登录；未登录时 UI 只做本地移除。
     * 接口按 app 协议带 mobi_app/platform/build 参数，网页与 App 推荐源都可用。
     */
    suspend fun feedDislike(goto: String, id: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val form = app.ailiaili.core.model.feedDislikeForm(goto, id).getOrThrow().toMutableMap()
            form["mobi_app"] = "android"
            form["platform"] = "android"
            form["build"] = "74800100"
            val (code, msg) = postRaw("https://api.bilibili.com/x/feed/dislike", form, REFERER)
            if (code != 0) error(friendly(code, msg ?: "反馈失败"))
        }
    }

    /** 直播间发言。写操作不重试；接口同时认 csrf 与 csrf_token，两个都带上。 */
    suspend fun sendLiveMsg(roomId: Long, text: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val form = app.ailiaili.core.model.liveMsgSendForm(roomId, text).getOrThrow().toMutableMap()
            form["csrf_token"] = session().biliJct.orEmpty()
            val (code, msg) = postRaw(
                "https://api.live.bilibili.com/msg/send",
                form,
                "https://live.bilibili.com/$roomId",
            )
            if (code != 0) error(liveMsgFriendly(code, msg))
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
            transportRetry = false,
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

    /** 综合动态流：视频投稿 + 图文动态都保留。 */
    suspend fun dynamicFeed(offset: String?): Result<app.ailiaili.core.model.DynamicFeed> = api {
        ensureBuvid()
        if (!session().loggedIn) error("请先登录后查看动态")
        val ps = mutableMapOf("timezone_offset" to "-480", "type" to "all")
        if (!offset.isNullOrBlank()) ps["offset"] = offset
        val resp = get(
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all",
            ps,
            sign = true,
            referer = "https://t.bilibili.com/",
            transportRetry = false,
        )
        if (resp.code == -101) error("请先登录后查看动态")
        val data = resp.data ?: error(resp.message ?: "动态加载失败")
        val entries = data.arr("items")?.objects().orEmpty().mapNotNull { parseDynamicEntry(it) }
        app.ailiaili.core.model.DynamicFeed(
            entries = entries,
            nextOffset = data.str("offset").ifBlank { null },
            hasMore = data.optBoolean("has_more") || data.int("has_more") == 1,
        )
    }

    /** 单条动态详情（图文详情页用）。 */
    suspend fun dynamicDetail(id: String): Result<app.ailiaili.core.model.DynamicEntry> = api {
        ensureBuvid()
        val resp = get(
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/detail",
            mutableMapOf("id" to id, "timezone_offset" to "-480"),
            sign = true,
            referer = "https://t.bilibili.com/$id",
        )
        if (resp.code != 0) error(friendly(resp.code, resp.message ?: "动态加载失败"))
        val item = resp.data?.obj("item") ?: error("动态不存在或已被删除")
        parseDynamicEntry(item) ?: error("暂不支持这种动态类型")
    }

    /** 图文动态点赞（opus 聚合接口）。失败时上层回退提示。 */
    suspend fun dynamicLike(id: String, like: Boolean): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val (code, msg) = postRaw(
                "https://api.bilibili.com/x/dynamic/feed/dyn/thumb",
                mapOf("dyn_id" to id, "up" to if (like) "1" else "2"),
                "https://t.bilibili.com/",
            )
            if (code != 0) error(friendly(code, msg ?: "点赞失败"))
        }
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

    suspend fun blockedUsers(): Result<List<BlockedUp>> = api {
        val uid = session().dedeUserId?.takeIf { session().loggedIn } ?: error("登录后可导入黑名单")
        val users = linkedMapOf<Long, BlockedUp>()
        var page = 1
        while (users.size < 200) {
            val response = get(
                "https://api.bilibili.com/x/relation/blacks",
                mapOf("pn" to page.toString(), "ps" to "50"),
                sign = true,
            )
            if (response.code != 0) error(friendly(response.code, response.message ?: "黑名单加载失败"))
            val data = response.data ?: error("黑名单加载失败")
            val previousSize = users.size
            val rows = data.arr("list")?.objects().orEmpty()
            for (row in rows) {
                val mid = row.lng("mid")
                if (mid > 0) users[mid] = BlockedUp(mid, row.str("uname").ifBlank { row.str("name") })
            }
            val total = data.lng("total")
            if (users.size == previousSize || rows.size < 50 || total > 0 && users.size >= total) break
            page++
        }
        if (session().dedeUserId != uid) error("账号已切换")
        users.values.take(200)
    }

    suspend fun followings(mid: Long, page: Int): Result<ItemPage<FollowUser>> = relations(mid, page, false)

    suspend fun followers(mid: Long, page: Int): Result<ItemPage<FollowUser>> = relations(mid, page, true)

    private suspend fun relations(mid: Long, page: Int, followers: Boolean): Result<ItemPage<FollowUser>> = api {
        if (!session().loggedIn) error("请先登录")
        ensureBuvid()
        val ps = if (followers) 20 else 30
        val path = if (followers) "followers" else "followings"
        val resp = get(
            "https://api.bilibili.com/x/relation/$path",
            mapOf(
                "vmid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to ps.toString(),
                "order" to "desc",
            ),
            referer = "https://space.bilibili.com/$mid/fans/${if (followers) "fans" else "follow"}",
        )
        if (resp.code == -101) error("请先登录")
        if (resp.code != 0) error(resp.message ?: if (followers) "粉丝列表加载失败" else "关注列表加载失败")
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
        ItemPage(users, if (total > 0) users.isNotEmpty() && page.toLong() * ps < total else users.size >= ps, total.toLong())
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

    suspend fun liveHistory(position: HistoryCursor?): Result<HistoryPage> = api {
        if (!session().loggedIn) error("云端历史需要登录")
        ensureBuvid()
        val ps = mutableMapOf(
            "type" to "live",
            "ps" to "20",
        )
        if (position != null) {
            ps["view_at"] = position.viewAt.toString()
            ps["max"] = position.max.toString()
            if (position.business.isNotBlank()) ps["business"] = position.business
        }
        val data = get(
            "https://api.bilibili.com/x/web-interface/history/cursor",
            ps,
            referer = "https://www.bilibili.com/account/history",
        ).data ?: error("直播历史加载失败")
        val next = data.obj("cursor")?.let { HistoryCursor(it.lng("max"), it.lng("view_at"), it.str("business")) }
            ?.takeIf { it.viewAt > 0 && it != position }
        val rows = data.arr("list")?.objects().orEmpty()
        val items = rows.mapNotNull { h ->
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
        HistoryPage(items, rows.isNotEmpty() && next != null, next)
    }

    suspend fun history(page: Int): Result<HistoryPage> = api {
        if (!session().loggedIn) error("云端历史需要登录")
        ensureBuvid()
        val data = get(
            "https://api.bilibili.com/x/v2/history",
            mapOf("pn" to page.toString(), "ps" to "30"),
            sign = true,
            referer = "https://www.bilibili.com/account/history",
        ).data ?: error("历史加载失败")
        val raw = data.arr("list")?.objects().orEmpty()
        val items = raw.mapNotNull { h ->
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
        HistoryPage(items, raw.size >= 30)
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
            FavFolder(
                id = it.lng("id"),
                title = it.str("title"),
                count = it.int("media_count"),
                privateFolder = (it.lng("attr") and 2L) != 0L,
            )
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

    suspend fun favVideos(mediaId: Long, page: Int, keyword: String = ""): Result<ItemPage<VideoItem>> = api {
        val mid = session().dedeUserId.orEmpty()
        val params = mapOf(
            "media_id" to mediaId.toString(), "pn" to page.toString(), "ps" to "20",
            "keyword" to keyword.trim(), "order" to "mtime", "type" to "0", "tid" to "0",
            "platform" to "web", "web_location" to "333.1387",
        )
        val response = get(
            "https://api.bilibili.com/x/v3/fav/resource/list",
            params,
            sign = true,
            referer = "https://space.bilibili.com/$mid/favlist?fid=$mediaId",
        )
        if (response.code != 0) error(friendly(response.code, response.message ?: "收藏加载失败"))
        val data = response.data ?: error("收藏加载失败")
        val items = data.arr("medias")?.objects().orEmpty().mapNotNull { m ->
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
        ItemPage(items, data.optBoolean("has_more"), data.obj("info")?.lng("media_count") ?: 0)
    }

    suspend fun createFavFolder(title: String, privateFolder: Boolean): Result<FavFolder?> = api {
        val name = title.trim()
        require(name.length in 1..20) { "收藏夹名称需要 1–20 字" }
        val account = session()
        val csrf = account.biliJct?.takeIf { it.isNotBlank() } ?: error("请先登录")
        val form = mapOf(
            "title" to name, "intro" to "", "privacy" to if (privateFolder) "1" else "0",
            "platform" to "web", "web_location" to "333.1387", "csrf" to csrf,
        )
        val response = http.post(
            "https://api.bilibili.com/x/v3/fav/folder/add",
            form,
            defaultHeaders("https://space.bilibili.com/${account.dedeUserId}/favlist", account.cookieHeader()),
        ).bodyText.let(::JSONObject)
        val code = response.optInt("code", -999)
        if (code != 0) error(friendly(code, response.str("message").ifBlank { "新建收藏夹失败" }))
        val data = response.obj("data")
        val id = data?.lng("id") ?: 0
        if (id <= 0) null else FavFolder(id, name, 0, privateFolder = privateFolder)
    }

    suspend fun deleteFavFolder(mediaId: Long): Result<Unit> = postOk(
        "https://api.bilibili.com/x/v3/fav/folder/del",
        mapOf("media_ids" to mediaId.toString(), "platform" to "web"),
        referer = "https://space.bilibili.com/${session().dedeUserId}/favlist",
    )

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
        // 缓存必须核对账号归属：换号后 60 秒内不能拿上个账号的集合判断。
        val uid = session().dedeUserId.orEmpty()
        val cached = laterAids?.takeIf { now - laterAt < 60_000 && laterOwner == uid }
        val set = cached ?: watchLater().getOrElse { error(it.message ?: "查询稍后再看失败") }
            .map { it.aid }
            .filter { it > 0 }
            .toSet()
            .also {
                laterAids = it
                laterAt = now
                laterOwner = uid
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
            transportRetry = false,
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
            transportRetry = false,
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

    /**
     * 会话已读回执：把已读位置推到最新一条。不上报的话服务端 unread_count
     * 永远不清，收件箱红点与真实已读状态长期不一致。失败静默（尽力而为）。
     */
    suspend fun ackMsg(talkerId: Long, ackSeqno: Long): Result<Unit> {
        if (talkerId <= 0 || ackSeqno <= 0) return Result.success(Unit)
        return postOk(
            "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack",
            mapOf(
                "talker_id" to talkerId.toString(),
                "session_type" to "1",
                "ack_seqno" to ackSeqno.toString(),
                "build" to "0",
                "mobi_app" to "web",
            ),
            referer = "https://message.bilibili.com/",
        )
    }

    suspend fun sendMsg(receiverId: Long, text: String): Result<Unit> =
        sendMsgRaw(receiverId, type = 1, content = JSONObject().put("content", text).toString())

    suspend fun commentEmotes(): Result<List<Pair<String, String>>> = api {
        if (!session().loggedIn) error("表情需要登录")
        val response = get(
            "https://api.bilibili.com/x/emote/user/panel/web",
            mapOf("business" to "reply"),
            cacheTtlMs = 30 * 60_000L,
        )
        if (response.code != 0) error(friendly(response.code, response.message ?: "表情加载失败"))
        response.data?.arr("packages")?.objects().orEmpty().flatMap { pack ->
            pack.arr("emote")?.objects().orEmpty().mapNotNull { emote ->
                val text = emote.str("text")
                val url = normalizeUrl(emote.str("url"))
                if (emote.int("type") == 4 || text.isBlank() || !url.startsWith("https://")) null else text to url
            }
        }.distinctBy { it.first }
    }

    suspend fun unreadMessages(): Result<Int> = api {
        if (!session().loggedIn) return@api 0
        coroutineScope {
            val notifications = async {
                get(
                    "https://api.bilibili.com/x/msgfeed/unread",
                    mapOf("build" to "0", "mobi_app" to "web"),
                    referer = "https://message.bilibili.com/",
                ).data
            }
            val messages = async {
                get(
                    "https://api.vc.bilibili.com/session_svr/v1/session_svr/single_unread",
                    mapOf("unread_type" to "0", "show_unfollow_list" to "0", "show_dustbin" to "0", "build" to "0", "mobi_app" to "web"),
                    referer = "https://message.bilibili.com/",
                ).data
            }
            val notice = notifications.await()
            val chat = messages.await()
            (notice?.let { maxOf(it.int("reply"), it.int("recv_reply")) + it.int("at") + maxOf(it.int("like"), it.int("recv_like")) } ?: 0) +
                (chat?.let { it.int("follow_unread") + it.int("unfollow_unread") } ?: 0)
        }
    }

    /** 私信表情面板（B 站官方表情包），key 是 [xxx] 短代码。基本不变，缓存 1 小时。 */
    suspend fun msgEmotes(): Result<List<Pair<String, String>>> = api {
        val data = get(
            "https://api.vc.bilibili.com/web_im/v1/web_im/query_available_emoticons",
            mapOf("build" to "0", "mobi_app" to "web"),
            referer = "https://message.bilibili.com/",
            cacheTtlMs = 60 * 60_000,
        ).data ?: return@api emptyList()
        val arr = data.arr("emoticons") ?: data.arr("emoticon") ?: return@api emptyList()
        arr.objects().mapNotNull { e ->
            val text = e.str("text")
            val url = normalizeUrl(e.str("url"))
            if (text.isBlank() || url.isBlank()) null else text to url
        }
    }

    /** 上传本地图片并作为图片私信发送。 */
    suspend fun sendMsgImage(receiverId: Long, bytes: ByteArray, fileName: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                if (!session().loggedIn) error("私信需要登录")
                if (bytes.isEmpty()) error("图片是空的")
                if (bytes.size > 10 * 1024 * 1024) error("图片超过 10MB，请换一张小一点的")
                val mime = when {
                    bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
                    bytes.size > 8 && bytes[1] == 'P'.code.toByte() -> "image/png"
                    bytes.size > 6 && bytes[0] == 'G'.code.toByte() -> "image/gif"
                    else -> "image/jpeg"
                }
                val upload = http.postMultipart(
                    "https://api.vc.bilibili.com/api/v1/drawImage/upload",
                    fields = mapOf("biz" to "draw", "category" to "daily"),
                    fileField = "file_up",
                    fileName = fileName.ifBlank { "image.jpg" },
                    fileBytes = bytes,
                    fileMime = mime,
                    headers = defaultHeaders("https://message.bilibili.com/", session().cookieHeader()),
                )
                val root = runCatching { JSONObject(upload.bodyText) }.getOrElse { error("图片上传失败") }
                if (root.int("code") != 0) {
                    error(friendly(root.int("code"), root.str("message").ifBlank { "图片上传失败" }))
                }
                val imageUrl = root.obj("data")?.str("image_url") ?: error("图片上传失败")
                sendMsgRaw(receiverId, type = 2, content = JSONObject().put("url", imageUrl).toString()).getOrThrow()
            }
        }

    private suspend fun sendMsgRaw(receiverId: Long, type: Int, content: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val me = session().dedeUserId ?: error("无法获取自己的 UID")
                val ts = (epochMs() / 1000).toString()
                val (code, msg) = postRaw(
                    "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg",
                    mapOf(
                        "msg[sender_uid]" to me,
                        "msg[receiver_id]" to receiverId.toString(),
                        "msg[receiver_type]" to "1",
                        "msg[msg_type]" to type.toString(),
                        "msg[msg_status]" to "0",
                        "msg[content]" to content,
                        "msg[timestamp]" to ts,
                        "msg[dev_id]" to msgDevId,
                        "csrf_token" to session().biliJct.orEmpty(),
                        "build" to "0",
                        "mobi_app" to "web",
                    ),
                    "https://message.bilibili.com/",
                )
                if (code != 0) error(friendly(code, msg ?: "发送失败"))
            }
        }

    /**
     * 直播列表。parentAreaId < 0 表示「热门」：沿用旧的跨分区兜底；
     * 指定分区时只查该分区，空列表就是空（不再拿别的分区凑数）。
     */
    suspend fun liveList(page: Int, parentAreaId: Int = -1): Result<List<VideoItem>> = api {
        ensureBuvid()
        var last = "直播列表为空"
        val areas = if (parentAreaId >= 0) intArrayOf(parentAreaId) else intArrayOf(0, 1, 2)
        for (area in areas) {
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
        if (parentAreaId >= 0) emptyList() else error(last)
    }

    /** 直播大分区列表（id + 名称），供分区浏览选择条。 */
    suspend fun liveAreas(): Result<List<app.ailiaili.core.model.LiveArea>> = api {
        ensureBuvid()
        val list = get(
            "https://api.live.bilibili.com/room/v1/Area/getList",
            mapOf("need_entrance" to "1", "parent_id" to "0"),
            referer = "https://live.bilibili.com/",
        ).data?.arr("list")?.objects().orEmpty()
        list.mapNotNull { area ->
            val id = area.int("id")
            val name = area.str("name")
            if (id > 0 && name.isNotBlank()) app.ailiaili.core.model.LiveArea(id, name) else null
        }
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
        val endpoints = data.arr("host_list")?.objects().orEmpty().mapNotNull { server ->
            val host = server.str("host").trim()
            if (host.isBlank()) null else LiveDanmakuEndpoint(
                host = host,
                port = server.int("wss_port").takeIf { it in 1..65535 } ?: 443,
            )
        }.distinct()
        if (endpoints.isEmpty()) error("弹幕服务器列表为空")
        LiveDanmakuInfo(endpoints, data.str("token"))
    }

    fun liveDanmaku(roomId: Long): Flow<LiveDanmaku> = flow {
        val info = liveDanmakuInfo(roomId).getOrThrow()
        val uid = session().dedeUserId?.toLongOrNull() ?: 0L
        emitAll(
            LiveDanmakuConnection(
                endpoints = info.endpoints,
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
        val ownerMid = anchor?.lng("uid") ?: data.lng("uid")
        // 粉丝数与认证：拿不到不阻塞直播间信息展示。
        val stat = runCatching {
            get(
                "https://api.bilibili.com/x/relation/stat",
                mapOf("vmid" to ownerMid.toString()),
            ).data
        }.getOrNull()
        LiveRoomInfo(
            title = data.str("title"),
            ownerName = anchor?.str("uname").orEmpty(),
            ownerFace = app.ailiaili.core.model.normalizeUrl(anchor?.str("face").orEmpty()),
            ownerMid = ownerMid,
            online = data.lng("online"),
            attention = data.lng("attention"),
            area = listOf(data.str("parent_area_name"), data.str("area_name"))
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            fans = stat?.lng("follower") ?: 0,
            officialVerify = anchor?.obj("official_verify")?.int("type")
                ?.takeIf { it >= 0 }
                ?.let { anchor.obj("official_verify")?.str("desc").orEmpty() }
                .orEmpty(),
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
        sessionLock.withLock {
            updateSession(session().copy(sessData = null, biliJct = null, dedeUserId = null, dedeUserIdCkMd5 = null))
        }
    }

    private suspend fun absorbCookies(headers: List<String>) {
        sessionLock.withLock {
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
    }

    private suspend fun listOf(
        url: String,
        params: Map<String, String>,
        arrayKey: String,
        cacheTtlMs: Long = 0L,
    ): Result<List<VideoItem>> = api {
        ensureBuvid()
        val data = get(url, params, cacheTtlMs = cacheTtlMs).data ?: error("列表为空")
        data.arr(arrayKey)?.objects().orEmpty().mapNotNull { parseVideo(it) }
    }

    private suspend fun <T> api(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        val result = runCatching { block() }
        when (val error = result.exceptionOrNull()) {
            null, is ApiException -> result
            is CancellationException -> throw error
            else -> Result.failure(ApiException(error.message ?: "请求失败"))
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
        cacheTtlMs: Long = 0L,
        transportRetry: Boolean = true,
    ): Resp {
        // 缓存键用原始参数（不含 wts/w_rid 签名时间戳），同参数的两次调用才能命中；
        // 带上账号标识：只带 loggedIn 布尔的话，A 退登换 B（同为已登录）会命中
        // A 的缓存（如 AI 字幕轨），TTL 内串号。
        val cacheKey = if (cacheTtlMs > 0) {
            val base = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
            val s = session()
            "${s.loggedIn}|${s.dedeUserId.orEmpty()}|$url?$base"
        } else {
            null
        }
        val ps = params.toMutableMap()
        if (sign) {
            val mixin = mixin() ?: return Resp(-400, "WBI 密钥获取失败", null)
            signParams(ps, mixin)
        }
        // 签名请求必须与 signParams 的 wbiEnc 逐字节一致（否则 w_rid 对不上）；
        // 未签名请求用标准编码：wbiEnc 会剥掉 !'()*，搜索词里的这些字符不能被改写。
        val enc: (String) -> String = if (sign) ::wbiEnc else ::urlEncode
        val full = if (ps.isEmpty()) url else url + "?" + ps.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }
        if (cacheKey != null) {
            readCache.get(cacheKey)?.let { cached ->
                return parseResp(cached, bodyKey)
            }
        }
        val text = getFlight.run(full) { text(full, referer, transportRetry) }
            ?: return Resp(-1, "网络请求失败", null)
        val trimmed = text.trimStart()
        val resp = parseResp(trimmed, bodyKey)
        if (sign && retry && resp.code == -403) {
            wbiImg = null
            wbiSub = null
            wbiAt = 0L
            return get(url, params, sign = true, referer = referer, retry = false, bodyKey = bodyKey, transportRetry = transportRetry)
        }
        if (cacheKey != null && resp.code == 0 && cacheTtlMs > 0) {
            readCache.put(cacheKey, trimmed, cacheTtlMs)
        }
        return resp
    }

    private fun parseResp(text: String, bodyKey: String): Resp {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return Resp(-412, "请求被拦截，请稍后重试", null)
        }
        val root = runCatching { JSONObject(trimmed) }.getOrElse { return Resp(-2, "响应解析失败", null) }
        val code = root.optInt("code", -999)
        val msg = root.optString("message").ifBlank { null }
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

    /**
     * GET 传输：瞬时失败（网络异常 / 408 / 429 / 5xx）指数退避重试两次。
     * B 站业务错误在 200 响应体里，不会走到重试；POST 一律不重试（点赞/投币不可幂等）。
     * 实时接口（播放地址/动态/评论/私信/搜索）传 transportRetry=false：
     * 限流时重试只会加剧风控，一次失败直接交给上层降级链。
     */
    private suspend fun text(url: String, referer: String, transportRetry: Boolean = true): String? {
        val attempts = if (transportRetry) 3 else 1
        repeat(attempts) { attempt ->
            val outcome = textOnce(url, referer)
            if (outcome != null) return outcome
            if (attempt < attempts - 1) {
                kotlinx.coroutines.delay(retryBackoffMs(attempt, kotlin.random.Random.nextDouble(-0.2, 0.2)))
            }
        }
        return null
    }

    /** 单次 GET。返回 null 表示瞬时失败值得重试；非瞬时失败原样返回响应体。 */
    private fun textOnce(url: String, referer: String): String? = runCatching {
        val resp = http.get(url, defaultHeaders(referer, session().cookieHeader()))
        val body = resp.bodyText
        when {
            resp.isSuccessful -> body
            // 网关错误页也可能带 JSON 业务码：有 JSON 体就交给上层按业务处理。
            body.trimStart().startsWith("{") -> body
            isTransientHttpStatus(resp.code) -> null // 瞬时失败：进入退避
            else -> body
        }
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
        // B 站密钥按天轮换：进程长驻（桌面托盘）时主动过期，
        // 不再等第一次 -403 失败重试才发现密钥换了。
        if (img != null && sub != null && epochMs() - wbiAt < WBI_KEY_TTL_MS) {
            return mixinKey(img, sub)
        }
        return wbiFlight.run("wbi") {
            val freshImg = wbiImg
            val freshSub = wbiSub
            if (freshImg != null && freshSub != null && epochMs() - wbiAt < WBI_KEY_TTL_MS) {
                return@run mixinKey(freshImg, freshSub)
            }
            val data = get("https://api.bilibili.com/x/web-interface/nav", emptyMap()).data ?: return@run null
            val wi = data.obj("wbi_img") ?: return@run null
            val i = fileNameOnly(wi.str("img_url"))
            val s = fileNameOnly(wi.str("sub_url"))
            if (i.isBlank() || s.isBlank()) return@run null
            wbiImg = i
            wbiSub = s
            wbiAt = epochMs()
            mixinKey(i, s)
        }
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

    private fun liveMsgFriendly(code: Int, msg: String?): String = when (code) {
        -101 -> "请先登录后再发言"
        -111 -> "登录状态已失效，请重新登录"
        -400 -> "发言内容不合规，换个说法试试"
        -403 -> "当前账号在这个直播间被禁言"
        -412, -509 -> "发言太频繁，请稍后再试"
        11000 -> "发言失败，请稍后再试"
        else -> msg?.takeIf { it.isNotBlank() } ?: friendly(code, "发言失败")
    }

    private fun danmakuFriendly(code: Int, msg: String?): String = when (code) {
        -101 -> "请先登录后再发弹幕"
        -111 -> "登录状态已失效，请重新登录"
        -400 -> "弹幕内容不合规，换个说法试试"
        -403 -> "当前账号没有发弹幕的权限（可能需要答题转正）"
        -412, -509 -> "发送太频繁，请稍后再试"
        36701 -> "弹幕包含被屏蔽的内容"
        36702 -> "弹幕最长 100 个字"
        36703 -> "发送太频繁，请稍后再试"
        36704 -> "禁止向未过审的视频发送弹幕"
        36705 -> "您的等级不足，暂时不能发弹幕"
        36706 -> "发送太频繁，请稍后再试"
        36707 -> "该视频禁止发送弹幕"
        36708 -> "该视频弹幕已满"
        36709 -> "弹幕不能为空"
        36710 -> "当前账号没有发弹幕的权限"
        36711 -> "该视频不允许发送这种类型的弹幕"
        36712 -> "当前等级不能发送彩色弹幕"
        36713 -> "当前等级不能发送这种模式的弹幕"
        36714 -> "弹幕发送时间不合法"
        else -> msg?.takeIf { it.isNotBlank() } ?: friendly(code, "弹幕发送失败")
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
        val allVideos = dash.arr("video")?.objects().orEmpty()
        if (allVideos.isEmpty()) return null
        // 设备解码不了的档位（桌面 VLC 的杜比视界/HDR/8K；Android 按 MediaCodec 能力）
        // 不进菜单也不参与选流；全是不可播档时保持原样，交给上层失败提示。
        val unsupported = unsupportedStreamQns(
            allVideos.map { StreamProbe(it.int("id"), it.str("codecs"), it.int("width"), it.int("height")) },
        )
        val videos = allVideos.filterNot { it.int("id") in unsupported }.ifEmpty { allVideos }
        val qualities = videos.map { it.int("id") }.filter { it > 0 }.distinct().sortedDescending()
            .map { app.ailiaili.core.model.Quality(it, app.ailiaili.core.model.qnLabel(it)) }
        val targetQn = app.ailiaili.core.model.clampPlayableQn(qn, qualities.map { it.qn })
        var best: JSONObject? = null
        var bestId = 0
        var bestRank = -1
        fun consider(v: JSONObject, allowAbove: Boolean) {
            val id = v.int("id")
            if (id <= 0) return
            if (!allowAbove && id > targetQn) return
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
        // 杜比(30250)/Hi-Res(30251) 不在 audio 数组里，分别在 dash.dolby.audio /
        // dash.flac.audio 对象中。fnval=4048 已请求这些档，不并进来就永远选不到。
        val audios = buildList {
            addAll(dash.arr("audio")?.objects().orEmpty())
            dash.obj("dolby")?.arr("audio")?.objects()?.let { addAll(it) }
            dash.obj("flac")?.let { flac ->
                flac.obj("audio")?.let { add(it) }
                // 个别响应把音轨字段直接平铺在 flac 对象里。
                if (flac.has("id") && flac.int("id") > 0) add(flac)
            }
        }
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
        val selectedAudioTrack = selectedAudio?.let { sel -> audios.firstOrNull { it.int("id") == sel.id } }
        return PlayInfo(
            videoUrl = videoUrl,
            audioUrl = selectedAudio?.url,
            backupUrls = CdnPicker.retryOrder(videoCands.filter { it != videoUrl }),
            durationMs = durationMs,
            qualities = qualities,
            actualQn = bestId,
            width = chosen.int("width"),
            height = chosen.int("height"),
            audioQn = selectedAudio?.id ?: 0,
            audioQualities = audioQualities,
            videoCodecs = chosen.str("codecs"),
            audioCodecs = selectedAudioTrack?.str("codecs").orEmpty(),
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
    }.distinct().let { app.ailiaili.core.model.filterAllowedMediaUrls(it) }

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
                    if (base.isNotBlank() && host.isNotBlank()) {
                        val full = host + base + extra
                        if (app.ailiaili.core.model.isAllowedMediaUrl(full)) return full
                    }
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
            val text = textOnce(url, referer) ?: return sessions
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
