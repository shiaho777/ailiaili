package app.ailiaili.feature.player

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.HistorySync
import app.ailiaili.core.data.canControlBrightness
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.data.setFullscreen
import app.ailiaili.core.designsystem.BiliActionItem
import app.ailiaili.core.designsystem.BiliAssistChip
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliErrorState
import app.ailiaili.core.designsystem.BiliLoadingState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.automirrored.filled.Send
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliSurfaceCard
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricFlow
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.metricToneColor
import app.ailiaili.core.network.CdnPicker
import app.ailiaili.core.player.BufferMediaKind
import app.ailiaili.core.player.BufferPolicy
import app.ailiaili.core.data.setWindowBrightness
import app.ailiaili.core.data.shareText
import app.ailiaili.core.data.windowBrightness
import app.ailiaili.core.designsystem.BiliContentTabs
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.LocalWindowForm
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.SponsorCategories
import app.ailiaili.core.model.SponsorSegment
import app.ailiaili.core.model.VideoDetail
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.VideoPage
import app.ailiaili.core.model.VideoShot
import app.ailiaili.core.model.ViewChapter
import app.ailiaili.core.model.epochMs
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.model.formatDay
import app.ailiaili.core.model.qnLabel
import app.ailiaili.core.player.PlaybackListener
import app.ailiaili.core.player.PlayerEngine
import app.ailiaili.core.player.VideoSurface
import coil3.compose.AsyncImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerRoute(
    bvid: String?,
    liveRoom: Long?,
    liveTitle: String,
    local: CacheEntry?,
    onBack: () -> Unit,
    onSpace: (Long) -> Unit,
    seedCid: Long = 0,
    seedAid: Long = 0,
    seedTitle: String = "",
    seedCover: String = "",
    seedAuthor: String = "",
    seedOwnerMid: Long = 0,
    seedDurationSec: Int = 0,
    seedViewCount: Long = 0,
    seedLikeCount: Long = 0,
    seedDanmakuCount: Long = 0,
    seedCoinCount: Long = 0,
    seedFavoriteCount: Long = 0,
    seedOwnerFace: String = "",
    seedPubdate: Long = 0,
    onOpenVideo: (VideoItem) -> Unit = {},
    onTag: (String) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val engine = remember { PlayerEngine() }
    fun leavePlayer() {
        engine.freeze()
        onBack()
    }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var page by remember { mutableStateOf<VideoPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<PlayInfo?>(null) }
    var listening by remember { mutableStateOf(false) }
    var listenClock by remember { mutableStateOf("00:00") }
    var avatarPreview by remember { mutableStateOf<String?>(null) }
    var commentImagePreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var danmaku by remember { mutableStateOf<List<Danmaku>>(emptyList()) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentPage by remember { mutableIntStateOf(1) }
    var commentsMore by remember { mutableStateOf(false) }
    var commentTotal by remember { mutableIntStateOf(0) }
    var commentMode by remember { mutableIntStateOf(3) }
    var commentLoading by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
    var replyExtra by remember { mutableStateOf<Map<Long, List<Comment>>>(emptyMap()) }
    var replyPage by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var replyDone by remember { mutableStateOf(setOf<Long>()) }
    var related by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var onlineText by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf("intro") }
    var descOpen by remember { mutableStateOf(false) }
    var descReady by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    val settings by AppGraph.repo.settings.collectAsState()
    var volume by remember { mutableFloatStateOf(settings.playerVolume.coerceIn(0f, 1f)) }
    fun setVolume(next: Float) {
        val v = next.coerceIn(0f, 1f)
        volume = v
        engine.volume = v
        AppGraph.repo.updateSettings { it.copy(playerVolume = v) }
    }
    LaunchedEffect(Unit) { engine.volume = volume }
    var volumeOpen by remember { mutableStateOf(false) }
    var areaOpen by remember { mutableStateOf(false) }
    var areaDraft by remember { mutableFloatStateOf(25f) }
    var undoAt by remember { mutableLongStateOf(-1L) }
    val skippedState = remember { mutableStateOf(setOf<Double>()) }
    var skippedStarts by skippedState
    var baseSpeed by remember { mutableFloatStateOf(1f) }
    var rawSegments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<ViewChapter>>(emptyList()) }
    var shot by remember { mutableStateOf<VideoShot?>(null) }
    var followed by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var coined by remember { mutableStateOf(false) }
    var coinOpen by remember { mutableStateOf(false) }
    var myCoins by remember { mutableStateOf<Double?>(null) }
    var favored by remember { mutableStateOf(false) }
    var inLater by remember { mutableStateOf(false) }
    var favOpen by remember { mutableStateOf(false) }
    var favDraft by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var favSaved by remember { mutableStateOf(setOf<Long>()) }
    var replyRoot by remember { mutableLongStateOf(0L) }
    var replyTarget by remember { mutableStateOf<Comment?>(null) }
    var reacting by remember { mutableStateOf(setOf<Long>()) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var qualityOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var fullscreen by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var composerExpanded by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(-1f) }
    var attempt by remember { mutableIntStateOf(0) }
    val form = LocalWindowForm.current
    val desktop = isDesktopHost()
    val brightnessOk = canControlBrightness()
    val splitPlayer = form.splitPlayer && !fullscreen
    var detailsReady by remember { mutableStateOf(false) }
    var gesturesReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(48)
        gesturesReady = true
        delay(132)
        detailsReady = true
    }
    val focus = remember { FocusRequester() }
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val reportAid = rememberUpdatedState(detail?.aid ?: seedAid)
    val reportCid = rememberUpdatedState(page?.cid ?: detail?.cid ?: seedCid)
    val reportBvid = rememberUpdatedState(detail?.bvid ?: bvid)
    fun fireHistory(now: Boolean) {
        val aid = reportAid.value
        val cid = reportCid.value
        val id = reportBvid.value
        val sec = runCatching { engine.currentPosition / 1000 }.getOrDefault(0L)
        scope.launch(ioDispatcher) {
            if (now) HistorySync.reportNow(aid, cid, sec, id)
            else HistorySync.report(aid, cid, sec, id)
        }
    }
    fun toggleLike() {
        val id = detail?.bvid ?: bvid
        if (id.isNullOrBlank()) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "点赞需要先登录"
            return
        }
        scope.launch {
            val next = !liked
            AppGraph.repo.like(id, next).onSuccess {
                liked = next
                detail = detail?.copy(
                    likeCount = (detail?.likeCount ?: 0).let { count ->
                        (count + if (next) 1 else -1).coerceAtLeast(0)
                    },
                )
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                toast = if (next) "已点赞" else "已取消点赞"
            }.onFailure { toast = it.message ?: "点赞失败" }
        }
    }

    LaunchedEffect(toast) {
        val message = toast ?: return@LaunchedEffect
        delay(1600)
        if (toast == message) toast = null
    }

    DisposableEffect(engine) {
        val listener = object : PlaybackListener {
            override fun onError() {
                val pos = engine.currentPosition
                if (!engine.failOver(pos)) error = "播放失败，线路都不可用"
            }

            override fun onEnded(endedNow: Boolean) {
                ended = endedNow
                if (endedNow) fireHistory(now = true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) ended = false else fireHistory(now = true)
            }
        }
        engine.addListener(listener)
        onDispose {
            PlayMemory.flush()
            fireHistory(now = true)
            engine.removeListener(listener)
            engine.freeze()
            // ExoPlayer 必须在主线程 release；VLC 的 release 可能阻塞，放 IO。
            if (isDesktopHost()) {
                CoroutineScope(ioDispatcher).launch { engine.release() }
            } else {
                engine.release()
            }
        }
    }

    DisposableEffect(fullscreen) {
        setFullscreen(fullscreen)
        onDispose { setFullscreen(false) }
    }

    BackHandler {
        if (fullscreen) fullscreen = false else leavePlayer()
    }

    fun playingCid(): Long = page?.cid?.takeIf { it > 0 } ?: detail?.cid ?: 0L

    fun start(
        targetBvid: String,
        cid: Long,
        seek: Long = 0,
        durationSec: Int = 0,
        forceDash: Boolean = false,
    ) {
        ended = false
        skippedStarts = emptySet()
        undoAt = -1L
        danmaku = emptyList()
        rawSegments = emptyList()
        chapters = emptyList()
        shot = null
        scope.launch {
            error = null
            val play = AppGraph.repo.play(targetBvid, cid, forceDash)
            play.onSuccess { got ->
                info = got
                val audioOnly = listening && !got.audioUrl.isNullOrBlank()
                if (listening && !audioOnly) {
                    listening = false
                    toast = "这条视频没有可用的音轨"
                }
                val durationMs = durationSec * 1000L
                val remembered = if (seek <= 0) PlayMemory.read(targetBvid, cid) else 0L
                val resume = remembered in 5_000 until (durationMs - 15_000).coerceAtLeast(5_000)
                val startAt = if (seek > 0) seek else if (resume) remembered else 0L
                if (resume && seek <= 0) {
                    undoAt = 0L
                    hint = "已回到上次看到的位置"
                }
                val kind = if (got.audioUrl.isNullOrBlank()) {
                    BufferMediaKind.VodSingle
                } else {
                    BufferMediaKind.VodDash
                }
                val mediaUrl = if (audioOnly) got.audioUrl.orEmpty() else got.videoUrl
                engine.networkCachingMs = BufferPolicy.resolve(
                    kind = kind,
                    shortVideo = durationMs in 1..180_000,
                    userMs = settings.networkCachingMs,
                    auto = settings.bufferAuto,
                    measuredBytesPerSecond = CdnPicker.lastMeasuredBytesPerSecond,
                    stallBoostMs = CdnPicker.stallBoostMs(mediaUrl),
                )
                runCatching { engine.play(got, startAt, audioOnly = audioOnly) }
                    .onFailure { error = it.message ?: "播放失败" }
            }.onFailure { error = it.message }
        }
        scope.launch {
            if (!settings.sponsorBlock) return@launch
            AppGraph.repo.sponsor(targetBvid).onSuccess { got ->
                if (detail?.bvid == targetBvid) rawSegments = got
            }
        }
        scope.launch {
            AppGraph.repo.chapters(targetBvid, cid).onSuccess { got ->
                if (playingCid() == cid) chapters = got
            }
        }
        scope.launch {
            AppGraph.repo.videoShot(targetBvid, cid).onSuccess { got ->
                if (playingCid() == cid) shot = got
            }
        }
    }

    fun loadComments(aid: Long, pageNum: Int, mode: Int, append: Boolean) {
        if (aid <= 0) return
        commentLoading = true
        commentError = null
        scope.launch {
            AppGraph.repo.comments(aid, pageNum, mode).onSuccess { got ->
                comments = if (append) (comments + got.items).distinctBy { it.rpid } else got.items
                commentPage = got.page
                commentsMore = got.hasMore
                if (got.total > 0) commentTotal = got.total
                commentError = null
            }.onFailure { commentError = it.message ?: "评论加载失败" }
            commentLoading = false
        }
    }

    fun applyDetail(d: VideoDetail) {
        detail = d
        panel = "intro"
        descOpen = false
        descReady = d.desc.isNotBlank()
        replyExtra = emptyMap()
        replyPage = emptyMap()
        replyDone = emptySet()
        replyTarget = null
        replyRoot = 0L
        liked = false
        coined = d.coined
        favored = false
        inLater = false
        favOpen = false
        onlineText = null
        comments = emptyList()
        commentTotal = 0
        commentError = null
        page = d.pages.firstOrNull { it.cid == d.cid } ?: d.pages.firstOrNull()
        val cid = page?.cid ?: d.cid
        if (cid > 0) start(d.bvid, cid, durationSec = page?.durationSec?.takeIf { it > 0 } ?: d.durationSec)
        else error = "没有可播放的分P"
        if (d.aid > 0) loadComments(d.aid, 1, commentMode, append = false)
        scope.launch {
            AppGraph.repo.description(d.bvid)
                .onSuccess { text ->
                    if (detail?.bvid != d.bvid) return@onSuccess
                    if (text.isNotBlank()) detail = detail?.copy(desc = text)
                    descReady = true
                }
                .onFailure { if (detail?.bvid == d.bvid) descReady = true }
        }
        scope.launch { AppGraph.repo.related(d.bvid).onSuccess { if (detail?.bvid == d.bvid) related = it } }
        scope.launch { AppGraph.repo.tags(d.bvid).onSuccess { if (detail?.bvid == d.bvid) tags = it } }
        if (cid > 0) {
            scope.launch {
                AppGraph.repo.online(d.bvid, cid).onSuccess { if (detail?.bvid == d.bvid) onlineText = it }
            }
        }
        if (AppGraph.repo.session.value.loggedIn) {
            val mine = AppGraph.repo.session.value.dedeUserId?.toLongOrNull()
            if (d.ownerMid > 0 && d.ownerMid != mine) {
                scope.launch { followed = AppGraph.repo.relation(d.ownerMid).getOrDefault(false) }
            }
            if (d.aid > 0) {
                scope.launch {
                    AppGraph.repo.hasLiked(d.aid).onSuccess { if (detail?.bvid == d.bvid) liked = it }
                }
                scope.launch {
                    AppGraph.repo.videoFavFolders(d.aid).onSuccess { folders ->
                        if (detail?.bvid == d.bvid) favored = folders.any { it.favored }
                    }
                }
                scope.launch {
                    AppGraph.repo.inWatchLater(d.aid).onSuccess { if (detail?.bvid == d.bvid) inLater = it }
                }
            }
        }
    }

    fun patchComment(rpid: Long, block: (Comment) -> Comment) {
        fun walk(list: List<Comment>): List<Comment> = list.map { item ->
            val self = if (item.rpid == rpid) block(item) else item
            self.copy(replies = walk(self.replies))
        }
        comments = walk(comments)
        replyExtra = replyExtra.mapValues { (_, list) -> walk(list) }
    }

    fun reactComment(target: Comment, like: Boolean) {
        val aid = detail?.aid ?: 0L
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = if (like) "请先登录后再点赞" else "请先登录后再点踩"
            return
        }
        if (aid <= 0 || target.rpid <= 0 || target.rpid in reacting) return
        val turningOn = if (like) !target.liked else !target.disliked
        val snapshot = target
        reacting = reacting + target.rpid
        patchComment(target.rpid) { current ->
            if (like) {
                current.copy(
                    liked = turningOn,
                    like = (current.like + if (turningOn) 1 else -1).coerceAtLeast(0),
                    disliked = if (turningOn) false else current.disliked,
                )
            } else {
                current.copy(
                    disliked = turningOn,
                    liked = if (turningOn) false else current.liked,
                    like = if (turningOn && current.liked) (current.like - 1).coerceAtLeast(0) else current.like,
                )
            }
        }
        scope.launch {
            val result = if (like) {
                AppGraph.repo.commentLike(aid, target.rpid, turningOn)
            } else {
                AppGraph.repo.commentDislike(aid, target.rpid, turningOn)
            }
            result.onFailure {
                patchComment(target.rpid) { current ->
                    current.copy(liked = snapshot.liked, disliked = snapshot.disliked, like = snapshot.like)
                }
                error = it.message
            }
            reacting = reacting - target.rpid
        }
    }

    fun giveCoins(count: Int) {
        val aid = detail?.aid ?: return
        val id = bvid ?: return
        coinOpen = false
        scope.launch {
            AppGraph.repo.coin(aid, id, count).onSuccess {
                coined = true
                detail = detail?.copy(coinCount = (detail?.coinCount ?: 0) + count, coined = true)
                myCoins = myCoins?.let { (it - count).coerceAtLeast(0.0) }
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                toast = "已投 $count 枚"
            }.onFailure { toast = it.message ?: "投币失败" }
        }
    }

    fun openEpisode(ep: app.ailiaili.core.model.Episode) {
        scope.launch {
            val next = AppGraph.repo.video(ep.bvid).getOrNull()
            if (next != null) {
                toast = null
                applyDetail(next.copy(cid = ep.cid.takeIf { it > 0 } ?: next.cid))
            } else {
                val cid = ep.cid.takeIf { it > 0 }
                    ?: AppGraph.repo.pages(ep.bvid).getOrNull()?.firstOrNull()?.cid
                    ?: 0L
                if (cid <= 0) {
                    error = "这一集暂时打不开"
                } else {
                    applyDetail(
                        fallbackDetail(
                            bvid = ep.bvid,
                            cid = cid,
                            aid = 0,
                            title = ep.title,
                            cover = ep.cover,
                            author = detail?.owner.orEmpty(),
                            ownerMid = detail?.ownerMid ?: 0,
                            ownerFace = detail?.ownerFace.orEmpty(),
                            durationSec = 0,
                            pages = emptyList(),
                        ),
                    )
                }
            }
        }
    }

    LaunchedEffect(bvid, liveRoom, local?.bvid, attempt) {
        if (liveRoom != null || local != null) listening = false
        coroutineScope {
            val ready = launch(ioDispatcher) { engine.prepare() }
            when {
                local != null -> {
                    ready.join()
                    engine.playFiles(local.videoPath, local.audioPath)
                }
                liveRoom != null -> {
                    val play = AppGraph.repo.livePlay(liveRoom)
                    ready.join()
                    play.onSuccess {
                        engine.networkCachingMs = BufferPolicy.resolve(
                            kind = BufferMediaKind.Live,
                            shortVideo = false,
                            userMs = settings.networkCachingMs,
                            auto = settings.bufferAuto,
                            measuredBytesPerSecond = CdnPicker.lastMeasuredBytesPerSecond,
                            stallBoostMs = CdnPicker.stallBoostMs(it),
                        )
                        engine.playLive(it)
                    }.onFailure { error = it.message }
                }
                !bvid.isNullOrBlank() -> {
                    error = null
                    toast = null
                    info = null
                    danmaku = emptyList()
                    rawSegments = emptyList()
                    descReady = false
                    val loaded = AppGraph.repo.video(bvid)
                    val resolved = loaded.getOrElse {
                        val pages = AppGraph.repo.pages(bvid).getOrNull().orEmpty()
                        val cid = seedCid.takeIf { it > 0 } ?: pages.firstOrNull()?.cid ?: 0L
                        if (cid <= 0) {
                            error = it.message
                            return@coroutineScope
                        }
                        val chosen = pages.firstOrNull { p -> p.cid == cid } ?: pages.firstOrNull()
                        fallbackDetail(
                            bvid = bvid,
                            cid = cid,
                            aid = seedAid,
                            title = seedTitle.ifBlank { chosen?.part ?: "播放" },
                            cover = seedCover,
                            author = seedAuthor,
                            ownerMid = seedOwnerMid,
                            ownerFace = seedOwnerFace,
                            durationSec = chosen?.durationSec?.takeIf { sec -> sec > 0 } ?: seedDurationSec,
                            pages = pages,
                            viewCount = seedViewCount,
                            likeCount = seedLikeCount,
                            danmakuCount = seedDanmakuCount,
                            coinCount = seedCoinCount,
                            favoriteCount = seedFavoriteCount,
                            pubdate = seedPubdate,
                        )
                    }
                    ready.join()
                    applyDetail(resolved)
                }
                else -> ready.join()
            }
        }
    }

    LaunchedEffect(speed) { engine.setPlaybackSpeed(speed) }

    // 播放中途卡顿检测：连续 1 秒位置不推进才算卡顿，避免正常抖动误报。
    // 上报按 CDN host 归因，后续视频的 BufferPolicy 会自动加深预读。
    LaunchedEffect(info, error, scrubbing) {
        var last = engine.currentPosition
        var still = 0
        var reported = false
        while (info != null && error == null) {
            delay(500)
            if (scrubbing || !engine.isPlaying) {
                last = engine.currentPosition
                still = 0
                buffering = false
                reported = false
                continue
            }
            val now = engine.currentPosition
            if (now > 0 && now == last) {
                still++
                if (still >= 2) {
                    buffering = true
                    if (!reported) {
                        val url = if (listening) info?.audioUrl else info?.videoUrl
                        CdnPicker.reportStall(url.orEmpty())
                        reported = true
                    }
                }
            } else {
                still = 0
                buffering = false
                reported = false
            }
            last = now
        }
        buffering = false
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(900)
            hint = null
        }
    }

    LaunchedEffect(chrome, playing, scrubbing, qualityOpen, speedOpen, volumeOpen, areaOpen) {
        if (chrome && playing && !scrubbing && !qualityOpen && !speedOpen && !volumeOpen && !areaOpen) {
            delay(3500)
            chrome = false
            volumeOpen = false
        }
    }

    val sponsorOn = rememberUpdatedState(settings.sponsorBlock)
    val sponsorRaw = rememberUpdatedState(rawSegments)
    val sponsorCid = rememberUpdatedState(page?.cid ?: detail?.cid ?: 0L)
    val sponsorCats = rememberUpdatedState(settings.sponsorCategories)
    val activeMarks = SponsorCategories.select(
        rawSegments,
        page?.cid ?: detail?.cid ?: 0L,
        settings.sponsorCategories,
    )
    LaunchedEffect(engine) {
        while (true) {
            if (sponsorOn.value) {
                val pos = runCatching { engine.currentPosition }.getOrNull()
                if (pos != null) {
                    val bvidNow = detail?.bvid
                    val cidNow = page?.cid ?: detail?.cid ?: 0L
                    if (!bvidNow.isNullOrBlank() && cidNow > 0) PlayMemory.write(bvidNow, cidNow, pos)
                    val sec = pos / 1000.0
                    val seen = skippedState.value
                    val hit = SponsorCategories.select(sponsorRaw.value, sponsorCid.value, sponsorCats.value)
                        .firstOrNull {
                            sec >= it.startSec && sec < it.endSec - 0.4 && it.startSec !in seen
                        }
                    if (hit != null) {
                        skippedState.value = seen + hit.startSec
                        undoAt = pos
                        engine.seekTo((hit.endSec * 1000).toLong())
                        hint = "已跳过${SponsorCategories.label(hit.category)}"
                    }
                }
            }
            delay(400)
        }
    }

    LaunchedEffect(settings.sponsorBlock, detail?.bvid) {
        val id = detail?.bvid ?: return@LaunchedEffect
        if (!settings.sponsorBlock || rawSegments.isNotEmpty()) return@LaunchedEffect
        AppGraph.repo.sponsor(id).onSuccess { if (detail?.bvid == id) rawSegments = it }
    }

    LaunchedEffect(detail?.aid, page?.cid) {
        HistorySync.reset()
        val aid = detail?.aid ?: return@LaunchedEffect
        val cid = page?.cid ?: return@LaunchedEffect
        if (aid <= 0 || !AppGraph.repo.session.value.loggedIn) return@LaunchedEffect
        while (true) {
            delay(1_000)
            if (!engine.isPlaying) continue
            HistorySync.report(aid, cid, engine.currentPosition / 1000, detail?.bvid)
        }
    }

    LaunchedEffect(page?.cid, detail?.cid, detail?.bvid) {
        val cid = page?.cid?.takeIf { it > 0 } ?: detail?.cid?.takeIf { it > 0 } ?: return@LaunchedEffect
        var loaded = HashSet<Int>()
        var merged = emptyList<Danmaku>()
        var activeSeg = -1
        var generation = 0
        danmaku = emptyList()
        while (isActive) {
            if (playingCid() != cid) return@LaunchedEffect
            val playerMs = runCatching { engine.duration }.getOrDefault(0L)
            val dur = if (playerMs > 0) {
                (playerMs / 1000).toInt()
            } else {
                page?.durationSec?.takeIf { it > 0 } ?: detail?.durationSec ?: 0
            }
            val posSec = runCatching { engine.currentPosition / 1000.0 }.getOrDefault(0.0)
            val seg = (posSec.toInt() / 360).coerceAtLeast(0)
            if (seg != activeSeg) {
                // 换段就换一代请求。旧段在途的响应回来时直接丢掉，
                // 避免连续拖进度条后旧弹幕覆盖新位置。
                activeSeg = seg
                generation++
                loaded = HashSet()
                merged = emptyList()
            }
            val requestGen = generation
            val maxSeg = if (dur > 0) ((dur + 359) / 360).coerceIn(1, 120) else 20
            for (index in intArrayOf(seg, seg + 1)) {
                if (index !in 0 until maxSeg || !loaded.add(index)) continue
                AppGraph.repo.danmakuSegment(cid, index + 1)
                    .onSuccess { items ->
                        if (playingCid() != cid ||
                            requestGen != generation ||
                            activeSeg != seg
                        ) {
                            return@onSuccess
                        }
                        merged = mergeDanmaku(merged, items)
                        danmaku = merged
                    }
                    .onFailure { loaded.remove(index) }
            }
            delay(500)
        }
    }

    val title = local?.title ?: liveTitle.ifBlank { detail?.title?.ifBlank { null } ?: seedTitle.ifBlank { "播放" } }
    val cover = local?.cover ?: detail?.pic?.ifBlank { null } ?: seedCover
    val showPoster = cover.isNotBlank() && info == null && local == null && liveRoom == null
    val scroll = rememberScrollState()
    LaunchedEffect(bvid) { scroll.scrollTo(0) }
    val stageRatio = run {
        val w = info?.width ?: 0
        val h = info?.height ?: 0
        if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
    }
    val showChrome = chrome || !playing
    LaunchedEffect(listening) {
        while (listening) {
            val sec = (engine.currentPosition.coerceAtLeast(0L) / 1000)
            listenClock = "${(sec / 60).toString().padStart(2, '0')}:${(sec % 60).toString().padStart(2, '0')}"
            delay(500)
        }
    }
    avatarPreview?.let { faceUrl ->
        app.ailiaili.core.designsystem.AvatarPreviewDialog(
            url = faceUrl,
            name = detail?.owner ?: seedAuthor,
            onDismiss = { avatarPreview = null },
            onSaved = { toast = it },
        )
    }
    commentImagePreview?.let { (url, name) ->
        app.ailiaili.core.designsystem.AvatarPreviewDialog(
            url = url,
            name = name,
            onDismiss = { commentImagePreview = null },
            onSaved = { toast = it },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (composing) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Spacebar, Key.MediaPlayPause -> {
                        if (engine.isPlaying) engine.pause() else engine.play()
                        true
                    }
                    Key.DirectionLeft -> {
                        engine.seekTo((engine.currentPosition - 5_000).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        engine.seekTo(engine.currentPosition + 5_000)
                        true
                    }
                    Key.DirectionUp -> {
                        setVolume(volume + 0.1f)
                        true
                    }
                    Key.DirectionDown -> {
                        setVolume(volume - 0.1f)
                        true
                    }
                    Key.M -> {
                        setVolume(if (volume == 0f) 0.8f else 0f)
                        true
                    }
                    Key.F -> {
                        fullscreen = !fullscreen
                        true
                    }
                    Key.D -> {
                        AppGraph.repo.updateSettings { it.copy(danmakuEnabled = !it.danmakuEnabled) }
                        true
                    }
                    Key.L -> {
                        toggleLike()
                        true
                    }
                    Key.U -> {
                        val mid = detail?.ownerMid?.takeIf { it > 0 } ?: seedOwnerMid
                        if (mid > 0) onSpace(mid) else toast = "没有 UP 主信息"
                        true
                    }
                    Key.Escape -> {
                        if (fullscreen) fullscreen = false else leavePlayer()
                        true
                    }
                    else -> false
                }
            }
            .background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background)
            .then(
                if (fullscreen) Modifier
                else Modifier.statusBarsPadding().navigationBarsPadding(),
            ),
    ) {
        @Composable
        fun Stage(modifier: Modifier) {
        BoxWithConstraints(modifier) {
            val fillStage = fullscreen || splitPlayer
            val stageHeight = if (fillStage) {
                if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) 300.dp else maxHeight
            } else {
                minOf(maxWidth / stageRatio, 300.dp)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    .background(Color.Black),
            ) {
            VideoSurface(engine, Modifier.fillMaxSize())
            if (showPoster) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (bvid != null && info != null && settings.danmakuEnabled) {
                if (!listening) {
                    DanmakuOverlay(
                        engine,
                        danmaku,
                        settings,
                        frozen = buffering || scrubbing,
                    )
                }
            }
            if (bvid != null || liveRoom != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (!gesturesReady) Modifier
                            else Modifier.pointerInput(baseSpeed, liveRoom, desktop, brightnessOk) {
                            var lastTap = 0L
                            var lastX = 0f
                            var chromeJob: kotlinx.coroutines.Job? = null
                            var hintJob: kotlinx.coroutines.Job? = null
                            fun showHint(text: String, linger: Boolean) {
                                hint = text
                                hintJob?.cancel()
                                if (linger) {
                                    hintJob = scope.launch {
                                        kotlinx.coroutines.delay(800)
                                        if (hint == text) hint = null
                                    }
                                }
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val origin = down.position
                                var moved = false
                                var holding = false
                                var mode = 0
                                var startPos = engine.currentPosition
                                var startVolume = volume
                                val startBrightness = windowBrightness()
                                val hold = if (desktop) {
                                    null
                                } else {
                                    scope.launch {
                                        kotlinx.coroutines.delay(320)
                                        if (!moved && liveRoom == null) {
                                            holding = true
                                            speed = 2f
                                            showHint("2 倍速", linger = false)
                                        }
                                    }
                                }
                                var pendingSeek = startPos
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        hold?.cancel()
                                        if (holding) {
                                            speed = baseSpeed
                                            hintJob?.cancel()
                                            hint = null
                                        } else if (moved && liveRoom == null) {
                                            if (mode == 1) {
                                                engine.seekTo(pendingSeek.coerceAtLeast(0L))
                                                showHint("滑到 ${clock(pendingSeek)}", linger = true)
                                            } else {
                                                showHint(hint.orEmpty(), linger = true)
                                            }
                                        } else if (!moved) {
                                            val now = epochMs()
                                            val x = change.position.x
                                            val double = now - lastTap < 280L && kotlin.math.abs(x - lastX) < 120f
                                            lastTap = now
                                            lastX = x
                                            chromeJob?.cancel()
                                            if (desktop) {
                                                if (double && liveRoom == null) {
                                                    fullscreen = !fullscreen
                                                } else if (liveRoom == null) {
                                                    val willPlay = !engine.isPlaying
                                                    if (willPlay) engine.play() else engine.pause()
                                                    chrome = true
                                                    showHint(if (willPlay) "播放" else "暂停", linger = true)
                                                } else {
                                                    chrome = !chrome
                                                }
                                            } else if (double && liveRoom == null) {
                                                val width = size.width.toFloat()
                                                when {
                                                    x < width / 3f -> {
                                                        engine.seekTo((engine.currentPosition - 10_000).coerceAtLeast(0L))
                                                        showHint("后退 10 秒", linger = true)
                                                    }
                                                    x > width * 2f / 3f -> {
                                                        engine.seekTo(engine.currentPosition + 10_000)
                                                        showHint("前进 10 秒", linger = true)
                                                    }
                                                    else -> {
                                                        val willPlay = !engine.isPlaying
                                                        if (willPlay) engine.play() else engine.pause()
                                                        showHint(if (willPlay) "播放" else "暂停", linger = true)
                                                    }
                                                }
                                            } else {
                                                chromeJob = scope.launch {
                                                    kotlinx.coroutines.delay(260)
                                                    chrome = !chrome
                                                }
                                            }
                                        }
                                        break
                                    }
                                    val dx = change.position.x - origin.x
                                    val dy = change.position.y - origin.y
                                    if (!moved && !holding && (kotlin.math.abs(dx) > 28f || kotlin.math.abs(dy) > 28f)) {
                                        moved = true
                                        hold?.cancel()
                                        mode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                            1
                                        } else if (origin.x < size.width / 2f && brightnessOk) {
                                            2
                                        } else {
                                            3
                                        }
                                        startPos = engine.currentPosition
                                    }
                                    if (moved && liveRoom == null) {
                                        change.consume()
                                        when (mode) {
                                            1 -> {
                                                val duration = engine.duration.coerceAtLeast(1L)
                                                val delta = (dx / size.width * 90_000f).toLong()
                                                pendingSeek = (startPos + delta).coerceIn(0L, duration)
                                                showHint("滑到 ${clock(pendingSeek)}", linger = false)
                                            }
                                            2 -> {
                                                val next = (startBrightness - dy / size.height).coerceIn(0.02f, 1f)
                                                setWindowBrightness(next)
                                                showHint("亮度 ${(next * 100).toInt()}", linger = false)
                                            }
                                            3 -> {
                                                setVolume(startVolume - dy / size.height)
                                                showHint("音量 ${(volume * 100).toInt()}", linger = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        )
                        .then(
                            if (desktop) {
                                Modifier.pointerInput(volume) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.type == PointerEventType.Scroll) {
                                                val dy = event.changes.first().scrollDelta.y
                                                setVolume(volume - dy * 0.05f)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            if (buffering && info != null && error == null && !ended) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Text("正在缓冲…", color = Color.White, fontSize = 13.sp)
                }
            }
            toast?.let { message ->
                Text(
                    message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
            hint?.let { message ->
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, color = Color.White)
                    if (undoAt >= 0 && (message.startsWith("已跳过") || message.startsWith("已回到"))) {
                        TextButton(onClick = {
                            engine.seekTo(undoAt)
                            undoAt = -1L
                            hint = null
                        }) { Text(if (message.startsWith("已回到")) "从头播放" else "撤回") }
                    }
                }
            }
            if (ended && liveRoom == null && local == null && info != null) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("已播完", color = Color.White)
                    FilledTonalButton(onClick = {
                        skippedStarts = emptySet()
                        engine.seekTo(0)
                        engine.play()
                        ended = false
                    }) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重播")
                    }
                }
            }
            if (info == null && error == null && local == null) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            }
            error?.let { message ->
                if (info == null) {
                    Column(
                        Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { attempt++ }) { Text("重试") }
                    }
                }
            }
            if (listening) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xF0141414)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (cover.isNotBlank()) {
                        AsyncImage(
                            cover,
                            title,
                            Modifier.size(148.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    }
                    Text(
                        title,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text("正在聆听 $listenClock", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val wave = rememberInfiniteTransition()
                        repeat(5) { index ->
                            val bar by wave.animateFloat(
                                initialValue = 8f,
                                targetValue = 28f,
                                animationSpec = infiniteRepeatable(
                                    tween(480, delayMillis = index * 90),
                                    RepeatMode.Reverse,
                                ),
                            )
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(bar.dp)
                                    .background(Color(0xFFF59E0B)),
                            )
                        }
                    }
                }
            }
            if (showChrome) Row(
                Modifier
                    .align(Alignment.TopStart)
                    .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (fullscreen) fullscreen = false else leavePlayer() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                if (fullscreen) {
                    Text(
                        title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (info != null && liveRoom == null) {
                    Box {
                        TextButton(onClick = { qualityOpen = true }) {
                            Text(qnLabel(info?.actualQn ?: settings.defaultQn), color = Color.White)
                        }
                        DropdownMenu(expanded = qualityOpen, onDismissRequest = { qualityOpen = false }) {
                            (info?.qualities.orEmpty()).forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q.label) },
                                    onClick = {
                                        qualityOpen = false
                                        val d = detail ?: return@DropdownMenuItem
                                        val cid = page?.cid ?: d.cid
                                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
                                        scope.launch {
                                            runCatching {
                                                AppGraph.repo.updateSettings { it.copy(defaultQn = q.qn) }
                                                start(d.bvid, cid, engine.currentPosition, page?.durationSec ?: d.durationSec)
                                            }.onFailure { error = it.message ?: "切换清晰度失败" }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (liveRoom == null && local == null && info != null) {
                    TextButton(onClick = {
                        val pos = engine.currentPosition
                        if (listening) {
                            listening = false
                            engine.restoreVideo(pos)
                        } else if (engine.playAudioOnly(pos)) {
                            listening = true
                        } else {
                            // durl 合流单流没有独立音轨；切回 DASH 再取一次 audioUrl。
                            val id = bvid ?: detail?.bvid
                            val cidNow = page?.cid ?: detail?.cid ?: 0L
                            if (id.isNullOrBlank() || cidNow <= 0) {
                                toast = "这条视频没有可用的音轨"
                            } else {
                                listening = true
                                start(
                                    id,
                                    cidNow,
                                    seek = pos,
                                    durationSec = page?.durationSec
                                        ?: detail?.durationSec
                                        ?: 0,
                                    forceDash = true,
                                )
                            }
                        }
                    }) {
                        Text(
                            if (listening) "正在聆听 $listenClock" else "听视频",
                            color = if (listening) Color(0xFFF59E0B) else Color.White,
                        )
                    }
                }
                IconButton(onClick = {
                    AppGraph.repo.updateSettings { it.copy(danmakuEnabled = !it.danmakuEnabled) }
                }) {
                    Icon(
                        if (settings.danmakuEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                        contentDescription = if (settings.danmakuEnabled) "关闭弹幕" else "打开弹幕",
                        tint = Color.White,
                    )
                }
                if (bvid != null) {
                    Box {
                        TextButton(onClick = {
                            areaDraft = settings.danmakuAreaPercent.toFloat()
                            areaOpen = true
                        }) { Text("${settings.danmakuAreaPercent}%", color = Color.White) }
                        DropdownMenu(expanded = areaOpen, onDismissRequest = { areaOpen = false }) {
                            Column(Modifier.padding(horizontal = 16.dp).width(220.dp)) {
                                Text("弹幕区域 ${areaDraft.toInt()}%")
                                Slider(
                                    value = areaDraft,
                                    onValueChange = { areaDraft = it },
                                    onValueChangeFinished = {
                                        AppGraph.repo.updateSettings {
                                            it.copy(danmakuAreaPercent = areaDraft.toInt().coerceIn(25, 100))
                                        }
                                    },
                                    valueRange = 25f..100f,
                                )
                            }
                        }
                    }
                }
                if (liveRoom == null) {
                    Box {
                        TextButton(onClick = { speedOpen = true }) {
                            Text(speedLabel(speed), color = Color.White)
                        }
                        DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                            speeds.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(speedLabel(value)) },
                                    onClick = {
                                        speedOpen = false
                                        speed = value
                                        baseSpeed = value
                                        runCatching { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = {
                    fullscreen = !fullscreen
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                    )
                }
            }
            if (showChrome) {
                PlayerTransport(
                    engine = engine,
                    live = liveRoom != null,
                    playing = playing,
                    onScrubbing = { scrubbing = it },
                    onSeek = { ms ->
                        engine.seekTo(ms)
                        if (ended) {
                            engine.play()
                            ended = false
                        }
                    },
                    onTogglePlay = {
                        if (engine.isPlaying) engine.pause() else engine.play()
                    },
                    onVolume = { volumeOpen = !volumeOpen },
                    muted = volume <= 0.01f,
                    marks = if (settings.sponsorBlock) activeMarks else emptyList(),
                    chapters = chapters,
                    shot = shot,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (fullscreen) Modifier.navigationBarsPadding() else Modifier),
                )
            }
            if (showChrome && volumeOpen) {
                Slider(
                    value = volume,
                    onValueChange = { setVolume(it) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 64.dp)
                        .fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                    ),
                )
            }
            }
        }
        }

        @Composable
        fun Details(modifier: Modifier) {
            Column(
                modifier
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VideoHeader(title = title, detail = detail, onlineText = onlineText)
                val owner = detail?.owner?.ifBlank { null } ?: seedAuthor
                val mid = detail?.ownerMid?.takeIf { it > 0 } ?: seedOwnerMid
                val face = detail?.ownerFace?.ifBlank { null } ?: seedOwnerFace
                val mine = AppGraph.repo.session.collectAsState().value.dedeUserId?.toLongOrNull()
                val self = mine != null && mine == mid
                if (owner.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (face.isNotBlank()) {
                                AsyncImage(
                                    model = face,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            enabled = mid > 0 || face.isNotBlank(),
                                            onClick = { if (mid > 0) onSpace(mid) },
                                            onLongClick = { if (face.isNotBlank()) avatarPreview = face },
                                        ),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                owner,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = mid > 0) { onSpace(mid) },
                            )
                            if (!self) {
                                FilledTonalButton(onClick = {
                                    if (mid <= 0) toast = "没有 UP 主信息"
                                    else scope.launch {
                                        val next = !followed
                                        AppGraph.repo.follow(mid, next).onSuccess {
                                            followed = next
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            toast = if (next) "已关注" else "已取消关注"
                                        }.onFailure { toast = it.message ?: "关注失败" }
                                    }
                                }) { Text(if (followed) "已关注" else "关注") }
                            }
                        }
                    }
                }
                if (bvid != null) {
                    val likeAccent = metricToneColor(MetricTone.Like)
                    val coinAccent = metricToneColor(MetricTone.Coin)
                    val favAccent = metricToneColor(MetricTone.Favorite)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Act(Icons.Filled.ThumbUp, actLabel("点赞", detail?.likeCount ?: 0), Modifier.weight(1f), liked, likeAccent) {
                            toggleLike()
                        }
                        Act(Icons.Filled.MonetizationOn, actLabel("投币", detail?.coinCount ?: 0), Modifier.weight(1f), coined, coinAccent) {
                            val aid = detail?.aid ?: 0L
                            if (!AppGraph.repo.session.value.loggedIn) toast = "投币需要先登录"
                            else if (aid <= 0) toast = "还没有稿件号，暂时不能投币"
                            else {
                                myCoins = null
                                coinOpen = true
                                scope.launch {
                                    myCoins = AppGraph.repo.userState().getOrNull()?.takeIf { it.loggedIn }?.coins
                                }
                            }
                        }
                        Act(Icons.Filled.Star, actLabel("收藏", detail?.favoriteCount ?: 0), Modifier.weight(1f), favored, favAccent) {
                            val aid = detail?.aid ?: 0L
                            if (!AppGraph.repo.session.value.loggedIn) toast = "收藏需要先登录"
                            else if (aid <= 0) toast = "还没有稿件号，暂时不能收藏"
                            else scope.launch {
                                AppGraph.repo.videoFavFolders(aid).onSuccess { folders ->
                                    if (folders.isEmpty()) {
                                        toast = "没有可用的收藏夹"
                                    } else {
                                        favSaved = folders.filter { it.favored }.map { it.id }.toSet()
                                        favDraft = folders
                                        favOpen = true
                                    }
                                }.onFailure { toast = it.message ?: "收藏夹加载失败" }
                            }
                        }
                        Act(Icons.Filled.Schedule, "稍后", Modifier.weight(1f), inLater, favAccent) {
                            val aid = detail?.aid ?: 0L
                            if (!AppGraph.repo.session.value.loggedIn) toast = "稍后再看需要先登录"
                            else if (aid <= 0) toast = "还没有稿件号，暂时不能加入稍后再看"
                            else scope.launch {
                                val target = !inLater
                                val result = if (target) AppGraph.repo.addWatchLater(aid) else AppGraph.repo.removeWatchLater(aid)
                                result.onSuccess {
                                    inLater = target
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    toast = if (target) "已加入稍后再看" else "已从稍后再看移出"
                                }.onFailure { toast = it.message ?: "稍后再看失败" }
                            }
                        }
                        Act(Icons.Filled.Share, "分享", Modifier.weight(1f)) {
                            val url = "https://www.bilibili.com/video/$bvid"
                            val message = shareText("分享", url)
                            if (message.isNotEmpty()) toast = message
                        }
                        Act(
                            Icons.Filled.Download,
                            if (downloading) "缓存中" else "缓存",
                            Modifier.weight(1f),
                            on = downloading,
                            active = MaterialTheme.colorScheme.primary,
                        ) {
                            val play = info ?: return@Act
                            val d = detail ?: return@Act
                            downloading = true
                            downloadProgress = -1f
                            var lastProgressAt = 0L
                            scope.launch {
                                runCatching {
                                    kotlinx.coroutines.withContext(app.ailiaili.core.model.ioDispatcher) {
                                        AppGraph.cache.download(
                                            d.bvid,
                                            d.title,
                                            d.pic,
                                            play,
                                            AppGraph.http,
                                        ) { done, total ->
                                            // 每 512KB 或结束时更新一次，避免每 64KB 触发一次重组。
                                            if (done - lastProgressAt >= 512 * 1024 || done == total) {
                                                lastProgressAt = done
                                                val ratio = if (total > 0) {
                                                    (done.toFloat() / total).coerceIn(0f, 1f)
                                                } else {
                                                    -1f
                                                }
                                                scope.launch { downloadProgress = ratio }
                                            }
                                        }
                                    }
                                    toast = "已缓存到离线缓存"
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                }.onFailure { toast = it.message ?: "缓存失败" }
                                downloading = false
                                downloadProgress = -1f
                            }
                        }
                    }
                }
                if (downloading) {
                    if (downloadProgress >= 0f) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                val pages = detail?.pages.orEmpty()
                if (pages.size > 1) {
                    Text("分P · ${pages.size}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pages.forEach { p ->
                            val on = page?.cid == p.cid
                            BiliFilterChip(
                                selected = on,
                                onClick = {
                                    page = p
                                    val id = detail?.bvid ?: bvid ?: return@BiliFilterChip
                                    start(id, p.cid, durationSec = p.durationSec)
                                },
                                label = {
                                    Text(
                                        p.part.ifBlank { "P${p.page}" },
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
                val episodes = detail?.episodes.orEmpty()
                val showingSeason = panel == "season" && episodes.size > 1
                val shown = when {
                    showingSeason -> "season"
                    panel == "comments" -> "comments"
                    else -> "intro"
                }
                BiliContentTabs(
                    labels = listOf("简介", "评论"),
                    selected = if (showingSeason) -1 else if (shown == "comments") 1 else 0,
                    onSelect = { panel = if (it == 0) "intro" else "comments" },
                    actionLabel = if (episodes.size > 1) "合集" else null,
                    actionOn = showingSeason,
                    onAction = { panel = if (panel == "season") "intro" else "season" },
                )
                when (shown) {
                    "intro" -> {
                        val ink = MaterialTheme.colorScheme.onSurface
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                tags.take(12).forEach { tag ->
                                    BiliAssistChip(
                                        label = tag,
                                        onClick = { onTag(tag) },
                                    )
                                }
                            }
                        }
                        val desc = detail?.desc.orEmpty()
                        val descLong = desc.length > 80 || desc.count { it == '\n' } >= 4
                        BiliSurfaceCard(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "简介",
                                    modifier = Modifier.weight(1f),
                                    color = ink,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (descLong) {
                                    Text(
                                        if (descOpen) "收起" else "展开",
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { descOpen = !descOpen }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            when {
                                desc.isBlank() -> Text(
                                    if (descReady) "没有简介" else "加载中…",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                )
                                else -> Text(
                                    desc,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    maxLines = if (descOpen) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (related.isNotEmpty()) {
                            BiliSectionTitle("相关推荐")
                            related.take(12).forEach { item ->
                                key(item.stableKey) {
                                    VideoListRow(
                                        title = item.title,
                                        cover = item.cover,
                                        onClick = { onOpenVideo(item) },
                                        coverWidth = 112.dp,
                                        badge = item.durationText,
                                        meta = {
                                            MetaRow(
                                                items = listOfNotNull(
                                                    item.author.takeIf { it.isNotBlank() }?.let {
                                                        MetaSpec(Icons.Filled.Person, it, MetricTone.Neutral)
                                                    },
                                                    formatCount(item.viewCount).takeIf { it.isNotBlank() }?.let {
                                                        MetaSpec(Icons.Filled.Visibility, "$it 播放", MetricTone.Online)
                                                    },
                                                ),
                                                maxItems = 2,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    "comments" -> {
                        val switchSort: (Int) -> Unit = { mode ->
                            if (mode != commentMode) {
                                commentMode = mode
                                replyExtra = emptyMap()
                                replyPage = emptyMap()
                                replyDone = emptySet()
                                detail?.aid?.let { loadComments(it, 1, mode, append = false) }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "评论",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (commentTotal > 0) {
                                BiliBadge(
                                    text = formatCount(commentTotal.toLong()).ifBlank { "$commentTotal" },
                                    tone = BiliBadgeTone.Neutral,
                                    style = BiliBadgeStyle.Soft,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            BiliFilterChip(
                                selected = commentMode == 3,
                                onClick = { switchSort(3) },
                                label = { Text("热门") },
                            )
                            BiliFilterChip(
                                selected = commentMode == 2,
                                onClick = { switchSort(2) },
                                label = { Text("最新") },
                            )
                        }
                        if (AppGraph.repo.session.value.loggedIn && (detail?.aid ?: 0) > 0) {
                            val replying = replyTarget
                            var sending by remember { mutableStateOf(false) }
                            val send: () -> Unit = send@{
                                val aid = detail?.aid ?: return@send
                                if (draft.isBlank() || sending) return@send
                                val root = replyRoot
                                val parent = replying?.rpid ?: 0L
                                sending = true
                                scope.launch {
                                    AppGraph.repo.postComment(aid, draft, root, parent).onSuccess {
                                        draft = ""
                                        replyTarget = null
                                        replyRoot = 0L
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        toast = if (root > 0) "已回复" else "已发送"
                                        loadComments(aid, 1, commentMode, append = false)
                                    }.onFailure { toast = it.message ?: "发送失败" }
                                    sending = false
                                }
                            }
                            CommentComposer(
                                draft = draft,
                                onDraft = { draft = it },
                                replyingTo = replying?.name?.ifBlank { "用户" },
                                expanded = composerExpanded,
                                sending = sending,
                                onToggleExpand = { composerExpanded = !composerExpanded },
                                onCancelReply = {
                                    replyTarget = null
                                    replyRoot = 0L
                                },
                                onFocus = { composing = it },
                                onSend = send,
                            )
                        } else if (!AppGraph.repo.session.value.loggedIn) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "登录后可以发评论、点赞和回复",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        val commentErr = commentError
                        if (commentErr != null && comments.isEmpty()) {
                            BiliErrorState(
                                message = commentErr,
                                onRetry = { detail?.aid?.let { loadComments(it, 1, commentMode, append = false) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (commentLoading && comments.isEmpty()) {
                            BiliLoadingState(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                label = "加载评论中…",
                            )
                        } else if (comments.isEmpty()) {
                            BiliEmptyState(
                                title = "还没有评论",
                                message = "来抢个沙发吧",
                                icon = Icons.AutoMirrored.Filled.Comment,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (commentErr != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    commentErr,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                )
                                TextButton(onClick = {
                                    detail?.aid?.let { loadComments(it, 1, commentMode, append = false) }
                                }) { Text("重试") }
                            }
                        }
                        comments.forEach { c ->
                            val shown = replyExtra[c.rpid] ?: c.replies
                            key(c.rpid) {
                            CommentBlock(
                                comment = c,
                                replies = shown,
                                rootRpid = c.rpid,
                                onSpace = onSpace,
                                onLike = { target -> reactComment(target, like = true) },
                                onDislike = { target -> reactComment(target, like = false) },
                                onReply = { root, target ->
                                    if (!AppGraph.repo.session.value.loggedIn) {
                                        toast = "请先登录后再回复"
                                    } else {
                                        replyRoot = root
                                        replyTarget = target
                                    }
                                },
                                onPreviewImage = { url, name -> commentImagePreview = url to name },
                                onExpand = if (c.replyCount > shown.size && c.rpid !in replyDone) {
                                    {
                                        val aid = detail?.aid ?: 0L
                                        val rootId = c.rpid
                                        if (aid > 0) scope.launch {
                                            val next = (replyPage[rootId] ?: 0) + 1
                                            AppGraph.repo.replies(aid, rootId, next).onSuccess { got ->
                                                val base = replyExtra[rootId] ?: comments.firstOrNull { it.rpid == rootId }?.replies.orEmpty()
                                                val merged = (base + got).distinctBy { it.rpid }
                                                replyExtra = replyExtra + (rootId to merged)
                                                replyPage = replyPage + (rootId to next)
                                                if (got.isEmpty() || merged.size >= c.replyCount) {
                                                    replyDone = replyDone + rootId
                                                }
                                            }.onFailure { commentError = it.message ?: "回复加载失败" }
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            }
                        }
                        if (commentsMore) {
                            FilledTonalButton(
                                onClick = {
                                    val aid = detail?.aid ?: return@FilledTonalButton
                                    loadComments(aid, commentPage + 1, commentMode, append = true)
                                },
                                enabled = !commentLoading,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                if (commentLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("加载中…")
                                } else {
                                    Text("更多评论")
                                }
                            }
                        }
                    }
                    "season" -> {
                        val accent = MaterialTheme.colorScheme.primary
                        val playingIndex = episodes.indexOfFirst { it.bvid == detail?.bvid }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                detail?.seasonTitle?.ifBlank { null } ?: "合集",
                                modifier = Modifier.weight(1f, fill = false),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            BiliBadge(
                                text = if (playingIndex >= 0) "${playingIndex + 1}/${episodes.size}" else "共 ${episodes.size} 集",
                                tone = BiliBadgeTone.Accent,
                                style = BiliBadgeStyle.Soft,
                            )
                        }
                        var lastSection = ""
                        episodes.forEachIndexed { position, ep ->
                            if (ep.section.isNotBlank() && ep.section != lastSection) {
                                lastSection = ep.section
                                BiliSectionTitle(ep.section, Modifier.padding(top = 4.dp))
                            }
                            val playing = ep.bvid == detail?.bvid
                            key(ep.bvid, ep.cid) {
                                VideoListRow(
                                    title = ep.title,
                                    cover = ep.cover,
                                    onClick = { if (!playing) openEpisode(ep) },
                                    selected = playing,
                                    coverWidth = 112.dp,
                                    leading = {
                                        Text(
                                            (if (ep.index > 0) ep.index else position + 1).toString(),
                                            modifier = Modifier.width(24.dp),
                                            color = if (playing) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                                        )
                                    },
                                    meta = if (playing) {
                                        {
                                            BiliBadge(
                                                text = "正在播放",
                                                tone = BiliBadgeTone.Accent,
                                                style = BiliBadgeStyle.Solid,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        when {
            fullscreen -> Stage(Modifier.weight(1f).fillMaxWidth())
            splitPlayer -> Row(Modifier.weight(1f).fillMaxSize()) {
                Stage(Modifier.weight(1.2f).fillMaxHeight())
                if (detailsReady) Details(Modifier.weight(0.8f).fillMaxHeight())
                else Spacer(Modifier.weight(0.8f).fillMaxHeight())
            }
            else -> {
                Stage(Modifier.fillMaxWidth())
                if (detailsReady) Details(Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
        if (coinOpen) {
            val balance = myCoins
            val canOne = balance != null && balance >= 1.0
            val canTwo = balance != null && balance >= 2.0 && !coined
            AlertDialog(
                onDismissRequest = { coinOpen = false },
                title = { Text("给这个视频投币") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricChip(
                                MetricSpec(
                                    Icons.Filled.MonetizationOn,
                                    if (balance == null) "…" else coinAmount(balance),
                                    "我的硬币",
                                    MetricTone.Coin,
                                ),
                                size = MetricChipSize.Compact,
                            )
                            MetricChip(
                                MetricSpec(
                                    Icons.Filled.ThumbUp,
                                    formatCount(detail?.coinCount ?: 0).ifBlank { "0" },
                                    "已投",
                                    MetricTone.Coin,
                                ),
                                size = MetricChipSize.Compact,
                            )
                        }
                        Text(
                            if (coined) "你已经投过这个视频，还可以再投 1 枚，同一个视频最多 2 枚。"
                            else "投币会消耗你的硬币，同一个视频最多投 2 枚。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { giveCoins(1) },
                                enabled = canOne,
                                modifier = Modifier.weight(1f),
                            ) { Text("投 1 枚") }
                            FilledTonalButton(
                                onClick = { giveCoins(2) },
                                enabled = canTwo,
                                modifier = Modifier.weight(1f),
                            ) { Text("投 2 枚") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { coinOpen = false }) { Text("取消") }
                },
            )
        }
        if (favOpen) {
            AlertDialog(
                onDismissRequest = { favOpen = false },
                title = { Text("选择收藏夹") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (favDraft.isEmpty()) {
                            Text("没有可用的收藏夹")
                        }
                        favDraft.forEach { folder ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        val on = !folder.favored
                                        favDraft = favDraft.map { item ->
                                            if (item.id == folder.id) item.copy(favored = on) else item
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = folder.favored,
                                    onCheckedChange = { on ->
                                        favDraft = favDraft.map { item ->
                                            if (item.id == folder.id) item.copy(favored = on) else item
                                        }
                                    },
                                )
                                Text(
                                    folder.title.ifBlank { "收藏夹" },
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (folder.privateFolder) {
                                    BiliBadge(
                                        text = "私密",
                                        tone = BiliBadgeTone.Warning,
                                        style = BiliBadgeStyle.Soft,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val aid = detail?.aid ?: 0L
                        val after = favDraft.filter { it.favored }.map { it.id }.toSet()
                        val add = after - favSaved
                        val remove = favSaved - after
                        if (aid <= 0 || (add.isEmpty() && remove.isEmpty())) {
                            favOpen = false
                            return@TextButton
                        }
                        scope.launch {
                            AppGraph.repo.dealFavorite(aid, add, remove).onSuccess {
                                val was = favSaved.isNotEmpty()
                                val now = after.isNotEmpty()
                                favored = now
                                favSaved = after
                                if (was != now) {
                                    detail = detail?.copy(
                                        favoriteCount = ((detail?.favoriteCount ?: 0) + if (now) 1 else -1).coerceAtLeast(0),
                                    )
                                }
                                toast = if (now) "已更新收藏" else "已取消收藏"
                                favOpen = false
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }.onFailure { toast = it.message ?: "收藏失败" }
                        }
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { favOpen = false }) { Text("取消") }
                },
            )
        }
    }
}

private fun mergeDanmaku(current: List<Danmaku>, extra: List<Danmaku>): List<Danmaku> {
    if (extra.isEmpty()) return current
    if (current.isEmpty()) return extra.sortedBy { it.timeSec }
    val last = current.last().timeSec
    val past = extra.any { it.timeSec < last }
    if (!past) return current + extra.filter { it.timeSec >= last }.sortedBy { it.timeSec }
    return (current + extra).sortedBy { it.timeSec }
}

private fun fallbackDetail(
    bvid: String,
    cid: Long,
    aid: Long,
    title: String,
    cover: String,
    author: String,
    ownerMid: Long,
    durationSec: Int,
    pages: List<VideoPage>,
    ownerFace: String = "",
    viewCount: Long = 0,
    likeCount: Long = 0,
    danmakuCount: Long = 0,
    coinCount: Long = 0,
    favoriteCount: Long = 0,
    pubdate: Long = 0,
): VideoDetail = VideoDetail(
    bvid = bvid,
    aid = aid,
    cid = cid,
    title = title.ifBlank { "播放" },
    pic = cover,
    owner = author,
    ownerMid = ownerMid,
    ownerFace = ownerFace,
    desc = "",
    viewCount = viewCount,
    likeCount = likeCount,
    danmakuCount = danmakuCount,
    coinCount = coinCount,
    favoriteCount = favoriteCount,
    shareCount = 0,
    durationSec = durationSec,
    pages = pages.ifEmpty { listOf(VideoPage(cid, 1, title.ifBlank { "P1" }, durationSec)) },
    pubdate = pubdate,
)

private fun coinAmount(value: Double): String {
    val whole = value.toLong().toDouble()
    return if (kotlin.math.abs(value - whole) < 0.05) value.toLong().toString() else {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        rounded.toString()
    }
}

private fun clock(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

private fun speedLabel(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}x" else "${value}x"

@Composable
private fun VideoHeader(
    title: String,
    detail: VideoDetail?,
    onlineText: String?,
) {
    BiliSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                ),
            )
        }
        if (detail != null) {
            val d = detail
            val watching = onlineText?.takeIf { it.isNotBlank() }?.let { raw ->
                raw.trim()
                    .removeSuffix("人在看")
                    .removeSuffix("人")
                    .trim()
                    .ifBlank { raw.trim() }
            }
            val metrics = remember(d, onlineText) {
                buildList {
                    add(
                        MetricSpec(
                            Icons.Filled.PlayArrow,
                            formatCount(d.viewCount).ifBlank { "0" },
                            "播放",
                            MetricTone.Play,
                        ),
                    )
                    add(
                        MetricSpec(
                            Icons.AutoMirrored.Filled.Comment,
                            formatCount(d.danmakuCount).ifBlank { "0" },
                            "弹幕",
                            MetricTone.Danmaku,
                        ),
                    )
                    add(
                        MetricSpec(
                            Icons.Filled.ThumbUp,
                            formatCount(d.likeCount).ifBlank { "0" },
                            "点赞",
                            MetricTone.Like,
                        ),
                    )
                    add(
                        MetricSpec(
                            Icons.Filled.MonetizationOn,
                            formatCount(d.coinCount).ifBlank { "0" },
                            "投币",
                            MetricTone.Coin,
                        ),
                    )
                    add(
                        MetricSpec(
                            Icons.Filled.Star,
                            formatCount(d.favoriteCount).ifBlank { "0" },
                            "收藏",
                            MetricTone.Favorite,
                        ),
                    )
                    if (!watching.isNullOrBlank()) {
                        add(
                            MetricSpec(
                                Icons.Filled.Visibility,
                                watching,
                                "人在看",
                                MetricTone.Online,
                            ),
                        )
                    }
                    val day = formatDay(d.pubdate)
                    if (day.isNotBlank()) {
                        add(
                            MetricSpec(
                                Icons.Filled.CalendarMonth,
                                day,
                                "发布",
                                MetricTone.Date,
                            ),
                        )
                    }
                }
            }
            MetricFlow(metrics)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentBlock(
    comment: Comment,
    replies: List<Comment>,
    rootRpid: Long,
    onSpace: (Long) -> Unit,
    onLike: (Comment) -> Unit,
    onDislike: (Comment) -> Unit,
    onReply: (Long, Comment) -> Unit,
    onExpand: (() -> Unit)?,
    onPreviewImage: (String, String) -> Unit,
    nested: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.padding(vertical = if (nested) 4.dp else 8.dp)) {
        if (comment.face.isNotBlank()) {
            AsyncImage(
                model = comment.face,
                contentDescription = comment.name,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(if (nested) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(if (nested) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                contentAlignment = Alignment.Center,
            ) {
                Text(comment.name.take(1).ifBlank { "用" }, color = accent, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (comment.pinned) {
                    BiliBadge(
                        text = "置顶",
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
                Text(
                    comment.name.ifBlank { "用户" },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                    color = if (comment.up) accent else ink,
                    fontSize = if (nested) 13.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.level > 0) {
                    BiliBadge(
                        text = "LV${comment.level}",
                        tone = BiliBadgeTone.Neutral,
                        style = BiliBadgeStyle.Soft,
                    )
                }
                if (comment.up) {
                    BiliBadge(
                        text = "UP",
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
            }
            MetaRow(
                items = listOfNotNull(
                    comment.timeText.takeIf { it.isNotBlank() }?.let {
                        MetaSpec(Icons.Filled.Schedule, it, MetricTone.Neutral)
                    },
                    comment.location.takeIf { it.isNotBlank() }?.let {
                        MetaSpec(Icons.Filled.LocationOn, it, MetricTone.Neutral)
                    },
                ),
                maxItems = 2,
            )
            if (comment.message.isNotBlank()) {
                SelectionContainer {
                    CommentRichText(
                        comment = comment,
                        modifier = Modifier.padding(top = 4.dp),
                        color = ink,
                        accent = accent,
                    )
                }
            }
            if (comment.pictures.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    comment.pictures.forEach { picture ->
                        val height = 140.dp
                        val width = if (picture.width > 0 && picture.height > 0) {
                            (height * (picture.width.toFloat() / picture.height))
                                .coerceIn(60.dp, 240.dp)
                        } else {
                            height
                        }
                        AsyncImage(
                            model = picture.url,
                            contentDescription = "评论图片",
                            modifier = Modifier
                                .size(width, height)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onPreviewImage(
                                        picture.url,
                                        comment.name.ifBlank { "评论图片" },
                                    )
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Row(
                Modifier.padding(top = 4.dp).offset(x = (-6).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommentAction(
                    Icons.Filled.ThumbUp,
                    formatCount(comment.like.toLong()),
                    comment.liked,
                    description = "点赞",
                ) { onLike(comment) }
                CommentAction(Icons.Filled.ThumbDown, "", comment.disliked, description = "点踩") { onDislike(comment) }
                CommentAction(Icons.AutoMirrored.Filled.Reply, "回复", false) { onReply(rootRpid, comment) }
            }
            if (!nested && (replies.isNotEmpty() || onExpand != null)) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    replies.forEach { reply ->
                        key(reply.rpid) {
                            CommentBlock(
                                reply,
                                emptyList(),
                                rootRpid,
                                onSpace,
                                onLike,
                                onDislike,
                                onReply,
                                onExpand = null,
                                onPreviewImage = onPreviewImage,
                                nested = true,
                            )
                        }
                    }
                    if (onExpand != null) {
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onExpand)
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "查看 ${comment.replyCount} 条回复",
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAction(
    icon: ImageVector,
    label: String,
    on: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "commentActionTint",
    )
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = description ?: label.ifBlank { null },
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (label.isNotEmpty()) {
            Text(label, modifier = Modifier.padding(start = 4.dp), color = tint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Act(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    active: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    BiliActionItem(
        icon = icon,
        label = label,
        modifier = modifier,
        active = on,
        accent = active,
        onClick = onClick,
    )
}

/** 竖向操作下方只放计数；为 0 时回退成动作名。 */
private fun actLabel(name: String, n: Long): String = formatCount(n).ifBlank { name }

@Composable
private fun CommentComposer(
    draft: String,
    onDraft: (String) -> Unit,
    replyingTo: String?,
    expanded: Boolean,
    sending: Boolean,
    onToggleExpand: () -> Unit,
    onCancelReply: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = replyingTo != null) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "回复 @${replyingTo.orEmpty()}",
                    modifier = Modifier.padding(start = 6.dp),
                    color = scheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onCancelReply, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "取消回复",
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocus(it.isFocused) },
                placeholder = { Text(if (replyingTo == null) "发一条友善的评论" else "回复 @$replyingTo") },
                shape = MaterialTheme.shapes.medium,
                minLines = if (expanded) 5 else 1,
                maxLines = if (expanded) 12 else 4,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                        contentDescription = if (expanded) "收起输入框" else "展开输入框",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (sending) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (replyingTo == null) "发送" else "回复",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}


