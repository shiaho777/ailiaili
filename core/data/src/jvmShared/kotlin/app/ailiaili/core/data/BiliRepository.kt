package app.ailiaili.core.data

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.BangumiDetail
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.CommentPage
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.DynamicPage
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.FollowUser
import app.ailiaili.core.model.ItemPage
import app.ailiaili.core.model.MsgItem
import app.ailiaili.core.model.NavStat
import app.ailiaili.core.model.MsgSession
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.QrStatus
import app.ailiaili.core.model.QrTicket
import app.ailiaili.core.model.SearchPage
import app.ailiaili.core.model.Session
import app.ailiaili.core.model.SpaceCollection
import app.ailiaili.core.model.SpaceFeed
import app.ailiaili.core.model.SpaceInfo
import app.ailiaili.core.model.SponsorSegment
import app.ailiaili.core.model.UserState
import app.ailiaili.core.model.VideoDetail
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.VideoPage
import app.ailiaili.core.model.VideoShot
import app.ailiaili.core.model.ViewChapter
import app.ailiaili.core.network.BiliClient
import app.ailiaili.core.network.CdnPicker
import app.ailiaili.core.network.CdnStatus
import app.ailiaili.core.network.RegionFeedDecode
import kotlinx.coroutines.flow.StateFlow

interface BiliRepository {
    val session: StateFlow<Session>
    val settings: StateFlow<AppSettings>
    val cdnReport: StateFlow<String>
    fun updateSettings(block: (AppSettings) -> AppSettings)
    fun invalidateCdn()
    suspend fun userState(): Result<UserState>
    suspend fun navStat(): Result<NavStat>
    suspend fun relationStat(mid: Long): Result<Pair<Long, Long>>
    suspend fun popular(page: Int): Result<List<VideoItem>>
    suspend fun ranking(): Result<List<VideoItem>>
    suspend fun regionRank(rid: Int): Result<List<VideoItem>>
    suspend fun regionFeed(tid: Int, displayId: Int): Result<RegionFeedDecode>
    suspend fun recommend(brush: Int = 1): Result<List<VideoItem>>
    suspend fun search(keyword: String, page: Int, kind: ContentKind = ContentKind.Video): Result<SearchPage>
    suspend fun bangumiDetail(seasonId: Long): Result<BangumiDetail>
    suspend fun hotSearch(): Result<List<String>>
    suspend fun video(bvid: String): Result<VideoDetail>
    suspend fun description(bvid: String): Result<String>
    suspend fun related(bvid: String): Result<List<VideoItem>>
    suspend fun tags(bvid: String): Result<List<String>>
    suspend fun pages(bvid: String): Result<List<VideoPage>>
    suspend fun play(bvid: String, cid: Long, forceDash: Boolean = false): Result<PlayInfo>
    suspend fun danmakuSegment(cid: Long, segmentIndex: Int): Result<List<Danmaku>>
    suspend fun chapters(bvid: String, cid: Long): Result<List<ViewChapter>>
    suspend fun videoShot(bvid: String, cid: Long): Result<VideoShot?>
    suspend fun heartbeat(bvid: String, cid: Long, progressSec: Long): Result<Unit>
    suspend fun comments(aid: Long, page: Int, mode: Int = 3): Result<CommentPage>
    suspend fun replies(aid: Long, root: Long, page: Int): Result<List<Comment>>
    suspend fun online(bvid: String, cid: Long): Result<String>
    suspend fun postComment(aid: Long, message: String, root: Long = 0, parent: Long = 0): Result<Unit>
    suspend fun commentLike(aid: Long, rpid: Long, like: Boolean): Result<Unit>
    suspend fun commentDislike(aid: Long, rpid: Long, dislike: Boolean): Result<Unit>
    suspend fun dynamics(offset: String?): Result<DynamicPage>
    suspend fun space(mid: Long): Result<SpaceInfo>
    suspend fun spaceVideos(mid: Long, page: Int, order: String = "pubdate"): Result<List<VideoItem>>
    suspend fun videoStats(bvid: String): Result<Triple<Long, Long, Long>>
    suspend fun spaceDynamics(mid: Long, offset: String?): Result<SpaceFeed>
    suspend fun spaceCollections(mid: Long, page: Int = 1): Result<ItemPage<SpaceCollection>>
    suspend fun spaceCollectionVideos(mid: Long, id: Long, series: Boolean, page: Int): Result<List<VideoItem>>
    suspend fun spaceFolders(mid: Long, page: Int = 1): Result<ItemPage<FavFolder>>
    suspend fun followings(mid: Long, page: Int): Result<ItemPage<FollowUser>>
    suspend fun followingLive(limit: Int = 30): Result<List<VideoItem>>
    suspend fun history(page: Int): Result<List<VideoItem>>
    suspend fun liveHistory(page: Int): Result<List<VideoItem>>
    suspend fun deleteHistory(id: Long, live: Boolean = false): Result<Unit>
    suspend fun clearHistory(): Result<Unit>
    suspend fun favFolders(): Result<List<FavFolder>>
    suspend fun favFolderCards(): Result<List<FavFolder>>
    suspend fun videoFavFolders(aid: Long): Result<List<FavFolder>>
    suspend fun favVideos(mediaId: Long, page: Int): Result<List<VideoItem>>
    suspend fun dealFavorite(aid: Long, add: Collection<Long>, remove: Collection<Long>): Result<Unit>
    suspend fun watchLater(): Result<List<VideoItem>>
    suspend fun inWatchLater(aid: Long): Result<Boolean>
    suspend fun addWatchLater(aid: Long): Result<Unit>
    suspend fun removeWatchLater(aid: Long): Result<Unit>
    suspend fun clearWatchLater(): Result<Unit>
    suspend fun batchRemoveFav(mediaId: Long, aids: Collection<Long>): Result<Unit>
    suspend fun messages(): Result<List<MsgSession>>
    suspend fun msgHistory(talkerId: Long): Result<List<MsgItem>>
    suspend fun sendMsg(receiverId: Long, text: String): Result<Unit>
    suspend fun liveList(page: Int): Result<List<VideoItem>>
    suspend fun livePlay(roomId: Long): Result<String>
    suspend fun like(bvid: String, liked: Boolean): Result<Unit>
    suspend fun hasLiked(aid: Long): Result<Boolean>
    suspend fun coin(aid: Long, bvid: String, count: Int = 1): Result<Unit>
    suspend fun relation(mid: Long): Result<Boolean>
    suspend fun follow(mid: Long, follow: Boolean): Result<Unit>
    suspend fun reportHistory(aid: Long, cid: Long, progressSec: Long): Result<Unit>
    suspend fun sponsor(bvid: String): Result<List<SponsorSegment>>
    suspend fun qrGenerate(): Result<QrTicket>
    suspend fun qrPoll(key: String): Result<QrStatus>
    suspend fun logout()
}

