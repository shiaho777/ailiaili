package app.ailiaili.feature.player

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import app.ailiaili.core.designsystem.AliStageDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import app.ailiaili.core.designsystem.AliStageMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import app.ailiaili.core.designsystem.AliColors
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.AliStageSeekBar
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.aliPlaybackAccent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.CachedPlayback
import app.ailiaili.core.data.readCachedPlayback
import androidx.compose.runtime.produceState
import app.ailiaili.core.data.HistorySync
import app.ailiaili.core.data.ResumePlayback
import app.ailiaili.core.data.canControlBrightness
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.data.setFullscreen
import app.ailiaili.core.designsystem.BiliActionItem
import app.ailiaili.core.designsystem.BiliAssistChip
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.LinkifiedText
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
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricFlow
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.metricToneColor
import app.ailiaili.core.designsystem.smoothWheelScroll
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
import app.ailiaili.core.model.videoMomentUrl
import app.ailiaili.core.model.ViewChapter
import app.ailiaili.core.model.epochMs
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.model.formatDay
import app.ailiaili.core.model.qnLabel
import app.ailiaili.core.model.LivePlayInfo
import app.ailiaili.core.network.LiveDanmaku
import app.ailiaili.core.model.LiveRoomInfo
import app.ailiaili.core.player.PlaybackListener
import app.ailiaili.core.player.PlayerEngine
import app.ailiaili.core.model.PlayerScale
import androidx.compose.material.icons.filled.Lock
import app.ailiaili.core.player.VideoSurface
import coil3.compose.AsyncImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private const val ChromeHideDelayMs = 3500L
private val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)

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
    /** 番剧集 id：>0 时取流优先走 PGC 链路，失败回退 UGC。 */
    seedEpId: Long = 0,
    /** 列表「播放全部」带入的顺序连播队列。 */
    queue: List<app.ailiaili.core.model.QueueItem> = emptyList(),
    queueIndex: Int = 0,
    startAtMs: Long? = null,
    startPage: Int? = null,
    onOpenVideo: (VideoItem) -> Unit = {},
    onTag: (String) -> Unit = {},
    onOpenCommentDetail: (aid: Long, comment: Comment) -> Unit = { _, _ -> },
    onOpenDownloads: () -> Unit = {},
    onOpenOriginal: ((Long) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val engine = remember {
        val settings = AppGraph.repo.settings.value
        PlayerEngine().apply {
            configureCompatibility(settings.desktopHardwareDecoding, settings.desktopHighQualityAudio)
        }
    }
    fun leavePlayer() {
        engine.freeze()
        onBack()
    }
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var page by remember { mutableStateOf<VideoPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<PlayInfo?>(null) }
    // 引擎实际起播的媒体身份。进度只写给这套身份，且要 engine.mediaTag 仍一致：
    // 换片瞬间 detail/page 已切新片而引擎还在播旧片，不能拿 detail 当进度归属。
    var startedBvid by remember { mutableStateOf<String?>(null) }
    var startedCid by remember { mutableLongStateOf(0L) }
    var startedTag by remember { mutableStateOf("") }
    var startedResume by remember { mutableStateOf<ResumePlayback?>(null) }
    var livePlayInfo by remember { mutableStateOf<LivePlayInfo?>(null) }
    var liveRoomInfo by remember { mutableStateOf<LiveRoomInfo?>(null) }
    var liveChat by remember { mutableStateOf<List<LiveDanmaku>>(emptyList()) }
    var listening by remember { mutableStateOf(false) }
    var listenSwitching by remember { mutableStateOf(false) }
    var listenClock by remember { mutableStateOf("00:00") }
    var avatarPreview by remember { mutableStateOf<String?>(null) }
    var commentImagePreview by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }
    var danmaku by remember { mutableStateOf<List<Danmaku>>(emptyList()) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var commentPage by remember { mutableIntStateOf(1) }
    var commentsMore by remember { mutableStateOf(false) }
    var commentTotal by remember { mutableIntStateOf(0) }
    var commentMode by remember { mutableIntStateOf(3) }
    // 只看 UP 主：本地过滤已加载评论（接口 up 标记），不改变排序与分页。
    var onlyUpComments by remember { mutableStateOf(false) }
    var commentLoading by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
    var commentCooldownUntil by remember { mutableLongStateOf(0L) }
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
    var pointerOverChrome by remember { mutableStateOf(false) }
    val pointerClock = remember { TimeSource.Monotonic.markNow() }
    var lastPointerMotion by remember { mutableLongStateOf(-ChromeHideDelayMs) }
    var hint by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    val settings by AppGraph.repo.settings.collectAsState()
    val keyboardSeekMs = (settings.seekStepSeconds.takeIf { it > 0 } ?: 5) * 1000L
    val gestureSeekSeconds = settings.seekStepSeconds.takeIf { it > 0 } ?: 10
    val reducedMotion = LocalAliReducedMotion.current
    val clipboard = LocalClipboardManager.current
    var volume by remember { mutableFloatStateOf(settings.playerVolume.coerceIn(0f, 1f)) }
    fun setVolume(next: Float) {
        val v = next.coerceIn(0f, 1f)
        volume = v
        engine.volume = v
        AppGraph.repo.updateSettings {
            it.copy(playerVolume = v, lastAudiblePlayerVolume = if (v > 0f) v else it.lastAudiblePlayerVolume)
        }
    }
    fun toggleMute() {
        val muted = volume > 0f
        setVolume(if (muted) 0f else AppGraph.repo.settings.value.lastAudiblePlayerVolume)
        hint = if (muted) "已静音" else "音量 ${(volume * 100).roundToInt()}%"
    }
    LaunchedEffect(Unit) { engine.volume = volume }
    var volumeOpen by remember { mutableStateOf(false) }
    var areaOpen by remember { mutableStateOf(false) }
    var areaDraft by remember { mutableFloatStateOf(25f) }
    var undoAt by remember { mutableLongStateOf(-1L) }
    val skippedState = remember { mutableStateOf(setOf<Double>()) }
    var skippedStarts by skippedState
    var baseSpeed by remember { mutableFloatStateOf(settings.playbackSpeed.coerceIn(0.25f, 4f)) }
    var rawSegments by remember { mutableStateOf<List<SponsorSegment>>(emptyList()) }
    var chapters by remember { mutableStateOf<List<ViewChapter>>(emptyList()) }
    var chaptersOpen by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<VideoShot?>(null) }
    // 播放/暂停的中央图标浮现：淡入放大后自动淡出，纯视觉反馈不打断手势。
    var playFlash by remember { mutableStateOf<FlashKind?>(null) }
    var playFlashTick by remember { mutableIntStateOf(0) }
    // 双击左右区的 ±10 秒反馈：-1 左 / 1 右，与中央浮现共用一套动画。
    var seekFlashSide by remember { mutableIntStateOf(0) }
    LaunchedEffect(reducedMotion, playFlashTick) {
        if (reducedMotion) {
            playFlash = null
            seekFlashSide = 0
        }
    }
    var subtitleTracks by remember { mutableStateOf<List<app.ailiaili.core.model.SubtitleTrack>>(emptyList()) }
    var subtitleCues by remember { mutableStateOf<List<app.ailiaili.core.model.SubtitleCue>>(emptyList()) }
    var subtitleLan by remember { mutableStateOf("") }
    var subtitleOpen by remember { mutableStateOf(false) }
    val cachedPlayback by produceState(CachedPlayback(), local?.videoPath) {
        value = if (local == null) CachedPlayback() else withContext(ioDispatcher) { readCachedPlayback(local) }
    }
    val subtitleChoices = if (local == null) subtitleTracks else cachedPlayback.subtitles.map { it.track }
    val selectedCachedSubtitle = cachedPlayback.subtitles.firstOrNull { it.track.lan == settings.subtitleLan }
        ?: cachedPlayback.subtitles.firstOrNull { it.track.lan.startsWith("zh") }
        ?: cachedPlayback.subtitles.firstOrNull()
    var followed by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var coined by remember { mutableStateOf(false) }
    var coinOpen by remember { mutableStateOf(false) }
    var myCoins by remember { mutableStateOf<Double?>(null) }
    var favored by remember { mutableStateOf(false) }
    var inLater by remember { mutableStateOf(false) }
    var favOpen by remember { mutableStateOf(false) }
    var createFolderOpen by remember { mutableStateOf(false) }
    var favDraft by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var favSaved by remember { mutableStateOf(setOf<Long>()) }
    var replyRoot by remember { mutableLongStateOf(0L) }
    var replyTarget by remember { mutableStateOf<Comment?>(null) }
    var reacting by remember { mutableStateOf(setOf<Long>()) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    // seek 落点确认期间 VLC 会短暂停住；这段时间不要误判成网络缓冲弹「正在缓冲」。
    var seekGraceUntil by remember { mutableLongStateOf(0L) }
    fun seekTo(ms: Long) {
        seekGraceUntil = epochMs() + 1_800
        engine.seekTo(ms)
    }
    var qualityOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var audioOpen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(settings.playbackSpeed.coerceIn(0.25f, 4f)) }
    var speedDraft by remember { mutableFloatStateOf(settings.playbackSpeed.coerceIn(0.25f, 4f)) }
    var rightHeld by remember { mutableStateOf(false) }
    var rightBoost by remember { mutableStateOf(false) }
    var temporarySpeedIndex by remember { mutableIntStateOf(2) }
    val temporarySpeeds = remember { listOf(1.5f, 2f, 3f) }
    fun adjustTemporarySpeed(direction: Int) {
        temporarySpeedIndex = (temporarySpeedIndex + direction + temporarySpeeds.size) % temporarySpeeds.size
        speed = temporarySpeeds[temporarySpeedIndex]
        hint = "${speedLabel(speed)} 快进中"
    }
    LaunchedEffect(rightHeld) {
        if (!rightHeld) {
            if (rightBoost) {
                rightBoost = false
                speed = baseSpeed
                hint = null
            }
            return@LaunchedEffect
        }
        delay(450)
        if (rightHeld && liveRoom == null) {
            rightBoost = true
            speed = temporarySpeeds[temporarySpeedIndex]
            hint = "3x 快进中，松开恢复"
        }
    }
    var fullscreen by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var scaleOpen by remember { mutableStateOf(false) }
    fun unlockControls() {
        controlsLocked = false
        chrome = true
    }
    LaunchedEffect(fullscreen) {
        if (!fullscreen && controlsLocked) unlockControls()
    }
    var commentsFullscreen by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var playbackOptionsOpen by remember { mutableStateOf(false) }
    var playbackInfoOpen by remember { mutableStateOf(false) }
    var danmakuTimingOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var composerExpanded by remember { mutableStateOf(false) }
    // 弹幕发送：输入条开合、草稿、类型/颜色、在途标记。
    var dmBarOpen by remember { mutableStateOf(false) }
    var clearScreen by remember { mutableStateOf(false) }
    fun toggleClearScreen() {
        clearScreen = !clearScreen
        if (clearScreen) {
            dmBarOpen = false
            areaOpen = false
        }
        toast = if (clearScreen) "清屏已开启 · 保留字幕" else "已退出清屏"
    }
    fun toggleDanmaku() {
        if (clearScreen) toggleClearScreen()
        else AppGraph.repo.updateSettings { it.copy(danmakuEnabled = !it.danmakuEnabled) }
    }
    var dmDraft by remember { mutableStateOf("") }
    var dmMode by remember { mutableIntStateOf(1) }
    var dmColor by remember { mutableIntStateOf(0xFFFFFF) }
    var dmSending by remember { mutableStateOf(false) }
    // 本地发送成功的弹幕单独维护：分段轮询重建列表时并回去，不被网络数据冲掉。
    var localDanmaku by remember { mutableStateOf<List<Danmaku>>(emptyList()) }
    // 直播间发言：草稿、在途标记、本人昵称（回显用，懒取一次）。
    var liveMsgDraft by remember { mutableStateOf("") }
    var liveMsgSending by remember { mutableStateOf(false) }
    var myLiveName by remember { mutableStateOf("") }
    // 睡眠定时器：-1 关闭；0 播完当前；>0 分钟后暂停。页面级状态，离开播放页即失效。
    var sleepOpen by remember { mutableStateOf(false) }
    var sleepMinutes by remember { mutableIntStateOf(-1) }
    var sleepDeadline by remember { mutableLongStateOf(0L) }
    var sleepRemainText by remember { mutableStateOf("") }
    LaunchedEffect(sleepMinutes, sleepDeadline, ended) {
        if (sleepMinutes < 0) return@LaunchedEffect
        while (true) {
            val now = epochMs()
            if (app.ailiaili.core.model.sleepTimerDue(sleepMinutes, true, sleepDeadline, now, ended, liveRoom != null)) {
                engine.pause()
                sleepMinutes = -1
                sleepDeadline = 0L
                sleepRemainText = ""
                toast = "定时时间到，已暂停播放"
                return@LaunchedEffect
            }
            sleepRemainText = if (sleepMinutes == 0) "播完当前" else app.ailiaili.core.model.sleepTimerClock(sleepDeadline - now)
            delay(1_000)
        }
    }
    var cacheOpen by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var liveRetries by remember { mutableIntStateOf(0) }
    var refetchRetries by remember { mutableIntStateOf(0) }
    var refetchRequest by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    // 当前媒体的番剧集号（PGC 取流用）；换片时随 start 更新。
    var currentEpId by remember { mutableLongStateOf(seedEpId) }
    // 顺序连播队列：列表页带入；curQueueIndex 指向正在播的条目。
    val playQueue = remember { queue }
    var curQueueIndex by remember { mutableIntStateOf(queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))) }
    var queuePanelOpen by remember { mutableStateOf(false) }
    var nextPlayback by remember { mutableStateOf<NextPlayback?>(null) }
    var nextSeconds by remember { mutableIntStateOf(5) }
    var advanceNow by remember { mutableStateOf(false) }
    var relatedQueue by remember { mutableStateOf<List<VideoItem>?>(null) }
    var relatedIndex by remember { mutableIntStateOf(-1) }
    var visitedVideos by remember { mutableStateOf(setOf<String>()) }
    val seasonQueue = remember(playQueue) { playQueue.isNotEmpty() && playQueue.all { it.epId > 0 } }
    // A-B 循环：-1 未设置；两点齐备后播放到 B 自动回 A。换片清除。
    var abA by remember { mutableLongStateOf(-1L) }
    var abB by remember { mutableLongStateOf(-1L) }
    var abOpen by remember { mutableStateOf(false) }
    val abAState = rememberUpdatedState(abA)
    val abBState = rememberUpdatedState(abB)
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
        if (engine.isSeekPending) return
        val aid = reportAid.value
        val cid = reportCid.value
        val id = reportBvid.value
        if (cid != startedCid || id != startedBvid || engine.mediaTag != startedTag) return
        val sec = runCatching { engine.currentPosition / 1000 }.getOrDefault(0L)
        scope.launch(ioDispatcher) {
            if (now) HistorySync.reportNow(aid, cid, sec, id)
            else HistorySync.report(aid, cid, sec, id)
        }
    }
    fun rememberProgress(position: Long, finished: Boolean = false) {
        val id = startedBvid ?: return
        val cid = startedCid
        if (cid <= 0 || engine.isSeekPending || engine.mediaTag != startedTag) return
        val recent = startedResume?.let { playback ->
            val duration = playback.durationMs.takeIf { it > 0 } ?: engine.duration
            playback.copy(
                positionMs = if (finished) duration else position / 1_000 * 1_000,
                durationMs = duration,
            )
        }
        val savedPosition = if (finished && recent != null) maxOf(position, recent.durationMs) else position
        AppGraph.playbackMemory.write(id, cid, savedPosition, recent)
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

    /** 长按点赞：点赞 → 投币（2 枚）→ 收藏到默认收藏夹。逐项独立执行，失败不拖累其他项。 */
    fun tripleCombo() {
        val id = detail?.bvid ?: bvid
        val aid = detail?.aid ?: seedAid
        if (id.isNullOrBlank() || aid <= 0) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "一键三连需要先登录"
            return
        }
        scope.launch {
            val results = mutableListOf<String>()
            if (!liked) {
                AppGraph.repo.like(id, true).onSuccess {
                    liked = true
                    detail = detail?.copy(likeCount = (detail?.likeCount ?: 0) + 1)
                    results += "点赞"
                }.onFailure { results += "点赞失败" }
            }
            if (!coined) {
                AppGraph.repo.coin(aid, id, 2).onSuccess {
                    coined = true
                    detail = detail?.copy(coinCount = (detail?.coinCount ?: 0) + 2, coined = true)
                    myCoins = myCoins?.let { (it - 2).coerceAtLeast(0.0) }
                    results += "投币"
                }.onFailure { results += "投币失败" }
            }
            if (!favored) {
                val folders = AppGraph.repo.videoFavFolders(aid).getOrNull().orEmpty()
                val target = folders.firstOrNull { !it.privateFolder } ?: folders.firstOrNull()
                if (target == null) {
                    results += "没有可用收藏夹"
                } else {
                    AppGraph.repo.dealFavorite(aid, setOf(target.id), emptySet()).onSuccess {
                        favored = true
                        favSaved = favSaved + target.id
                        detail = detail?.copy(favoriteCount = (detail?.favoriteCount ?: 0) + 1)
                        results += "收藏"
                    }.onFailure { results += "收藏失败" }
                }
            }
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            toast = when {
                results.isEmpty() -> "已赞过、投过、收藏过了"
                results.all { !it.endsWith("失败") && it != "没有可用收藏夹" } -> "已${results.joinToString("、")}，三连完成"
                else -> results.joinToString("，")
            }
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
                playing = false
                val pos = engine.currentPosition
                if (runCatching { engine.failOver(pos) }.getOrDefault(false)) return
                // 直播没有备用线路：断流后重新取流地址（CDN 可能已切换），
                // 最多自动重连 3 次，仍失败才交给错误页的手动重试。
                if (liveRoom != null && liveRetries < 3) {
                    liveRetries += 1
                    toast = "直播断流，正在重连（$liveRetries/3）"
                    attempt += 1
                    return
                }
                // 点播：playurl 链接有效期短，长暂停/慢速播放后会过期
                // （PiliPlus #2711 同款）。备用线路用尽后重新取流续播，
                // 最多 2 次；换片/手动重试会复位计数。
                val id = detail?.bvid ?: bvid
                val cidNow = page?.cid ?: detail?.cid ?: 0L
                if (liveRoom == null && local == null && !id.isNullOrBlank() && cidNow > 0 && refetchRetries < 2) {
                    refetchRetries += 1
                    toast = "播放链接可能已过期，正在重新取流（$refetchRetries/2）"
                    refetchRequest = Triple(id, cidNow, pos)
                    return
                }
                error = "播放失败，线路都不可用"
            }

            override fun onEnded(endedNow: Boolean) {
                if (endedNow && ended) return
                ended = endedNow
                if (!endedNow) {
                    nextPlayback = null
                    return
                }
                playing = false
                rememberProgress(engine.currentPosition, finished = true)
                fireHistory(now = true)
                val currentSettings = AppGraph.repo.settings.value
                if (!currentSettings.autoPlayNext || sleepMinutes == 0 || liveRoom != null || local != null) return
                val current = detail ?: return
                val partIndex = current.pages.indexOfFirst { it.cid == (page?.cid ?: current.cid) }
                val nextPart = if (partIndex >= 0) current.pages.getOrNull(partIndex + 1) else null
                val queueItem = playQueue.getOrNull(curQueueIndex + 1)
                val episodeIndex = current.episodes.indexOfFirst { it.bvid == current.bvid }
                val nextEpisode = if (episodeIndex >= 0) current.episodes.getOrNull(episodeIndex + 1) else null
                val relatedItems = relatedQueue.orEmpty()
                val recommendedIndex = if (currentSettings.autoPlayRelated) {
                    relatedItems.indices.firstOrNull { index ->
                        val video = relatedItems[index]
                        index > relatedIndex && video.bvid !in visitedVideos &&
                            app.ailiaili.core.designsystem.ContentFilter.allows(video)
                    }
                } else {
                    null
                }
                nextSeconds = 5
                advanceNow = false
                nextPlayback = when {
                    nextPart != null -> NextPlayback.Part(nextPart)
                    queueItem != null -> NextPlayback.Queue(curQueueIndex + 1, queueItem)
                    nextEpisode != null -> NextPlayback.Collection(nextEpisode)
                    recommendedIndex != null -> NextPlayback.Related(recommendedIndex, relatedItems[recommendedIndex])
                    else -> null
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) {
                    ended = false
                    nextPlayback = null
                } else {
                    fireHistory(now = true)
                }
            }
        }
        engine.addListener(listener)
        onDispose {
            runCatching { engine.currentPosition }.getOrNull()?.let { rememberProgress(it, finished = ended) }
            AppGraph.playbackMemory.flush()
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
        if (!fullscreen) commentsFullscreen = false
        setFullscreen(fullscreen)
        onDispose { setFullscreen(false) }
    }

    // 画中画：登记「正在播放」查询，Home 键由 Activity 自动进小窗；
    // 小窗期间返回键不弹页（引擎保持存活），退出小窗回到全屏播放页。
    val pipOk = app.ailiaili.core.data.pipSupported()
    DisposableEffect(engine, pipOk) {
        if (pipOk) app.ailiaili.core.data.registerPipPlayback { engine.isPlaying }
        onDispose { if (pipOk) app.ailiaili.core.data.registerPipPlayback(null) }
    }

    BackHandler {
        when {
            app.ailiaili.core.data.inPip() -> Unit
            controlsLocked -> unlockControls()
            commentsFullscreen -> commentsFullscreen = false
            fullscreen -> fullscreen = false
            else -> leavePlayer()
        }
    }

    fun playingCid(): Long = page?.cid?.takeIf { it > 0 } ?: detail?.cid ?: 0L

    fun sendDanmakuNow() {
        if (dmSending) return
        val text = dmDraft.trim()
        if (text.isEmpty()) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "请先登录后再发弹幕"
            return
        }
        val aid = detail?.aid ?: seedAid
        val cid = playingCid()
        if (aid <= 0 || cid <= 0) {
            toast = "视频信息还没就绪，稍后再发"
            return
        }
        val posMs = runCatching { engine.currentPosition }.getOrDefault(0L)
        val mode = dmMode
        val color = dmColor
        dmSending = true
        scope.launch {
            AppGraph.repo.sendDanmaku(aid, cid, text, posMs, mode, color)
                .onSuccess {
                    dmDraft = ""
                    // 本地回显：时间取当前播放位置；若早于列表末尾则贴到末尾，
                    // 保证 Runtime 走追加路径不清屏（暂停时发送也能立刻看到）。
                    val sec = posMs / 1000.0
                    val tail = danmaku.lastOrNull()?.timeSec ?: 0.0
                    val echo = Danmaku(maxOf(sec, tail), text, mode, color)
                    localDanmaku = localDanmaku + echo
                    danmaku = (danmaku + echo).sortedBy { it.timeSec }
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    toast = "弹幕已发送"
                }
                .onFailure { toast = it.message ?: "弹幕发送失败" }
            dmSending = false
        }
    }

    fun sendLiveMsgNow() {
        val room = liveRoom ?: return
        if (liveMsgSending) return
        val text = liveMsgDraft.trim()
        if (text.isEmpty()) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "请先登录后再发言"
            return
        }
        liveMsgSending = true
        scope.launch {
            if (myLiveName.isBlank()) {
                myLiveName = AppGraph.repo.userState().getOrNull()?.name.orEmpty()
            }
            AppGraph.repo.sendLiveMsg(room, text)
                .onSuccess {
                    liveMsgDraft = ""
                    // 本地回显：WS 也会推回自己这条，按 uid+文本去重由列表上限自然收敛。
                    val uid = AppGraph.repo.session.value.dedeUserId?.toLongOrNull() ?: 0L
                    val echo = LiveDanmaku(text = text, user = myLiveName.ifBlank { "我" }, uid = uid)
                    liveChat = (liveChat + echo).takeLast(120)
                    val sec = runCatching { engine.currentPosition / 1000.0 }.getOrDefault(0.0)
                    danmaku = (danmaku + Danmaku(sec, text, 1, 0xFFFFFF)).takeLast(400)
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
                .onFailure { toast = it.message ?: "发言失败" }
            liveMsgSending = false
        }
    }

    var qualityMedia by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    var fetchedQualityCatalog by remember { mutableStateOf(false) }
    var catalogQualities by remember { mutableStateOf<List<app.ailiaili.core.model.Quality>>(emptyList()) }
    var loadMedia by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun start(
        targetBvid: String,
        cid: Long,
        seek: Long? = null,
        durationSec: Int = 0,
        forceDash: Boolean = false,
        epId: Long = currentEpId,
    ) {
        // 换片复位重取流额度；同片重取流（seek>0 的错误恢复）保留计数防死循环。
        if (targetBvid != startedBvid || cid != startedCid) refetchRetries = 0
        currentEpId = epId
        val mediaKey = Triple(targetBvid, cid, epId)
        if (qualityMedia != mediaKey) {
            qualityMedia = mediaKey
            catalogQualities = emptyList()
            fetchedQualityCatalog = false
        }
        val requestedQuality = AppGraph.repo.settings.value.defaultQn
        loadMedia?.cancel()
        nextPlayback = null
        ended = false
        skippedStarts = emptySet()
        undoAt = -1L
        abA = -1L
        abB = -1L
        danmaku = emptyList()
        rawSegments = emptyList()
        chapters = emptyList()
        chaptersOpen = false
        shot = null
        subtitleTracks = emptyList()
        subtitleCues = emptyList()
        scope.launch {
            AppGraph.repo.subtitles(targetBvid, cid).onSuccess { got ->
                if (playingCid() != cid) return@onSuccess
                subtitleTracks = got
                if (got.isNotEmpty()) {
                    val preferred = settings.subtitleLan
                    val pick = got.firstOrNull { it.lan == preferred }
                        ?: got.firstOrNull { it.lan.startsWith("zh") }
                        ?: got.first()
                    subtitleLan = pick.lan
                    AppGraph.repo.subtitleCues(pick.url).onSuccess { cues ->
                        if (playingCid() == cid && subtitleLan == pick.lan) subtitleCues = cues
                    }
                }
            }
        }
        loadMedia = scope.launch {
            error = null
            // 番剧集优先 PGC 取流（大会员/付费内容只有这条链路给源）；
            // 失败回退 UGC bvid——限免集两边都能播，回退保证不因 PGC 风控断播。
            val play = if (epId > 0) {
                AppGraph.repo.pgcPlay(epId, detail?.aid ?: seedAid, cid, requestedQuality)
                    .recoverCatching { AppGraph.repo.play(targetBvid, cid, forceDash).getOrThrow() }
            } else {
                AppGraph.repo.play(targetBvid, cid, forceDash)
            }
            if (!kotlinx.coroutines.currentCoroutineContext().isActive) return@launch
            play.onSuccess { got ->
                catalogQualities = mergeQualityCatalog(catalogQualities, got.qualities)
                info = got.copy(qualities = catalogQualities)
                if (got.audioUrl.isNullOrBlank() && epId <= 0 && !forceDash && !fetchedQualityCatalog) {
                    fetchedQualityCatalog = true
                    scope.launch {
                        val catalog = kotlinx.coroutines.withTimeoutOrNull(3_000) {
                            AppGraph.repo.playAt(targetBvid, cid, requestedQuality, forceDash = true).getOrNull()
                        }
                        if (qualityMedia == mediaKey && catalog?.audioUrl != null) {
                            catalogQualities = mergeQualityCatalog(catalogQualities, catalog.qualities)
                            info = info?.copy(qualities = catalogQualities)
                        }
                    }
                }
                val audioOnly = listening && !got.audioUrl.isNullOrBlank()
                if (listening && !audioOnly) {
                    listening = false
                    toast = "这条视频没有可用的音轨"
                }
                val durationMs = durationSec * 1000L
                val remembered = if (seek == null) AppGraph.playbackMemory.read(targetBvid, cid) else 0L
                val resume = remembered in 5_000 until (durationMs - 15_000).coerceAtLeast(5_000)
                val startAt = seek?.let { if (durationMs > 0) it.coerceAtMost(durationMs - 1_000) else it }
                    ?: if (resume) remembered else 0L
                if (resume && seek == null) {
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
                engine.setMediaInfo(detail?.title.orEmpty(), detail?.owner.orEmpty())
                app.ailiaili.core.data.Diagnostics.lastPlayInfo =
                    "$targetBvid cid=$cid 请求画质=${settings.defaultQn} 实际=${got.actualQn} " +
                        "编码=${got.videoCodecs.ifBlank { "?" }} 音质=${got.audioQn} " +
                        "起播=${startAt}ms 听视频=$audioOnly"
                runCatching { engine.play(got, startAt, audioOnly = audioOnly) }
                    .onSuccess {
                        if (targetBvid != startedBvid || cid != startedCid) AppGraph.playbackMemory.begin(targetBvid, cid)
                        startedBvid = targetBvid
                        visitedVideos = visitedVideos + targetBvid
                        startedCid = cid
                        startedTag = engine.mediaTag
                        startedResume = detail?.takeIf { it.bvid == targetBvid && it.title.isNotBlank() }?.let { video ->
                            ResumePlayback(
                                video = VideoItem(
                                    bvid = targetBvid, cid = cid, title = video.title, cover = video.pic,
                                    author = video.owner, ownerMid = video.ownerMid,
                                ),
                                partLabel = video.pages.firstOrNull { it.cid == cid && video.pages.size > 1 }?.part.orEmpty(),
                                episodeId = epId,
                                positionMs = 0,
                                durationMs = durationMs,
                            )
                        }
                        listenSwitching = false
                    }
                    .onFailure {
                        error = it.message ?: "播放失败"
                        listenSwitching = false
                    }
            }.onFailure {
                error = it.message
                listenSwitching = false
            }
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

    // 取流过期的自动恢复：onError 里不能前向引用 start，用状态触发。
    LaunchedEffect(refetchRequest) {
        val req = refetchRequest ?: return@LaunchedEffect
        refetchRequest = null
        start(req.first, req.second, seek = req.third, durationSec = page?.durationSec ?: detail?.durationSec ?: 0)
    }

    fun openPart(next: VideoPage) {
        val current = detail ?: return
        page = next
        detail = current.copy(cid = next.cid)
        start(current.bvid, next.cid, durationSec = next.durationSec.takeIf { it > 0 } ?: current.durationSec)
    }

    fun loadComments(aid: Long, pageNum: Int, mode: Int, append: Boolean) {
        if (aid <= 0) return
        // 风控重试要等 30 秒，期间可能已换视频：结果落地前必须核对 aid，
        // 否则旧片评论覆盖新片评论区。
        if (detail?.aid != aid) return
        commentLoading = true
        commentError = null
        scope.launch {
            AppGraph.repo.comments(aid, pageNum, mode).onSuccess { got ->
                if (detail?.aid != aid) return@onSuccess
                comments = if (append) (comments + got.items).distinctBy { it.rpid } else got.items
                commentPage = got.page
                commentsMore = got.hasMore
                if (got.total > 0) commentTotal = got.total
                commentError = null
                commentCooldownUntil = 0L
            }.onFailure { failure ->
                if (detail?.aid != aid) return@onFailure
                val message = failure.message ?: "评论加载失败"
                commentError = message
                val risk = message.contains("风控") || message.contains("-412") || message.contains("频繁")
                if (risk && commentCooldownUntil == 0L) {
                    commentCooldownUntil = epochMs() + 30_000
                    commentError = "$message（30 秒后自动重试）"
                    scope.launch {
                        delay(30_000)
                        if (detail?.aid != aid) return@launch
                        commentCooldownUntil = 0L
                        loadComments(aid, pageNum, mode, append)
                    }
                }
            }
            if (detail?.aid == aid) commentLoading = false
        }
    }

    fun applyDetail(d: VideoDetail, epId: Long = seedEpId, seek: Long? = null) {
        nextPlayback = null
        detail = d
        commentsFullscreen = false
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
        createFolderOpen = false
        onlineText = null
        comments = emptyList()
        commentTotal = d.replyCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        commentError = null
        page = d.pages.firstOrNull { it.cid == d.cid } ?: d.pages.firstOrNull()
        val cid = page?.cid ?: d.cid
        if (cid > 0) start(d.bvid, cid, seek = seek, durationSec = page?.durationSec?.takeIf { it > 0 } ?: d.durationSec, epId = epId)
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
        related = emptyList()
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

    var selectionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun openEpisode(ep: app.ailiaili.core.model.Episode) {
        nextPlayback = null
        selectionJob?.cancel()
        selectionJob = scope.launch {
            val next = AppGraph.repo.video(ep.bvid).getOrNull()
            if (next != null) {
                toast = null
                applyDetail(next.copy(cid = ep.cid.takeIf { it > 0 } ?: next.cid), epId = 0)
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
                        epId = 0,
                    )
                }
            }
        }
    }

    /** 队列条目换片：带番剧集号的走 PGC，普通条目走 UGC；详情失败用种子兜底。 */
    fun openQueueItem(item: app.ailiaili.core.model.QueueItem) {
        nextPlayback = null
        selectionJob?.cancel()
        selectionJob = scope.launch {
            val next = AppGraph.repo.video(item.bvid).getOrNull()
            if (next != null) {
                applyDetail(next.copy(cid = item.cid.takeIf { it > 0 } ?: next.cid), epId = item.epId)
            } else {
                val cid = item.cid.takeIf { it > 0 }
                    ?: AppGraph.repo.pages(item.bvid).getOrNull()?.firstOrNull()?.cid
                    ?: 0L
                if (cid <= 0) {
                    error = "这一条暂时打不开"
                } else {
                    applyDetail(
                        fallbackDetail(
                            bvid = item.bvid,
                            cid = cid,
                            aid = 0,
                            title = item.title,
                            cover = item.cover,
                            author = item.author,
                            ownerMid = item.ownerMid,
                            durationSec = item.durationSec,
                            pages = emptyList(),
                        ),
                        epId = item.epId,
                    )
                }
            }
        }
    }

    LaunchedEffect(detail?.bvid, settings.showRelated, settings.autoPlayNext, settings.autoPlayRelated) {
        val current = detail?.bvid ?: return@LaunchedEffect
        if (!settings.showRelated && !(settings.autoPlayNext && settings.autoPlayRelated && relatedQueue == null)) return@LaunchedEffect
        AppGraph.repo.related(current).onSuccess { videos ->
            if (detail?.bvid != current) return@onSuccess
            related = videos
            if (relatedQueue == null) {
                relatedQueue = videos.filter { it.bvid.isNotBlank() && it.bvid != current && !it.isLive }
                    .distinctBy { it.bvid }
            }
        }
    }

    LaunchedEffect(nextPlayback, advanceNow, settings.autoPlayNext, settings.autoPlayRelated) {
        val next = nextPlayback ?: return@LaunchedEffect
        if (!settings.autoPlayNext || next is NextPlayback.Related && !settings.autoPlayRelated) {
            nextPlayback = null
            return@LaunchedEffect
        }
        if (!advanceNow) {
            for (seconds in 5 downTo 1) {
                nextSeconds = seconds
                delay(1_000)
            }
        }
        nextPlayback = null
        when (next) {
            is NextPlayback.Part -> openPart(next.part)
            is NextPlayback.Queue -> {
                curQueueIndex = next.index
                openQueueItem(next.item)
            }
            is NextPlayback.Collection -> openEpisode(next.episode)
            is NextPlayback.Related -> {
                relatedIndex = next.index
                val video = next.video
                openQueueItem(
                    app.ailiaili.core.model.QueueItem(
                        bvid = video.bvid,
                        cid = video.cid,
                        title = video.title,
                        cover = video.cover,
                        author = video.author,
                        ownerMid = video.ownerMid,
                        durationSec = video.durationSec,
                    ),
                )
            }
        }
    }

    // 桌面全局媒体键的上一曲/下一曲：队列 > 分P > 合集；离开播放页注销。
    DisposableEffect(engine) {
        engine.setMediaKeyNav { action ->
            val delta = if (action == 1) 1 else -1
            if (playQueue.isNotEmpty()) {
                val idx = (curQueueIndex + delta).coerceIn(0, playQueue.size - 1)
                if (idx != curQueueIndex) {
                    curQueueIndex = idx
                    openQueueItem(playQueue[idx])
                }
                return@setMediaKeyNav
            }
            val d = detail ?: return@setMediaKeyNav
            val pages = d.pages
            if (pages.size > 1) {
                val curCid = page?.cid ?: d.cid
                val idx = pages.indexOfFirst { it.cid == curCid }
                val next = pages.getOrNull(idx + delta)
                if (next != null) {
                    page = next
                    detail = d.copy(cid = next.cid)
                    start(d.bvid, next.cid, durationSec = next.durationSec.takeIf { it > 0 } ?: d.durationSec)
                }
                return@setMediaKeyNav
            }
            val eps = d.episodes
            if (eps.size > 1) {
                val idx = eps.indexOfFirst { it.bvid == d.bvid }
                eps.getOrNull(idx + delta)?.let { openEpisode(it) }
            }
        }
        onDispose { engine.setMediaKeyNav(null) }
    }

    LaunchedEffect(liveRoom, settings.danmakuEnabled) {
        val room = liveRoom ?: return@LaunchedEffect
        if (!settings.danmakuEnabled) return@LaunchedEffect
        while (true) {
            // 聊天列表攒 100ms 一批上屏（对齐 BiliDesk v1.7.6）：密集弹幕下
            // 逐条写状态会逐条重组。滚动弹幕的时间戳仍逐条打，保证入场时机。
            val chatBatch = ArrayList<LiveDanmaku>()
            var lastChatFlush = epochMs()
            AppGraph.repo.liveDanmaku(room)
                .catch { }
                .collect { msg ->
                    val sec = runCatching { engine.currentPosition / 1000.0 }.getOrDefault(0.0)
                    danmaku = (danmaku + Danmaku(sec, msg.text, msg.mode, msg.color)).takeLast(400)
                    chatBatch += msg
                    val now = epochMs()
                    if (now - lastChatFlush >= 100) {
                        liveChat = (liveChat + chatBatch).takeLast(120)
                        chatBatch.clear()
                        lastChatFlush = now
                    }
                }
            if (chatBatch.isNotEmpty()) {
                liveChat = (liveChat + chatBatch).takeLast(120)
            }
            delay(3_000)
        }
    }
    LaunchedEffect(bvid, liveRoom, local?.bvid, attempt) {
        if (liveRoom != null || local != null) listening = false
        coroutineScope {
            val ready = launch(ioDispatcher) { engine.prepare() }
            when {
                local != null -> {
                    ready.join()
                    // 离线续播：按 bvid+cid 读在线时记下的进度（下载时已存 cid）。
                    val remembered = if (local.cid > 0) AppGraph.playbackMemory.read(local.bvid, local.cid) else 0L
                    val startAt = remembered.takeIf { it >= 5_000 } ?: 0L
                    engine.setMediaInfo(local.title, "")
                    engine.playFiles(local.videoPath, local.audioPath, startAt)
                    startedBvid = local.bvid
                    startedCid = local.cid
                    startedTag = engine.mediaTag
                    if (startAt > 0) hint = "已回到上次看到的位置"
                    // 播的是合流 MP4：正常播了 5 秒才清原始双流，坏了还能回退。
                    if (local.videoPath.endsWith(".mp4") && local.audioPath == null) {
                        val mediaTag = engine.mediaTag
                        launch {
                            delay(5_000)
                            if (engine.mediaTag == mediaTag && engine.isPlaying && engine.currentPosition > 1_000) {
                                withContext(ioDispatcher) {
                                    runCatching { AppGraph.cache.pruneSources(local) }
                                }
                            }
                        }
                    }
                }
                liveRoom != null -> {
                    val play = AppGraph.repo.livePlay(liveRoom)
                    danmaku = emptyList()
                    liveChat = emptyList()
                    ready.join()
                    play.onSuccess { got ->
                        livePlayInfo = got
                        liveRetries = 0
                        engine.networkCachingMs = BufferPolicy.resolve(
                            kind = BufferMediaKind.Live,
                            shortVideo = false,
                            userMs = settings.networkCachingMs,
                            auto = settings.bufferAuto,
                            measuredBytesPerSecond = CdnPicker.lastMeasuredBytesPerSecond,
                            stallBoostMs = CdnPicker.stallBoostMs(got.url),
                        )
                        engine.setMediaInfo("直播 · 房间 $liveRoom", "")
                        engine.playLive(got.url)
                        scope.launch {
                            AppGraph.repo.liveRoomInfo(liveRoom).onSuccess { room ->
                                liveRoomInfo = room
                                val mine = AppGraph.repo.session.value.dedeUserId?.toLongOrNull()
                                if (room.ownerMid > 0 && room.ownerMid != mine) {
                                    followed = AppGraph.repo.relation(room.ownerMid).getOrDefault(false)
                                }
                            }
                        }
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
                        val cid = pages.firstOrNull { it.page == startPage }?.cid
                            ?: seedCid.takeIf { it > 0 } ?: pages.firstOrNull()?.cid ?: 0L
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
                    val chosen = resolved.pages.firstOrNull { it.page == startPage }
                        ?: resolved.pages.firstOrNull { it.cid == seedCid && startAtMs != null }
                    val initial = if (chosen != null) resolved.copy(cid = chosen.cid) else resolved
                    applyDetail(initial, seek = startAtMs)
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
            if (scrubbing || !engine.isPlaying || epochMs() < seekGraceUntil) {
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

    val usingControls = scrubbing || qualityOpen || speedOpen || audioOpen || volumeOpen || areaOpen ||
        dmBarOpen || subtitleOpen || sleepOpen || abOpen || chaptersOpen || playbackOptionsOpen ||
        playbackInfoOpen || scaleOpen || danmakuTimingOpen || cacheOpen || queuePanelOpen
    LaunchedEffect(chrome, playing, controlsLocked, usingControls, pointerOverChrome) {
        if (chrome && playing && !controlsLocked && !usingControls && !pointerOverChrome) {
            var wait = ChromeHideDelayMs
            do {
                delay(wait)
                val elapsed = pointerClock.elapsedNow().inWholeMilliseconds - lastPointerMotion
                wait = (ChromeHideDelayMs - elapsed).coerceAtLeast(0L)
            } while (wait > 0L)
            chrome = false
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
            val pos = runCatching { engine.currentPosition }.getOrNull()
            if (pos != null) {
                // 进度记录与 SponsorBlock 无关，任何时候都要写。身份取引擎实际在播的
                // 媒体（started*），不是 detail/page：换片瞬间 detail 已切到新片而引擎
                // 还在播旧片，用 detail 会把旧片进度写到新片名下。
                rememberProgress(pos, finished = ended)
                if (sponsorOn.value) {
                    val sec = pos / 1000.0
                    val seen = skippedState.value
                    val hit = SponsorCategories.select(sponsorRaw.value, sponsorCid.value, sponsorCats.value)
                        .firstOrNull {
                            sec >= it.startSec && sec < it.endSec - 0.4 && it.startSec !in seen
                        }
                    if (hit != null) {
                        skippedState.value = seen + hit.startSec
                        undoAt = pos
                        seekTo((hit.endSec * 1000).toLong())
                        hint = "已跳过${SponsorCategories.label(hit.category)}"
                    }
                }
                // A-B 循环：到 B 回 A。seek 宽限期内不判，避免落点确认时重复触发。
                if (epochMs() > seekGraceUntil &&
                    app.ailiaili.core.model.abLoopShouldRewind(abAState.value, abBState.value, pos)
                ) {
                    seekTo(abAState.value)
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
            if (!engine.isPlaying || engine.isSeekPending || engine.mediaTag != startedTag || cid != startedCid) continue
            HistorySync.report(aid, cid, engine.currentPosition / 1000, detail?.bvid)
        }
    }

    LaunchedEffect(page?.cid, detail?.cid, detail?.bvid) {
        val cid = page?.cid?.takeIf { it > 0 } ?: detail?.cid?.takeIf { it > 0 } ?: return@LaunchedEffect
        var loaded = HashSet<Int>()
        var merged = emptyList<Danmaku>()
        var activeSeg = -1
        var generation = 0
        localDanmaku = emptyList()
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
                        // 本地发送的弹幕并回网络列表；服务端段里已有同文同秒的就不再重复。
                        val local = localDanmaku.filter { l -> merged.none { it.text == l.text && kotlin.math.abs(it.timeSec - l.timeSec) < 1.0 } }
                        danmaku = if (local.isEmpty()) merged else (merged + local).sortedBy { it.timeSec }
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
    // 小窗里只留画面：控制栏/浮层全部收起，点按小窗由系统接管。
    val inPip = app.ailiaili.core.data.inPip()
    val showChrome = !controlsLocked && (chrome || !playing) && !inPip
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
    commentImagePreview?.let { (urls, index, name) ->
        app.ailiaili.core.designsystem.AvatarPreviewDialog(
            url = urls.getOrElse(index) { urls.first() },
            name = name,
            onDismiss = { commentImagePreview = null },
            onSaved = { toast = it },
            urls = urls,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onFocusChanged { state ->
                // 长按 → 期间 Alt-Tab/点走会丢 KeyUp，rightHeld 不复位就永远
                // 卡在 3 倍速。焦点一丢立即按「松开」处理。
                if (!state.hasFocus && rightHeld) rightHeld = false
            }
            .onPreviewKeyEvent { event ->
                if (controlsLocked) {
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        unlockControls()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                // 裸字母快捷键不吞 Ctrl/Alt/Meta 组合键：Ctrl+C 复制、Ctrl+F 查找等
                // 必须原样放行给文本框/系统，否则复制失效还会误触字幕/全屏。
                if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp && event.key == Key.DirectionRight) {
                    if (rightHeld) {
                        rightHeld = false
                        if (!rightBoost) seekTo(engine.currentPosition + keyboardSeekMs)
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (composing) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Spacebar, Key.MediaPlayPause -> {
                        if (liveRoom != null && event.key == Key.Spacebar) return@onPreviewKeyEvent true
                        val willPlay = !engine.isPlaying
                        if (willPlay) engine.play() else engine.pause()
                        playFlash = if (willPlay) FlashKind.Play else FlashKind.Pause
                        playFlashTick++
                        true
                    }
                    Key.DirectionLeft -> {
                        seekTo((engine.currentPosition - keyboardSeekMs).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight -> {
                        // 首次按下只记时；系统自动重复不再逐次快进，450ms 后由长按逻辑进入 3 倍速。
                        if (!rightHeld) rightHeld = true
                        true
                    }
                    Key.DirectionUp -> {
                        if (rightBoost) adjustTemporarySpeed(1) else setVolume(volume + 0.1f)
                        true
                    }
                    Key.DirectionDown -> {
                        if (rightBoost) adjustTemporarySpeed(-1) else setVolume(volume - 0.1f)
                        true
                    }
                    Key.M -> {
                        toggleMute()
                        true
                    }
                    Key.F -> {
                        fullscreen = !fullscreen
                        true
                    }
                    Key.D -> {
                        toggleDanmaku()
                        true
                    }
                    Key.G -> {
                        toggleClearScreen()
                        true
                    }
                    Key.C -> {
                        if (subtitleChoices.isEmpty()) {
                            toast = "这条视频没有字幕"
                        } else {
                            AppGraph.repo.updateSettings { it.copy(subtitleEnabled = !it.subtitleEnabled) }
                            toast = if (settings.subtitleEnabled) "字幕已关闭" else "字幕已开启"
                        }
                        true
                    }
                    Key.Z -> {
                        val next = ((settings.subtitleDelaySec - 0.2) * 10).roundToInt() / 10.0
                        AppGraph.repo.updateSettings { it.copy(subtitleDelaySec = next.coerceIn(-5.0, 5.0)) }
                        toast = "字幕提前 0.2s（${next.coerceIn(-5.0, 5.0)}s）"
                        true
                    }
                    Key.X -> {
                        val next = ((settings.subtitleDelaySec + 0.2) * 10).roundToInt() / 10.0
                        AppGraph.repo.updateSettings { it.copy(subtitleDelaySec = next.coerceIn(-5.0, 5.0)) }
                        toast = "字幕延后 0.2s（${next.coerceIn(-5.0, 5.0)}s）"
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
                        if (commentsFullscreen) {
                            commentsFullscreen = false
                        } else if (fullscreen) fullscreen = false else leavePlayer()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
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
            // 视频区窄于 560dp 时收起次要控制项（弹幕区域、听视频），
            // 否则竖屏副屏/窄窗口下顶栏会挤成一团甚至被裁掉。
            val compactChrome = maxWidth < 560.dp
            val stageHeight = if (fillStage) {
                if (maxHeight == androidx.compose.ui.unit.Dp.Infinity) 300.dp else maxHeight
            } else {
                minOf(maxWidth / stageRatio, (form.height * .56f).coerceIn(172.dp, 450.dp))
            }
            val inlineTimeline = !compactChrome && liveRoom == null
            val transportClearance = if (inlineTimeline || liveRoom != null) InlineTransportClearance else StackedTransportClearance
            val expandedControls = dmBarOpen || volumeOpen
            val chromeInset = minOf(transportClearance + (if (expandedControls) 52.dp else 0.dp), stageHeight / 2)
            val transportInsets = if (fullscreen) Modifier.navigationBarsPadding() else Modifier
            val topShade = minOf(92.dp, stageHeight * .43f)
            val bottomShade = minOf(
                transportClearance + (if (expandedControls) 76.dp else 26.dp),
                stageHeight * (if (expandedControls) .70f else .57f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    .clipToBounds()
                    .background(AliColors.StageMedia)
                    .then(
                        if (!desktop || controlsLocked || inPip) Modifier
                        else Modifier.pointerInput(transportClearance) {
                            try {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        when (event.type) {
                                            PointerEventType.Exit -> pointerOverChrome = false
                                            PointerEventType.Enter, PointerEventType.Move, PointerEventType.Release -> {
                                                val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse && !it.pressed }
                                                if (mouse != null) {
                                                    val y = mouse.position.y
                                                    pointerOverChrome = y < 58.dp.toPx() || y >= size.height - transportClearance.toPx()
                                                    lastPointerMotion = pointerClock.elapsedNow().inWholeMilliseconds
                                                    chrome = true
                                                }
                                            }
                                        }
                                    }
                                }
                            } finally {
                                pointerOverChrome = false
                            }
                        },
                    ),
            ) {
            VideoSurface(engine, Modifier.fillMaxSize(), scale = settings.playerScale)
            if (showPoster) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            if (!clearScreen && (bvid != null && info != null || liveRoom != null || cachedPlayback.danmaku.isNotEmpty()) && settings.danmakuEnabled) {
                if (!listening) {
                    DanmakuOverlay(
                        engine,
                        if (local == null) danmaku else cachedPlayback.danmaku,
                        settings,
                        frozen = buffering || scrubbing,
                        live = liveRoom != null,
                    )
                }
            }
            if ((bvid != null || local != null) && !listening && liveRoom == null) {
                SubtitleOverlay(
                    engine = engine,
                    cues = if (local == null) subtitleCues else selectedCachedSubtitle?.cues.orEmpty(),
                    delaySec = settings.subtitleDelaySec,
                    visible = settings.subtitleEnabled,
                    fontSp = settings.subtitleFontSp,
                    bottomPercent = settings.subtitleBottomPercent,
                    // 控制栏显示时字幕上移一点，不被工具条压住。
                    modifier = (if (showChrome) transportInsets else Modifier)
                        .padding(bottom = if (showChrome) chromeInset else 0.dp),
                )
            }
            if (!controlsLocked && (bvid != null || liveRoom != null || local != null)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (!gesturesReady) Modifier
                            else Modifier.pointerInput(baseSpeed, liveRoom, desktop, brightnessOk, startedTag, gestureSeekSeconds) {
                                var lastTap = -1L
                                var lastPosition = androidx.compose.ui.geometry.Offset.Zero
                                var tapJob: kotlinx.coroutines.Job? = null
                                var hintJob: kotlinx.coroutines.Job? = null
                                var gestureHint: String? = null
                                val tapWindow = viewConfiguration.doubleTapTimeoutMillis.coerceAtMost(250L)
                                val slop = viewConfiguration.touchSlop

                                fun clearGestureHint() {
                                    hintJob?.cancel()
                                    if (hint == gestureHint) hint = null
                                    gestureHint = null
                                }

                                fun showHint(text: String, linger: Boolean = false) {
                                    hintJob?.cancel()
                                    hint = text
                                    gestureHint = text
                                    if (linger) {
                                        hintJob = scope.launch {
                                            kotlinx.coroutines.delay(800)
                                            if (hint == text) hint = null
                                            gestureHint = null
                                        }
                                    }
                                }

                                fun togglePlayback() {
                                    val willPlay = !engine.isPlaying
                                    if (willPlay) engine.play() else engine.pause()
                                    playFlash = if (willPlay) FlashKind.Play else FlashKind.Pause
                                    playFlashTick++
                                }

                                try {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val origin = down.position
                                        val media = engine.mediaTag
                                        var drag: PlayerSurfaceDrag? = null
                                        var holding = false
                                        var seekOutside = false
                                        var seekDrag = 0f
                                        var startPos = engine.currentPosition
                                        var pendingSeek = startPos
                                        val startVolume = volume
                                        val startBrightness = windowBrightness()
                                        val hold = if (!desktop && liveRoom == null) {
                                            scope.launch {
                                                kotlinx.coroutines.delay(320)
                                                if (drag == null && engine.mediaTag == media) {
                                                    tapJob?.cancel()
                                                    lastTap = -1L
                                                    holding = true
                                                    val holdSpeed = temporarySpeeds[temporarySpeedIndex]
                                                    speed = holdSpeed
                                                    showHint("${speedLabel(holdSpeed)} 快进中")
                                                }
                                            }
                                        } else null
                                        var holdStep = 0
                                        var release: PointerInputChange? = null
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.isConsumed || engine.mediaTag != media ||
                                                    event.changes.any { it.id != down.id && it.pressed }
                                                ) break
                                                val p = change.position
                                                val outside = p.x !in 0f..size.width.toFloat() || p.y !in 0f..size.height.toFloat()
                                                if (!change.pressed) {
                                                    if (!outside && !seekOutside) release = change
                                                    break
                                                }
                                                val dx = p.x - origin.x
                                                val dy = p.y - origin.y
                                                if (holding) {
                                                    val step = (dx / 64.dp.toPx()).toInt()
                                                    if (step != holdStep) {
                                                        adjustTemporarySpeed(if (step > holdStep) 1 else -1)
                                                        holdStep = step
                                                    }
                                                    change.consume()
                                                    continue
                                                }
                                                val stepX = p.x - change.previousPosition.x
                                                if (drag == null && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                                                    hold?.cancel()
                                                    tapJob?.cancel()
                                                    lastTap = -1L
                                                    drag = when {
                                                        kotlin.math.abs(dx) > kotlin.math.abs(dy) -> PlayerSurfaceDrag.Seek
                                                        origin.x < size.width / 2f && brightnessOk -> PlayerSurfaceDrag.Brightness
                                                        else -> PlayerSurfaceDrag.Volume
                                                    }
                                                    seekDrag = dx - stepX
                                                    startPos = engine.currentPosition
                                                }
                                                if (liveRoom != null) continue
                                                val activeDrag = drag ?: continue
                                                change.consume()
                                                when (activeDrag) {
                                                    PlayerSurfaceDrag.Seek -> {
                                                        if (!outside && !seekOutside) seekDrag += stepX
                                                        seekOutside = outside
                                                        val duration = engine.duration.coerceAtLeast(1L)
                                                        val delta = (seekDrag / size.width.coerceAtLeast(1) * 90_000f).toLong()
                                                        pendingSeek = (startPos + delta).coerceIn(0L, duration)
                                                        showHint(if (outside) "松手取消定位" else "滑到 ${clock(pendingSeek)}")
                                                    }
                                                    PlayerSurfaceDrag.Brightness -> {
                                                        val next = (startBrightness - dy / size.height.coerceAtLeast(1)).coerceIn(0.02f, 1f)
                                                        setWindowBrightness(next)
                                                        showHint("亮度 ${(next * 100).toInt()}")
                                                    }
                                                    PlayerSurfaceDrag.Volume -> {
                                                        setVolume(startVolume - dy / size.height.coerceAtLeast(1))
                                                        showHint("音量 ${(volume * 100).toInt()}")
                                                    }
                                                }
                                            }
                                        } finally {
                                            hold?.cancel()
                                            if (holding) speed = baseSpeed
                                            if (holding || drag != null && release == null) clearGestureHint()
                                        }
                                        val up = release ?: return@awaitEachGesture
                                        if (holding || engine.mediaTag != media) return@awaitEachGesture
                                        if (drag != null) {
                                            if (liveRoom == null && drag == PlayerSurfaceDrag.Seek) {
                                                seekTo(pendingSeek)
                                                showHint("滑到 ${clock(pendingSeek)}", linger = true)
                                            } else if (gestureHint != null) {
                                                showHint(gestureHint.orEmpty(), linger = true)
                                            }
                                            return@awaitEachGesture
                                        }
                                        val paired = liveRoom == null && lastTap >= 0L &&
                                            up.uptimeMillis - lastTap in 1..tapWindow &&
                                            (up.position - lastPosition).getDistance() <= 48.dp.toPx()
                                        tapJob?.cancel()
                                        if (paired) {
                                            lastTap = -1L
                                            val x = up.position.x
                                            when {
                                                desktop -> fullscreen = !fullscreen
                                                x < size.width / 3f -> {
                                                    seekTo((engine.currentPosition - gestureSeekSeconds * 1000L).coerceAtLeast(0L))
                                                    seekFlashSide = -1
                                                    playFlashTick++
                                                }
                                                x > size.width * 2f / 3f -> {
                                                    seekTo(engine.currentPosition + gestureSeekSeconds * 1000L)
                                                    seekFlashSide = 1
                                                    playFlashTick++
                                                }
                                                else -> togglePlayback()
                                            }
                                        } else if (liveRoom != null) {
                                            chrome = !chrome
                                        } else {
                                            lastTap = up.uptimeMillis
                                            lastPosition = up.position
                                            tapJob = scope.launch {
                                                kotlinx.coroutines.delay(tapWindow)
                                                lastTap = -1L
                                                if (engine.mediaTag == media) {
                                                    if (desktop) {
                                                        togglePlayback()
                                                        chrome = true
                                                    } else {
                                                        chrome = !chrome
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } finally {
                                    tapJob?.cancel()
                                    clearGestureHint()
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
                                                val change = event.changes.firstOrNull() ?: continue
                                                val dy = change.scrollDelta.y
                                                if (dy == 0f || change.isConsumed) continue
                                                if (rightBoost) adjustTemporarySpeed(if (dy < 0f) 1 else -1)
                                                else setVolume(volume - dy * 0.05f)
                                                change.consume()
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
            if (controlsLocked && !app.ailiaili.core.data.inPip()) {
                AliStageIconButton(
                    Icons.Filled.Lock,
                    "解锁播放控件",
                    onClick = ::unlockControls,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    onMedia = true,
                    emphasized = true,
                )
            }
            // 中央状态浮现：播放/暂停图标或 ±10 秒，600ms 内淡入放大再淡出。
            playFlash?.takeUnless { reducedMotion }?.let { kind ->
                androidx.compose.runtime.key(playFlashTick) {
                    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        anim.animateTo(1f, androidx.compose.animation.core.tween(140))
                        kotlinx.coroutines.delay(260)
                        anim.animateTo(0f, androidx.compose.animation.core.tween(220))
                        playFlash = null
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                alpha = anim.value
                                val s = 0.7f + 0.3f * anim.value
                                scaleX = s
                                scaleY = s
                            }
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(18.dp),
                    ) {
                        Icon(
                            when (kind) {
                                FlashKind.Play -> Icons.Filled.PlayArrow
                                FlashKind.Pause -> Icons.Filled.Pause
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            if (!reducedMotion && seekFlashSide != 0) {
                androidx.compose.runtime.key(playFlashTick) {
                    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        anim.animateTo(1f, androidx.compose.animation.core.tween(140))
                        kotlinx.coroutines.delay(300)
                        anim.animateTo(0f, androidx.compose.animation.core.tween(220))
                        seekFlashSide = 0
                    }
                    Row(
                        Modifier
                            .align(if (seekFlashSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(horizontal = 32.dp)
                            .graphicsLayer { alpha = anim.value }
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (seekFlashSide < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("$gestureSeekSeconds 秒", color = Color.White, fontSize = 13.sp)
                    }
                }
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
                        .padding(bottom = if (showChrome) chromeInset else 24.dp)
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
                            seekTo(undoAt)
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
                    val next = nextPlayback
                    if (next != null) {
                        Text("${nextSeconds} 秒后播放", color = Color.White)
                        Text(next.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { advanceNow = true }) { Text("立即播放") }
                            FilledTonalButton(onClick = { nextPlayback = null }) { Text("取消") }
                        }
                    } else {
                        Text("已播完", color = Color.White)
                    }
                    FilledTonalButton(onClick = {
                        nextPlayback = null
                        skippedStarts = emptySet()
                        if (isDesktopHost()) {
                            // 已结束的 VLC input 对 seek 只改读数不出画面：重建 Media 从头起播。
                            engine.seekTo(0)
                        } else {
                            seekTo(0)
                            engine.play()
                        }
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
                if (info == null || !playing) {
                    Column(
                        Modifier.align(Alignment.Center)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            refetchRetries = 0
                            liveRetries = 0
                            val current = detail
                            val cid = playingCid()
                            if (current != null && local == null && liveRoom == null && cid > 0) {
                                start(current.bvid, cid, engine.currentPosition, page?.durationSec ?: current.durationSec)
                            } else {
                                attempt++
                            }
                        }) { Text("重试") }
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
            PlayerChromeEdge(
                visible = showChrome,
                top = true,
                shadeHeight = topShade,
                reducedMotion = reducedMotion,
                hideImmediately = controlsLocked || inPip,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                PlayerTopControls(
                    title = title.takeIf { fullscreen && !compactChrome },
                    onBack = { if (fullscreen) fullscreen = false else leavePlayer() },
                    modifier = if (fullscreen) Modifier.statusBarsPadding() else Modifier,
                    trailing = {
                        if (fullscreen && detail != null && liveRoom == null && local == null) {
                            AliStageIconButton(
                                Icons.AutoMirrored.Filled.Comment,
                                "全屏评论",
                                onClick = {
                                    commentsFullscreen = !commentsFullscreen
                                    if (commentsFullscreen) {
                                        panel = "comments"
                                        chrome = true
                                    }
                                },
                                selected = commentsFullscreen,
                                overVideo = true,
                            )
                        }
                    },
                ) {
                    if (info != null && liveRoom == null) {
                        Box {
                            PlayerControlLabel(qnLabel(info?.actualQn ?: settings.defaultQn), { qualityOpen = true }, active = qualityOpen)
                            AliStageMenu(expanded = qualityOpen, onDismissRequest = { qualityOpen = false }) {
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
                    if (liveRoom == null && subtitleChoices.isNotEmpty()) {
                        Box {
                            PlayerControlLabel("字幕", { subtitleOpen = true }, active = settings.subtitleEnabled || subtitleOpen)
                            AliStageMenu(expanded = subtitleOpen, onDismissRequest = { subtitleOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (settings.subtitleEnabled) "关闭字幕（C）" else "开启字幕（C）") },
                                    onClick = {
                                        subtitleOpen = false
                                        AppGraph.repo.updateSettings { it.copy(subtitleEnabled = !it.subtitleEnabled) }
                                    },
                                )
                                HorizontalDivider()
                                subtitleChoices.forEach { track ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                track.label + if (track.ai) "（AI）" else "",
                                                fontWeight = if (track.lan == (selectedCachedSubtitle?.track?.lan ?: subtitleLan)) {
                                                    FontWeight.SemiBold
                                                } else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            subtitleOpen = false
                                            subtitleLan = track.lan
                                            subtitleCues = emptyList()
                                            AppGraph.repo.updateSettings {
                                                it.copy(subtitleLan = track.lan, subtitleEnabled = true)
                                            }
                                            if (local == null) scope.launch {
                                                AppGraph.repo.subtitleCues(track.url).onSuccess { cues ->
                                                    if (subtitleLan == track.lan) subtitleCues = cues
                                                }.onFailure { toast = it.message ?: "字幕加载失败" }
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("提前 0.2s（Z）") },
                                    onClick = {
                                        val next = ((settings.subtitleDelaySec - 0.2) * 10).roundToInt() / 10.0
                                        AppGraph.repo.updateSettings { it.copy(subtitleDelaySec = next.coerceIn(-5.0, 5.0)) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("延后 0.2s（X）") },
                                    onClick = {
                                        val next = ((settings.subtitleDelaySec + 0.2) * 10).roundToInt() / 10.0
                                        AppGraph.repo.updateSettings { it.copy(subtitleDelaySec = next.coerceIn(-5.0, 5.0)) }
                                    },
                                )
                                if (settings.subtitleDelaySec != 0.0) {
                                    DropdownMenuItem(
                                        text = { Text("重置延迟（当前 ${settings.subtitleDelaySec}s）") },
                                        onClick = {
                                            subtitleOpen = false
                                            AppGraph.repo.updateSettings { it.copy(subtitleDelaySec = 0.0) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    val room = liveRoom
                    val liveInfo = livePlayInfo
                    if (room != null && liveInfo != null && liveInfo.qualities.size > 1) {
                        Box {
                            PlayerControlLabel(
                                liveInfo.qualities.firstOrNull { it.qn == liveInfo.currentQn }?.label ?: "清晰度",
                                { qualityOpen = true },
                                active = qualityOpen,
                            )
                            AliStageMenu(expanded = qualityOpen, onDismissRequest = { qualityOpen = false }) {
                                liveInfo.qualities.forEach { quality ->
                                    DropdownMenuItem(
                                        text = { Text(quality.label) },
                                        onClick = {
                                            qualityOpen = false
                                            scope.launch {
                                                AppGraph.repo.livePlay(room, quality.qn).onSuccess { got ->
                                                    livePlayInfo = got
                                                    engine.playLive(got.url)
                                                    toast = "清晰度已切换为 ${quality.label}"
                                                }.onFailure { toast = it.message ?: "切换清晰度失败" }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (liveRoom == null && local == null && info != null && !compactChrome) {
                        PlayerControlLabel(
                            label = when {
                                listenSwitching -> "切换中…"
                                listening -> "正在聆听 $listenClock"
                                else -> "听视频"
                            },
                            active = listening,
                            enabled = !listenSwitching,
                            onClick = {
                                // 切换闸门：重建媒体要停上一路解码，期间连点会出
                                // 「按钮说听视频、实际播的是视频」的半状态。
                                listenSwitching = true
                                val pos = engine.currentPosition
                                if (listening) {
                                    listening = false
                                    engine.restoreVideo(pos)
                                    scope.launch {
                                        delay(600)
                                        listenSwitching = false
                                    }
                                } else if (engine.playAudioOnly(pos)) {
                                    listening = true
                                    scope.launch {
                                        delay(600)
                                        listenSwitching = false
                                    }
                                } else {
                                    // durl 合流单流没有独立音轨；切回 DASH 再取一次 audioUrl。
                                    val id = bvid ?: detail?.bvid
                                    val cidNow = page?.cid ?: detail?.cid ?: 0L
                                    if (id.isNullOrBlank() || cidNow <= 0) {
                                        toast = "这条视频没有可用的音轨"
                                        listenSwitching = false
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
                            },
                        )
                    }
                    AliStageIconButton(
                        when {
                            clearScreen -> Icons.Filled.CenterFocusStrong
                            settings.danmakuEnabled -> Icons.Filled.ClosedCaption
                            else -> Icons.Filled.ClosedCaptionOff
                        },
                        when {
                            clearScreen -> "退出清屏"
                            settings.danmakuEnabled -> "关闭弹幕"
                            else -> "打开弹幕"
                        },
                        onClick = ::toggleDanmaku,
                        selected = clearScreen || settings.danmakuEnabled,
                        overVideo = true,
                    )
                    if (!clearScreen && liveRoom == null && local == null && bvid != null) {
                        PlayerControlLabel("发弹幕", onClick = {
                            dmBarOpen = !dmBarOpen
                            if (dmBarOpen) {
                                volumeOpen = false
                                chrome = true
                            }
                        }, active = dmBarOpen)
                    }
                    if (playQueue.size > 1) {
                        PlayerControlLabel(
                            "${if (seasonQueue) "选集" else "队列"} ${curQueueIndex + 1}/${playQueue.size}",
                            { queuePanelOpen = true },
                            active = queuePanelOpen,
                        )
                    }
                    if (!clearScreen && bvid != null && !compactChrome) {
                        PlayerControlLabel(if (settings.danmakuPillBackground) "底衬" else "纯字", onClick = {
                            AppGraph.repo.updateSettings {
                                it.copy(danmakuPillBackground = !it.danmakuPillBackground)
                            }
                        })
                        Box {
                            PlayerControlLabel("${settings.danmakuAreaPercent}%", onClick = {
                                areaDraft = settings.danmakuAreaPercent.toFloat()
                                areaOpen = true
                            }, active = areaOpen)
                            AliStageMenu(expanded = areaOpen, onDismissRequest = { areaOpen = false }) {
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
                            PlayerControlLabel(speedLabel(speed), onClick = {
                                speedDraft = speed
                                speedOpen = true
                            }, active = speedOpen)
                            AliStageMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                                speeds.forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(speedLabel(value)) },
                                        onClick = {
                                            speedOpen = false
                                            speed = value
                                            baseSpeed = value
                                            AppGraph.repo.updateSettings { it.copy(playbackSpeed = value) }
                                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
                                        },
                                    )
                                }
                                HorizontalDivider()
                                Column(Modifier.padding(horizontal = 16.dp).width(220.dp)) {
                                    Text("自定义 ${speedLabel(speedDraft)}")
                                    Slider(
                                        value = speedDraft,
                                        onValueChange = { speedDraft = it },
                                        onValueChangeFinished = {
                                            speed = speedDraft
                                            baseSpeed = speedDraft
                                            AppGraph.repo.updateSettings { it.copy(playbackSpeed = speedDraft) }
                                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
                                        },
                                        valueRange = 0.25f..4f,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PlayerChromeEdge(
                visible = showChrome,
                top = false,
                shadeHeight = bottomShade,
                reducedMotion = reducedMotion,
                hideImmediately = controlsLocked || inPip,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PlayerTransport(
                    engine = engine,
                    live = liveRoom != null,
                    playing = playing,
                    onScrubbing = { scrubbing = it },
                    onSeek = { ms ->
                        seekTo(ms)
                        if (ended) {
                            nextPlayback = null
                            engine.play()
                            ended = false
                        }
                    },
                    onTogglePlay = {
                        val willPlay = !engine.isPlaying
                        if (willPlay) engine.play() else engine.pause()
                        playFlash = if (willPlay) FlashKind.Play else FlashKind.Pause
                        playFlashTick++
                    },
                    onVolume = {
                        volumeOpen = !volumeOpen
                        if (volumeOpen && dmBarOpen) dmBarOpen = false
                    },
                    volumeOpen = volumeOpen,
                    muted = volume <= 0f,
                    marks = if (settings.sponsorBlock) activeMarks else emptyList(),
                    chapters = chapters,
                    chaptersOpen = chaptersOpen,
                    onChaptersOpen = { chaptersOpen = it },
                    shot = shot,
                    abRange = if (abA >= 0 && abB > abA + 1_000) abA to abB else null,
                    fullscreen = fullscreen,
                    inlineTimeline = inlineTimeline,
                    playbackOptions = {
                        val inf = info
                        Box {
                            AliStageIconButton(
                                Icons.Filled.MoreHoriz, "更多播放选项",
                                onClick = { playbackOptionsOpen = true }, selected = playbackOptionsOpen,
                                overVideo = true,
                            )
                            AliStageMenu(expanded = playbackOptionsOpen, onDismissRequest = { playbackOptionsOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (clearScreen) "关闭清屏模式" else "清屏模式") },
                                    leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, null) },
                                    trailingIcon = if (desktop) ({ Text("G", style = MaterialTheme.typography.labelMedium) }) else null,
                                    onClick = {
                                        playbackOptionsOpen = false
                                        toggleClearScreen()
                                    },
                                )
                                val momentBvid = startedBvid
                                if (local == null && liveRoom == null && momentBvid != null && startedCid == playingCid() &&
                                    detail?.bvid == momentBvid && engine.mediaTag == startedTag
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("分享此刻 · 复制链接") },
                                        leadingIcon = { Icon(Icons.Filled.Link, null) },
                                        onClick = {
                                            playbackOptionsOpen = false
                                            val position = engine.currentPosition.coerceAtLeast(0L)
                                            clipboard.setText(AnnotatedString(videoMomentUrl(momentBvid, page?.page ?: 1, position)))
                                            toast = "已复制 ${clock(position)} 的播放链接"
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("画面比例 · ${settings.playerScale.label}") },
                                    onClick = {
                                        playbackOptionsOpen = false
                                        scaleOpen = true
                                    },
                                )
                                if (fullscreen) {
                                    DropdownMenuItem(
                                        text = { Text("锁定播放控件") },
                                        onClick = {
                                            playbackOptionsOpen = false
                                            rightHeld = false
                                            controlsLocked = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("播放信息") },
                                    onClick = {
                                        playbackOptionsOpen = false
                                        playbackInfoOpen = true
                                    },
                                )
                                if ((bvid != null || cachedPlayback.danmaku.isNotEmpty()) && liveRoom == null) {
                                    DropdownMenuItem(
                                        text = { Text("弹幕时间偏移") },
                                        onClick = {
                                            playbackOptionsOpen = false
                                            danmakuTimingOpen = true
                                        },
                                    )
                                }
                                if (inf != null && inf.audioQualities.size > 1) {
                                    DropdownMenuItem(text = { Text("音质") }, onClick = {
                                        playbackOptionsOpen = false
                                        audioOpen = true
                                    })
                                }
                                if (liveRoom == null) {
                                    DropdownMenuItem(
                                        text = { Text("截图") },
                                        onClick = {
                                            playbackOptionsOpen = false
                                            scope.launch {
                                                val name = (detail?.title ?: seedTitle).take(40)
                                                val result = runCatching { engine.snapshot(name) }.getOrNull()
                                                toast = result ?: "截图失败，画面可能还没就绪"
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(when {
                                                abA >= 0 && abB > abA -> "A–B 循环 · 已开启"
                                                abA >= 0 -> "A–B 循环 · 已设 A 点"
                                                else -> "A–B 循环"
                                            })
                                        },
                                        onClick = {
                                            playbackOptionsOpen = false
                                            abOpen = true
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(if (sleepMinutes < 0) "睡眠定时" else "睡眠定时 · ${sleepRemainText.ifBlank { "已开启" }}")
                                    },
                                    onClick = {
                                        playbackOptionsOpen = false
                                        sleepOpen = true
                                    },
                                )
                            }
                            if (inf != null && inf.audioQualities.size > 1) {
                                AliStageMenu(expanded = audioOpen, onDismissRequest = { audioOpen = false }) {
                                    inf.audioQualities.forEach { quality ->
                                        DropdownMenuItem(
                                            text = { Text(quality.label) },
                                            onClick = {
                                                audioOpen = false
                                                val pos = engine.currentPosition
                                                val next = inf.copy(audioUrl = quality.url, audioQn = quality.id)
                                                info = next
                                                engine.play(next, pos, audioOnly = listening)
                                                toast = "音质已切换为 ${quality.label}"
                                            },
                                        )
                                    }
                                }
                            }
                            if (liveRoom == null) {
                                AliStageMenu(expanded = abOpen, onDismissRequest = { abOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (abA >= 0) "重设 A 点（${clock(abA)}）" else "设为 A 点") },
                                        onClick = {
                                            abOpen = false
                                            abA = engine.currentPosition
                                            if (abB >= 0 && abB <= abA + 1_000) abB = -1L
                                            toast = "A 点已设在 ${clock(abA)}"
                                        },
                                    )
                                    DropdownMenuItem(
                                        enabled = abA >= 0,
                                        text = { Text(if (abB > abA) "重设 B 点（${clock(abB)}）" else "设为 B 点") },
                                        onClick = {
                                            abOpen = false
                                            val pos = engine.currentPosition
                                            if (pos <= abA + 1_000) {
                                                toast = "B 点要在 A 点之后至少 1 秒"
                                            } else {
                                                abB = pos
                                                toast = "A-B 循环已开启"
                                            }
                                        },
                                    )
                                    if (abA >= 0 || abB >= 0) {
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("清除循环") },
                                            onClick = {
                                                abOpen = false
                                                abA = -1L
                                                abB = -1L
                                                toast = "已清除 A-B 循环"
                                            },
                                        )
                                    }
                                }
                            }
                            AliStageMenu(expanded = sleepOpen, onDismissRequest = { sleepOpen = false }) {
                                app.ailiaili.core.model.SLEEP_TIMER_OPTIONS.forEach { minutes ->
                                    if (minutes == 0 && (liveRoom != null || local != null)) return@forEach
                                    DropdownMenuItem(
                                        text = {
                                            Text(app.ailiaili.core.model.sleepTimerLabel(minutes) + if (sleepMinutes == minutes) " ✓" else "")
                                        },
                                        onClick = {
                                            sleepOpen = false
                                            sleepMinutes = minutes
                                            sleepDeadline = if (minutes > 0) epochMs() + minutes * 60_000L else 0L
                                            toast = if (minutes == 0) "将在播完当前视频后暂停" else "将在 $minutes 分钟后暂停播放"
                                        },
                                    )
                                }
                                if (sleepMinutes >= 0) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("取消定时") },
                                        onClick = {
                                            sleepOpen = false
                                            sleepMinutes = -1
                                            sleepDeadline = 0L
                                            sleepRemainText = ""
                                            toast = "已取消定时关闭"
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onToggleFullscreen = {
                        fullscreen = !fullscreen
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    modifier = transportInsets,
                )
            }
            if (showChrome && volumeOpen) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(transportInsets)
                        .padding(start = 16.dp, end = 16.dp, bottom = transportClearance)
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .aliMaterial(AliMaterial.Playback, MaterialTheme.shapes.large, opacity = .84f)
                        .padding(start = 4.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AliStageIconButton(
                        if (volume <= 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        if (volume <= 0f) "解除静音" else "静音",
                        onClick = ::toggleMute,
                        overVideo = true,
                    )
                    AliStageSeekBar(
                        value = volume,
                        onValueChange = { setVolume(it) },
                        onValueChangeFinished = {},
                        modifier = Modifier.weight(1f).height(44.dp),
                    )
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        color = AliColors.StageInk,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = PlayerCaptionShadow, fontFeatureSettings = "tnum"),
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
            // 弹幕发送条：浮在控制栏上方，与音量滑条同一套浮层约定。
            if (showChrome && dmBarOpen && liveRoom == null && local == null) {
                val playbackAccent = aliPlaybackAccent()
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(transportInsets)
                        .imePadding()
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = transportClearance)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box {
                        var dmStyleOpen by remember { mutableStateOf(false) }
                        TextButton(onClick = { dmStyleOpen = true }, shape = MaterialTheme.shapes.small) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(Color(dmColor or 0xFF000000.toInt()))
                                    .border(1.dp, AliColors.StageMuted, CircleShape),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                when (dmMode) {
                                    4 -> "底部"
                                    5 -> "顶部"
                                    else -> "滚动"
                                },
                                color = AliColors.StageInk,
                                style = MaterialTheme.typography.labelLarge.copy(shadow = PlayerCaptionShadow),
                            )
                        }
                        AliStageMenu(expanded = dmStyleOpen, onDismissRequest = { dmStyleOpen = false }) {
                            listOf(1 to "滚动", 5 to "顶部", 4 to "底部").forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (dmMode == mode) " ✓" else "") },
                                    onClick = { dmMode = mode; dmStyleOpen = false },
                                )
                            }
                            HorizontalDivider()
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    0xFFFFFF to "白", 0xFE0302 to "红", 0xFF7204 to "橙",
                                    0xFFAA02 to "黄", 0x00CD00 to "绿", 0x00A0EA to "蓝",
                                    0xCC0273 to "紫",
                                ).forEach { (c, name) ->
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(c or 0xFF000000.toInt()))
                                            .then(
                                                if (dmColor == c) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                            )
                                            .clickable { dmColor = c },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = dmDraft,
                        onValueChange = { if (it.length <= 100) dmDraft = it },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { composing = it.isFocused },
                        placeholder = { Text("发个友善的弹幕吧（最长 100 字）", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = MaterialTheme.shapes.small,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AliColors.StageInk,
                            unfocusedTextColor = AliColors.StageInk,
                            focusedBorderColor = playbackAccent,
                            unfocusedBorderColor = AliColors.StageMuted,
                            focusedPlaceholderColor = AliColors.StageMuted,
                            unfocusedPlaceholderColor = AliColors.StageMuted,
                            cursorColor = playbackAccent,
                        ),
                    )
                    if (dmSending) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = playbackAccent, strokeWidth = 2.dp)
                        }
                    } else {
                        AliStageIconButton(
                            Icons.AutoMirrored.Filled.Send,
                            "发送弹幕",
                            onClick = { sendDanmakuNow() },
                            enabled = dmDraft.isNotBlank(),
                            selected = dmDraft.isNotBlank(),
                            overVideo = true,
                        )
                    }
                    AliStageIconButton(Icons.Filled.Close, "收起弹幕输入", onClick = { dmBarOpen = false }, overVideo = true)
                }
            }
            }
        }
        }

        @Composable
        fun Details(modifier: Modifier) {
            val episodes = detail?.episodes.orEmpty()
            val showingSeason = panel == "season" && episodes.size > 1
            val shown = when {
                showingSeason -> "season"
                panel == "comments" -> "comments"
                else -> "intro"
            }
            LaunchedEffect(shown, commentsFullscreen) { scroll.scrollTo(0) }
            Column(modifier.imePadding()) {
                if (liveRoom == null && local == null && !commentsFullscreen) {
                    BiliContentTabs(
                        labels = listOf("简介", if (commentTotal > 0) "评论 ${formatCount(commentTotal.toLong())}" else "评论"),
                        selected = if (showingSeason) -1 else if (shown == "comments") 1 else 0,
                        onSelect = { panel = if (it == 0) "intro" else "comments" },
                        actionLabel = if (episodes.size > 1) "合集" else null,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        actionOn = showingSeason,
                        onAction = { panel = if (panel == "season") "intro" else "season" },
                    )
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .smoothWheelScroll(scroll, LocalWheelSpeed.current)
                        .verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                if (shown == "intro") {
                VideoHeader(
                    title = title,
                    detail = detail,
                    onlineText = onlineText,
                    onCopyTitle = {
                        clipboard.setText(AnnotatedString(title))
                        toast = "标题已复制"
                    },
                )
                val liveRoomNow = liveRoomInfo
                if (liveRoomNow != null && liveRoomNow.ownerName.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (liveRoomNow.ownerFace.isNotBlank()) {
                                AsyncImage(
                                    model = liveRoomNow.ownerFace,
                                    contentDescription = liveRoomNow.ownerName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = liveRoomNow.ownerMid > 0) { onSpace(liveRoomNow.ownerMid) },
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    liveRoomNow.ownerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.clickable(enabled = liveRoomNow.ownerMid > 0) {
                                        onSpace(liveRoomNow.ownerMid)
                                    },
                                )
                                if (liveRoomNow.area.isNotBlank()) {
                                    Text(
                                        liveRoomNow.area,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (liveRoomNow.officialVerify.isNotBlank()) {
                                    Text(
                                        liveRoomNow.officialVerify,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                MetaRow(
                                    items = buildList {
                                        add(MetaSpec(Icons.Filled.Visibility, "${formatCount(liveRoomNow.online)} 人在看", MetricTone.Online))
                                        if (liveRoomNow.fans > 0) {
                                            add(MetaSpec(Icons.Filled.Person, "${formatCount(liveRoomNow.fans)} 粉丝", MetricTone.Favorite))
                                        } else {
                                            add(MetaSpec(Icons.Filled.Favorite, "${formatCount(liveRoomNow.attention)} 关注", MetricTone.Favorite))
                                        }
                                        val start = livePlayInfo?.liveStartSec ?: 0
                                        if (start > 0) {
                                            val minutes = ((epochMs() / 1000 - start) / 60).coerceAtLeast(0)
                                            val text = if (minutes >= 60) "已开播 ${minutes / 60} 小时 ${minutes % 60} 分" else "已开播 $minutes 分钟"
                                            add(MetaSpec(Icons.Filled.Schedule, text, MetricTone.Neutral))
                                        }
                                    },
                                    maxItems = 3,
                                )
                            }
                            val liveMid = liveRoomNow.ownerMid
                            val liveMine = AppGraph.repo.session.collectAsState().value.dedeUserId?.toLongOrNull()
                            if (liveMid > 0 && liveMid != liveMine) {
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = {
                                    scope.launch {
                                        val next = !followed
                                        AppGraph.repo.follow(liveMid, next).onSuccess {
                                            followed = next
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            toast = if (next) "已关注主播" else "已取消关注"
                                        }.onFailure { toast = it.message ?: "关注失败" }
                                    }
                                }) { Text(if (followed) "已关注" else "关注") }
                            }
                        }
                    }
                }
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
                                    model = app.ailiaili.core.model.cdnThumb(face, 96),
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
                        Act(
                            Icons.Filled.ThumbUp,
                            actLabel("点赞", detail?.likeCount ?: 0),
                            Modifier.weight(1f),
                            liked,
                            likeAccent,
                            onLongClick = { tripleCombo() },
                        ) {
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
                        Act(Icons.Filled.Download, "缓存", Modifier.weight(1f)) {
                            if (detail != null) cacheOpen = true
                        }
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
                }
                when {
                    local != null -> {
                        AliStagePanel(Modifier.fillMaxWidth()) {
                            Text(
                                "离线播放",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (local.audioPath == null) "本地视频文件" else "本地视频及独立音轨",
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (local.bvid.isNotBlank()) {
                                if (onOpenOriginal != null) {
                                    TextButton(onClick = { onOpenOriginal(engine.currentPosition.coerceAtLeast(0L)) }) {
                                        Text("打开原视频")
                                    }
                                }
                            }
                        }
                    }
                    liveRoom != null -> {
                        BiliSectionTitle("直播弹幕")
                        LiveChatList(liveChat)
                        if (AppGraph.repo.session.collectAsState().value.loggedIn) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedTextField(
                                    value = liveMsgDraft,
                                    onValueChange = { if (it.length <= 30) liveMsgDraft = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { composing = it.isFocused },
                                    placeholder = { Text("发个友善的弹幕吧（最长 30 字）", fontSize = 13.sp) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                )
                                if (liveMsgSending) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { sendLiveMsgNow() }, enabled = liveMsgDraft.isNotBlank()) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "发送",
                                            tint = if (liveMsgDraft.isNotBlank()) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "登录后可以在直播间发言",
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    shown == "intro" -> {
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
                        // 关掉相关视频后简介是页面唯一内容：整段展开，不再给折叠按钮。
                        // settings 是响应式读取，设置里切开关立即生效。
                        val descLong = settings.showRelated && (desc.length > 80 || desc.count { it == '\n' } >= 4)
                        AliStagePanel(
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
                                else -> LinkifiedText(
                                    text = desc,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    maxLines = if (descOpen || !settings.showRelated) Int.MAX_VALUE else 4,
                                    onOpenLink = { url, linkBvid ->
                                        if (linkBvid != null) {
                                            onOpenVideo(VideoItem(bvid = linkBvid))
                                        } else {
                                            openUrl(url)
                                        }
                                    },
                                )
                            }
                        }
                        val relatedFiltered = app.ailiaili.core.designsystem.rememberFilteredVideos(related)
                        val relatedShown = if (settings.showRelated) relatedFiltered else emptyList()
                        if (relatedShown.isNotEmpty()) {
                            BiliSectionTitle("相关推荐")
                            relatedShown.take(12).forEach { item ->
                                key(item.stableKey) {
                                    VideoListRow(
                                        title = item.title,
                                        cover = item.cover,
                                        onClick = { onOpenVideo(item) },
                                        coverWidth = 112.dp,
                                        aid = if (item.isLive) 0 else item.aid,
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
                    shown == "comments" -> {
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
                            IconButton(
                                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "回到顶部",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                            BiliFilterChip(
                                selected = onlyUpComments,
                                onClick = { onlyUpComments = !onlyUpComments },
                                label = { Text("只看UP") },
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
                        // 只看 UP：本地过滤已加载页（接口 up 标记）；整页没有 UP 评论时
                        // 给提示，「更多评论」仍可继续翻页找。
                        val visibleComments = if (onlyUpComments) comments.filter { it.up } else comments
                        if (onlyUpComments && comments.isNotEmpty() && visibleComments.isEmpty()) {
                            Text(
                                "这一页还没有 UP 主的评论，点「更多评论」继续找",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        visibleComments.forEachIndexed { index, c ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
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
                                onPreviewImage = { urls, picIndex, name ->
                                    commentImagePreview = Triple(urls, picIndex, name)
                                },
                                onOpenLink = { url, linkBvid ->
                                    if (linkBvid != null) {
                                        onOpenVideo(VideoItem(bvid = linkBvid))
                                    } else {
                                        openUrl(url)
                                    }
                                },
                                onOpenDetail = { target ->
                                    val aid = detail?.aid ?: seedAid
                                    if (aid > 0) onOpenCommentDetail(aid, target)
                                },
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
                    shown == "season" -> {
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
        }
        when {
            fullscreen -> BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Stage(Modifier.fillMaxSize())
                if (commentsFullscreen) {
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 480.dp)
                            .fillMaxWidth(if (maxWidth >= 800.dp) 0.48f else 0.92f),
                    ) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("评论", modifier = Modifier.weight(1f))
                                IconButton(onClick = { commentsFullscreen = false }) {
                                    Icon(Icons.Filled.Close, "关闭评论")
                                }
                            }
                            Details(Modifier.weight(1f))
                        }
                    }
                }
            }
            splitPlayer -> Row(
                Modifier.weight(1f).fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Stage(Modifier.weight(1.85f).fillMaxHeight().clip(MaterialTheme.shapes.medium))
                Surface(
                    Modifier.weight(1f).fillMaxHeight().aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    if (detailsReady) Details(Modifier.fillMaxSize())
                }
            }
            else -> {
                Stage(Modifier.fillMaxWidth())
                if (detailsReady) Details(Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
        if (queuePanelOpen && playQueue.size > 1) {
            AliStageDialog(
                onDismissRequest = { queuePanelOpen = false },
                title = { Text(if (seasonQueue) "选集 · ${playQueue.size} 集" else "播放队列 · ${playQueue.size} 条") },
                text = {
                    val queueScroll = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (curQueueIndex - 1).coerceAtLeast(0))
                    LazyColumn(
                        Modifier.height(360.dp).smoothWheelScroll(queueScroll, LocalWheelSpeed.current),
                        state = queueScroll,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(playQueue) { index, item ->
                            val current = index == curQueueIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else Color.Transparent,
                                    )
                                    .clickable {
                                        queuePanelOpen = false
                                        if (!current) {
                                            curQueueIndex = index
                                            openQueueItem(item)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = if (current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    item.title.ifBlank { item.bvid },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 13.sp,
                                    color = if (current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (current) {
                                    Text("播放中", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { queuePanelOpen = false }) { Text("关闭") }
                },
            )
        }
        if (cacheOpen) {
            detail?.let { source ->
                PlayerDownloadDialog(
                    detail = source,
                    info = info,
                    currentCid = page?.cid ?: source.cid,
                    episodeId = currentEpId,
                    defaultQn = settings.downloadQuality,
                    onDismiss = { cacheOpen = false },
                    onOpenQueue = onOpenDownloads,
                    onMessage = { toast = it },
                )
            }
        }
        if (danmakuTimingOpen) {
            AliStageDialog(
                onDismissRequest = { danmakuTimingOpen = false },
                title = { Text("弹幕时间偏移") },
                text = {
                    app.ailiaili.core.designsystem.DanmakuDelayControl(settings.danmakuDelayMs, onChange = { value ->
                        AppGraph.repo.updateSettings { it.copy(danmakuDelayMs = value) }
                    })
                },
                confirmButton = { TextButton(onClick = { danmakuTimingOpen = false }) { Text("完成") } },
            )
        }
        if (scaleOpen) {
            AliStageDialog(
                onDismissRequest = { scaleOpen = false },
                title = { Text("画面比例") },
                text = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerScale.entries.forEach { mode ->
                            BiliFilterChip(
                                selected = settings.playerScale == mode,
                                onClick = { AppGraph.repo.updateSettings { it.copy(playerScale = mode) } },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { scaleOpen = false }) { Text("完成") } },
            )
        }
        if (playbackInfoOpen) {
            PlaybackInfoDialog(info, local, liveRoom != null, desktop, engine, speed) {
                playbackInfoOpen = false
            }
        }
        if (coinOpen) {
            val balance = myCoins
            val canOne = balance != null && balance >= 1.0
            val canTwo = balance != null && balance >= 2.0 && !coined
            AliStageDialog(
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
        if (createFolderOpen) {
            app.ailiaili.core.designsystem.CreateFavoriteFolderDialog(onDismiss = { createFolderOpen = false }) { created ->
                createFolderOpen = false
                if (created != null) {
                    favDraft = favDraft + created.copy(favored = true)
                } else {
                    val aid = detail?.aid ?: 0L
                    scope.launch {
                        AppGraph.repo.videoFavFolders(aid).onSuccess { loaded ->
                            if (detail?.aid != aid) return@onSuccess
                            val existing = favDraft.associateBy { it.id }
                            val added = loaded.filterNot { it.id in existing }
                            favDraft = loaded.map { existing[it.id] ?: it.copy(favored = added.size == 1) }
                        }
                    }
                }
            }
        } else if (favOpen) {
            AliStageDialog(
                onDismissRequest = { favOpen = false },
                title = { Text("选择收藏夹") },
                text = {
                    val favScroll = rememberScrollState()
                    Column(Modifier.verticalScroll(favScroll).smoothWheelScroll(favScroll, LocalWheelSpeed.current)) {
                        TextButton(onClick = { createFolderOpen = true }) { Text("新建收藏夹") }
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

private enum class FlashKind { Play, Pause }

private enum class PlayerSurfaceDrag { Seek, Brightness, Volume }

/** 用户主动取消缓存：与真实失败区分，提示语不同且保留断点。 */

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

@Composable
private fun LiveChatList(messages: List<LiveDanmaku>) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (messages.isEmpty()) {
            Text(
                "等待弹幕…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }
        messages.takeLast(80).forEach { message ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (message.admin) {
                    BiliBadge(text = "房管", style = BiliBadgeStyle.Solid)
                } else if (message.guard > 0) {
                    BiliBadge(
                        text = when (message.guard) {
                            1 -> "总督"
                            2 -> "提督"
                            else -> "舰长"
                        },
                        tone = BiliBadgeTone.Warning,
                        style = BiliBadgeStyle.Solid,
                    )
                }
                if (message.medalName.isNotBlank()) {
                    BiliBadge(
                        text = "${message.medalName}${message.medalLevel}".take(12),
                        tone = BiliBadgeTone.Neutral,
                        style = BiliBadgeStyle.Soft,
                    )
                }
                if (message.user.isNotBlank()) {
                    Text(
                        message.user,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
                Text(
                    message.text,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun speedLabel(value: Float): String {
    val hundredths = (value * 100).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> "${whole}x"
        frac % 10 == 0 -> "$whole.${frac / 10}x"
        else -> "$whole.${frac.toString().padStart(2, '0')}x"
    }
}

@Composable
private fun VideoHeader(
    title: String,
    detail: VideoDetail?,
    onlineText: String?,
    onCopyTitle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            AliStageIconButton(Icons.Filled.ContentCopy, "复制标题", onCopyTitle)
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                metrics.forEach { MetaRow(listOf(MetaSpec(it.icon, "${it.value} ${it.label}", it.tone))) }
            }
        }
    }
}