class BiliRepositoryImpl(
    private val client: BiliClient,
    private val settingsStore: SettingsStore,
    sessionStore: SessionStore,
) : BiliRepository {
    // 只缓存只读详情，返回上一页再进来时不用整页重新请求。失败不缓存。
    private val videoCache = TtlCache<String, VideoDetail>(ttlMs = 60_000)
    private val pagesCache = TtlCache<String, List<VideoPage>>(ttlMs = 5 * 60_000)
    private val descriptionCache = TtlCache<String, String>(ttlMs = 10 * 60_000)
    private val relatedCache = TtlCache<String, List<VideoItem>>(ttlMs = 3 * 60_000)
    private val tagsCache = TtlCache<String, List<String>>(ttlMs = 10 * 60_000)
    private val spaceCache = TtlCache<Long, SpaceInfo>(ttlMs = 60_000)

    override val session = sessionStore.session
    override val settings = settingsStore.settings
    override val cdnReport = CdnStatus.report
    override fun updateSettings(block: (AppSettings) -> AppSettings) = settingsStore.update(block)
    override fun invalidateCdn() = CdnPicker.invalidate()
    override suspend fun userState() = client.userState()
    override suspend fun navStat() = client.navStat()
    override suspend fun relationStat(mid: Long) = client.relationStat(mid)
    override suspend fun popular(page: Int) = client.popular(page)
    override suspend fun ranking() = client.ranking()
    override suspend fun regionRank(rid: Int) = client.regionRank(rid)
    override suspend fun regionFeed(tid: Int, displayId: Int) = client.regionFeed(tid, displayId)
    override suspend fun recommend(brush: Int) = client.recommend(brush)
    override suspend fun search(keyword: String, page: Int, kind: ContentKind) = client.search(keyword, page, kind)
    override suspend fun bangumiDetail(seasonId: Long) = client.bangumiDetail(seasonId)
    override suspend fun hotSearch() = client.hotSearch()
    override suspend fun video(bvid: String) = videoCache.get(bvid) { client.video(bvid) }
    override suspend fun description(bvid: String) = descriptionCache.get(bvid) { client.description(bvid) }
    override suspend fun related(bvid: String) = relatedCache.get(bvid) { client.related(bvid) }
    override suspend fun tags(bvid: String) = tagsCache.get(bvid) { client.tags(bvid) }
    override suspend fun pages(bvid: String) = pagesCache.get(bvid) { client.pages(bvid) }
    override suspend fun play(bvid: String, cid: Long, forceDash: Boolean) =
        client.playUrl(bvid, cid, settingsStore.current, forceDash)
    override suspend fun danmakuSegment(cid: Long, segmentIndex: Int) = client.danmakuSegment(cid, segmentIndex)
    override suspend fun chapters(bvid: String, cid: Long) = client.chapters(bvid, cid)
    override suspend fun videoShot(bvid: String, cid: Long) = client.videoShot(bvid, cid)
    override suspend fun heartbeat(bvid: String, cid: Long, progressSec: Long) = client.heartbeat(bvid, cid, progressSec)
    override suspend fun comments(aid: Long, page: Int, mode: Int) = client.comments(aid, page, mode)
    override suspend fun replies(aid: Long, root: Long, page: Int) = client.replies(aid, root, page)
    override suspend fun online(bvid: String, cid: Long) = client.online(bvid, cid)
    override suspend fun postComment(aid: Long, message: String, root: Long, parent: Long) =
        client.postComment(aid, message, root, parent)
    override suspend fun commentLike(aid: Long, rpid: Long, like: Boolean) = client.commentLike(aid, rpid, like)
    override suspend fun commentDislike(aid: Long, rpid: Long, dislike: Boolean) =
        client.commentDislike(aid, rpid, dislike)
    override suspend fun dynamics(offset: String?) = client.dynamics(offset)
    override suspend fun space(mid: Long) = spaceCache.get(mid) { client.space(mid) }
    override suspend fun spaceVideos(mid: Long, page: Int, order: String) = client.spaceVideos(mid, page, order)
    override suspend fun videoStats(bvid: String) = client.videoStats(bvid)
    override suspend fun spaceDynamics(mid: Long, offset: String?) = client.spaceDynamics(mid, offset)
    override suspend fun spaceCollections(mid: Long, page: Int) = client.spaceCollections(mid, page)
    override suspend fun spaceCollectionVideos(mid: Long, id: Long, series: Boolean, page: Int) =
        client.spaceCollectionVideos(mid, id, series, page)
    override suspend fun spaceFolders(mid: Long, page: Int) = client.spaceFolders(mid, page)
    override suspend fun followings(mid: Long, page: Int) = client.followings(mid, page)
    override suspend fun followingLive(limit: Int) = client.followingLive(limit)
    override suspend fun history(page: Int) = client.history(page)
    override suspend fun liveHistory(page: Int) = client.liveHistory(page)
    override suspend fun deleteHistory(id: Long, live: Boolean) = client.deleteHistory(id, live)
    override suspend fun clearHistory() = client.clearHistory()
    override suspend fun favFolders() = client.favFolders()
    override suspend fun favFolderCards() = client.favFolderCards()
    override suspend fun videoFavFolders(aid: Long) = client.videoFavFolders(aid)
    override suspend fun favVideos(mediaId: Long, page: Int) = client.favVideos(mediaId, page)
    override suspend fun dealFavorite(aid: Long, add: Collection<Long>, remove: Collection<Long>) =
        client.dealFavorite(aid, add, remove)
    override suspend fun watchLater() = client.watchLater()
    override suspend fun inWatchLater(aid: Long) = client.inWatchLater(aid)
    override suspend fun addWatchLater(aid: Long) = client.addWatchLater(aid)
    override suspend fun removeWatchLater(aid: Long) = client.removeWatchLater(aid)
    override suspend fun clearWatchLater() = client.clearWatchLater()
    override suspend fun batchRemoveFav(mediaId: Long, aids: Collection<Long>) = client.batchRemoveFav(mediaId, aids)
    override suspend fun messages() = client.messages()
    override suspend fun msgHistory(talkerId: Long) = client.msgHistory(talkerId)
    override suspend fun sendMsg(receiverId: Long, text: String) = client.sendMsg(receiverId, text)
    override suspend fun liveList(page: Int) = client.liveList(page)
    override suspend fun livePlay(roomId: Long) = client.livePlay(roomId)
    override suspend fun like(bvid: String, liked: Boolean) =
        client.like(bvid, liked).also { if (it.isSuccess) videoCache.invalidate(bvid) }
    override suspend fun hasLiked(aid: Long) = client.hasLiked(aid)
    override suspend fun coin(aid: Long, bvid: String, count: Int) =
        client.coin(aid, bvid, count).also { if (it.isSuccess) videoCache.invalidate(bvid) }
    override suspend fun relation(mid: Long) = client.relation(mid)
    override suspend fun follow(mid: Long, follow: Boolean) = client.follow(mid, follow)
    override suspend fun reportHistory(aid: Long, cid: Long, progressSec: Long) = client.reportHistory(aid, cid, progressSec)
    override suspend fun sponsor(bvid: String) = client.sponsor(bvid)
    override suspend fun qrGenerate() = client.qrGenerate()
    override suspend fun qrPoll(key: String) = client.qrPoll(key)
    override suspend fun logout() = client.logout()
}
