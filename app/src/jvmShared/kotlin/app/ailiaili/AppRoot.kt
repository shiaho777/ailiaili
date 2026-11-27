package app.ailiaili

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import app.ailiaili.core.designsystem.AliNavigationMotion
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.designsystem.AliStageNotice
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.aliScreenTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.ailiaili.core.designsystem.BiliBottomBar
import app.ailiaili.core.designsystem.BiliChrome
import app.ailiaili.core.designsystem.BiliLoadingState
import app.ailiaili.core.designsystem.BiliRail
import app.ailiaili.core.designsystem.BiliRailItem
import app.ailiaili.core.designsystem.BiliShell
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.LocalWindowForm
import app.ailiaili.core.designsystem.PageRefresh
import app.ailiaili.core.designsystem.ProvideWindowForm
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.toChrome
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.exitApp
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.model.DISCLAIMER_VERSION
import app.ailiaili.core.model.StartupPage
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.bangumiPlayTitle
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.feature.dynamic.DynamicRoute
import app.ailiaili.core.designsystem.PlatformThemeEffects
import app.ailiaili.feature.home.HomeRoute
import app.ailiaili.feature.home.LocalFeedPaging
import app.ailiaili.feature.library.CacheRoute
import app.ailiaili.feature.library.FavVideosRoute
import app.ailiaili.feature.library.FavoritesRoute
import app.ailiaili.feature.library.HistoryRoute
import app.ailiaili.feature.library.WatchLaterRoute
import app.ailiaili.feature.login.LoginRoute
import app.ailiaili.feature.messages.ChatRoute
import app.ailiaili.feature.messages.MessagesRoute
import app.ailiaili.feature.mine.MineRoute
import app.ailiaili.feature.player.PlayerRoute
import app.ailiaili.feature.region.RegionPickerRoute
import app.ailiaili.feature.region.RegionRoute
import app.ailiaili.feature.search.SearchRoute
import app.ailiaili.feature.search.SeasonRoute
import app.ailiaili.feature.settings.SettingsRoute
import app.ailiaili.feature.space.SpaceRoute

sealed interface Dest {
    data object Home : Dest
    data object Dynamic : Dest
    data object Mine : Dest
    data object Settings : Dest
    data class Search(val keyword: String) : Dest
    data class Bangumi(val seasonId: Long, val title: String) : Dest
    data object Regions : Dest
    data class RegionRank(val tid: Int, val name: String) : Dest
    data class Space(val mid: Long) : Dest
    data object History : Dest
    data object Favorites : Dest
    data object FollowedSeasons : Dest
    data class Relations(val mid: Long, val followers: Boolean) : Dest
    data class FavList(val id: Long, val title: String) : Dest
    data object WatchLater : Dest
    data object Messages : Dest
    data class Chat(val talkerId: Long, val name: String) : Dest
    data object Cache : Dest
    data object Downloads : Dest
    data object Login : Dest
    data class Play(
        val bvid: String,
        val cid: Long = 0,
        val aid: Long = 0,
        val title: String = "",
        val cover: String = "",
        val author: String = "",
        val ownerMid: Long = 0,
        val durationSec: Int = 0,
        val viewCount: Long = 0,
        val likeCount: Long = 0,
        val danmakuCount: Long = 0,
        val coinCount: Long = 0,
        val favoriteCount: Long = 0,
        val ownerFace: String = "",
        val pubdate: Long = 0,
        /** 番剧集 id：>0 时取流走 PGC 链路。 */
        val epId: Long = 0,
        /** 列表「播放全部」带入的顺序连播队列；空 = 单视频。 */
        val queue: List<app.ailiaili.core.model.QueueItem> = emptyList(),
        val queueIndex: Int = 0,
        val startAtMs: Long? = null,
        val startPage: Int? = null,
    ) : Dest
    data class Live(val roomId: Long, val title: String) : Dest
    data class Local(val bvid: String, val cid: Long? = null) : Dest
    data class DynamicPost(val id: String) : Dest
    data class CommentDetail(val aid: Long, val rpid: Long) : Dest
}

enum class NavKind { Push, Pop, Tab }

class ShellViewModel(private val saved: SavedStateHandle, startup: StartupPage = StartupPage.Home) : ViewModel() {
    var stack by mutableStateOf(saved.get<String>(KEY)?.let(::decodeStack) ?: listOf(when (startup) {
        StartupPage.Home -> Dest.Home
        StartupPage.Regions -> Dest.Regions
        StartupPage.Dynamic -> Dest.Dynamic
        StartupPage.Mine -> Dest.Mine
    }))
        private set
    var kind by mutableStateOf(NavKind.Tab)
        private set

    val current: Dest get() = stack.last()

    fun tab(dest: Dest) {
        kind = NavKind.Tab
        stack = listOf(dest)
        persist()
    }

    fun push(dest: Dest) {
        kind = NavKind.Push
        stack = stack + dest
        persist()
    }

    fun pop() {
        if (stack.size <= 1) return
        kind = NavKind.Pop
        stack = stack.dropLast(1)
        persist()
    }

    private fun persist() {
        saved[KEY] = stack.joinToString("\n") { encode(it) }
    }

    private companion object {
        const val KEY = "stack"
    }
}

private fun encode(dest: Dest): String = when (dest) {
    Dest.Home -> "home"
    Dest.Dynamic -> "dynamic"
    Dest.Mine -> "mine"
    Dest.Settings -> "settings"
    is Dest.Search -> "search\t${dest.keyword.oneLine()}"
    is Dest.Bangumi -> "bangumi\t${dest.seasonId}\t${dest.title.oneLine()}"
    Dest.Regions -> "regions"
    is Dest.RegionRank -> "region\t${dest.tid}\t${dest.name.oneLine()}"
    is Dest.Space -> "space\t${dest.mid}"
    Dest.History -> "history"
    Dest.Favorites -> "favorites"
    Dest.FollowedSeasons -> "followedSeasons"
    is Dest.Relations -> "relations\t${dest.mid}\t${dest.followers}"
    is Dest.FavList -> "fav\t${dest.id}\t${dest.title.oneLine()}"
    Dest.WatchLater -> "later"
    Dest.Messages -> "messages"
    is Dest.Chat -> "chat\t${dest.talkerId}\t${dest.name.oneLine()}"
    Dest.Cache -> "cache"
    Dest.Downloads -> "downloads"
    Dest.Login -> "login"
    is Dest.Play -> listOf(
        "play4",
        dest.bvid,
        dest.cid.toString(),
        dest.aid.toString(),
        dest.durationSec.toString(),
        dest.ownerMid.toString(),
        dest.viewCount.toString(),
        dest.likeCount.toString(),
        dest.danmakuCount.toString(),
        dest.coinCount.toString(),
        dest.favoriteCount.toString(),
        dest.pubdate.toString(),
        dest.title.oneLine(),
        dest.author.oneLine(),
        dest.cover.oneLine(),
        dest.ownerFace.oneLine(),
        dest.epId.toString(),
        encodeQueue(dest.queue),
        dest.queueIndex.toString(),
        dest.startAtMs?.toString().orEmpty(),
        dest.startPage?.toString().orEmpty(),
    ).joinToString("\t")
    is Dest.Live -> "live\t${dest.roomId}\t${dest.title.oneLine()}"
    is Dest.Local -> "local\t${dest.bvid}\t${dest.cid ?: ""}"
    is Dest.DynamicPost -> "dynpost\t${dest.id}"
    is Dest.CommentDetail -> "cdetail\t${dest.aid}\t${dest.rpid}"
}

private fun decodeStack(raw: String): List<Dest> {
    val items = raw.lineSequence().map { decode(it) }.toList()
    return items.ifEmpty { listOf(Dest.Home) }
}

private fun decode(line: String): Dest {
    val parts = line.split('\t')
    return when (parts.firstOrNull()) {
        "dynamic" -> Dest.Dynamic
        "mine" -> Dest.Mine
        "settings" -> Dest.Settings
        "search" -> Dest.Search(parts.getOrElse(1) { "" })
        "bangumi" -> Dest.Bangumi(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "" })
        "regions" -> Dest.Regions
        "region" -> Dest.RegionRank(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrElse(2) { "分区" })
        "space" -> Dest.Space(parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        "history" -> Dest.History
        "favorites" -> Dest.Favorites
        "followedSeasons" -> Dest.FollowedSeasons
        "relations" -> Dest.Relations(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrNull(2) == "true")
        "fav" -> Dest.FavList(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "收藏" })
        "later" -> Dest.WatchLater
        "messages" -> Dest.Messages
        "chat" -> Dest.Chat(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "" })
        "cache" -> Dest.Cache
        "downloads" -> Dest.Downloads
        "login" -> Dest.Login
        "play" -> Dest.Play(
            bvid = parts.getOrElse(1) { "" },
            cid = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            aid = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            durationSec = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            ownerMid = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            title = parts.getOrElse(6) { "" },
            author = parts.getOrElse(7) { "" },
            cover = parts.getOrElse(8) { "" },
        )
        "play2" -> Dest.Play(
            bvid = parts.getOrElse(1) { "" },
            cid = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            aid = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            durationSec = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            ownerMid = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            viewCount = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
            likeCount = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
            danmakuCount = parts.getOrNull(8)?.toLongOrNull() ?: 0L,
            coinCount = parts.getOrNull(9)?.toLongOrNull() ?: 0L,
            favoriteCount = parts.getOrNull(10)?.toLongOrNull() ?: 0L,
            pubdate = parts.getOrNull(11)?.toLongOrNull() ?: 0L,
            title = parts.getOrElse(12) { "" },
            author = parts.getOrElse(13) { "" },
            cover = parts.getOrElse(14) { "" },
            ownerFace = parts.getOrElse(15) { "" },
        )
        "play3", "play4" -> Dest.Play(
            bvid = parts.getOrElse(1) { "" },
            cid = parts.getOrNull(2)?.toLongOrNull() ?: 0L,
            aid = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            durationSec = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            ownerMid = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
            viewCount = parts.getOrNull(6)?.toLongOrNull() ?: 0L,
            likeCount = parts.getOrNull(7)?.toLongOrNull() ?: 0L,
            danmakuCount = parts.getOrNull(8)?.toLongOrNull() ?: 0L,
            coinCount = parts.getOrNull(9)?.toLongOrNull() ?: 0L,
            favoriteCount = parts.getOrNull(10)?.toLongOrNull() ?: 0L,
            pubdate = parts.getOrNull(11)?.toLongOrNull() ?: 0L,
            title = parts.getOrElse(12) { "" },
            author = parts.getOrElse(13) { "" },
            cover = parts.getOrElse(14) { "" },
            ownerFace = parts.getOrElse(15) { "" },
            epId = parts.getOrNull(16)?.toLongOrNull() ?: 0L,
            queue = decodeQueue(parts.getOrElse(17) { "" }),
            queueIndex = parts.getOrNull(18)?.toIntOrNull() ?: 0,
            startAtMs = parts.getOrNull(19)?.toLongOrNull()?.takeIf { it > 0 || (it == 0L && parts[0] == "play4") },
            startPage = parts.getOrNull(20)?.toIntOrNull()?.takeIf { it > 0 },
        )
        "live" -> Dest.Live(parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrElse(2) { "直播" })
        "local" -> Dest.Local(parts.getOrElse(1) { "" }, parts.getOrNull(2)?.toLongOrNull())
        "dynpost" -> Dest.DynamicPost(parts.getOrElse(1) { "" })
        "cdetail" -> Dest.CommentDetail(
            parts.getOrNull(1)?.toLongOrNull() ?: 0L,
            parts.getOrNull(2)?.toLongOrNull() ?: 0L,
        )
        else -> Dest.Home
    }
}

private fun String.oneLine(): String = replace('\n', ' ').replace('\t', ' ')

// 队列序列化：条目间用竖线分隔，字段间用波浪号分隔（bvid~cid~epId~title）。
// 只存定位与展示必需字段；标题里的分隔符先清洗。空队列编码为空串。
private fun encodeQueue(queue: List<app.ailiaili.core.model.QueueItem>): String =
    queue.joinToString("|") { item ->
        listOf(
            item.bvid,
            item.cid.toString(),
            item.epId.toString(),
            item.title.replace('|', ' ').replace('~', ' ').oneLine(),
        ).joinToString("~")
    }

private fun decodeQueue(raw: String): List<app.ailiaili.core.model.QueueItem> {
    if (raw.isBlank()) return emptyList()
    return raw.split('|').mapNotNull { entry ->
        val f = entry.split('~')
        val bv = f.getOrElse(0) { "" }
        if (bv.isBlank()) return@mapNotNull null
        app.ailiaili.core.model.QueueItem(
            bvid = bv,
            cid = f.getOrNull(1)?.toLongOrNull() ?: 0L,
            epId = f.getOrNull(2)?.toLongOrNull() ?: 0L,
            title = f.getOrElse(3) { "" },
        )
    }
}

private fun Dest.isTab(): Boolean =
    this is Dest.Regions || this is Dest.Home || this is Dest.Dynamic || this is Dest.Mine || this is Dest.Settings

internal fun Dest.showsShellChrome(): Boolean = isTab()

internal val tabDests: List<Dest> = listOf(Dest.Regions, Dest.Home, Dest.Dynamic, Dest.Mine, Dest.Settings)

internal fun railSelection(base: Dest): Int = tabDests.indexOf(base).takeIf { it >= 0 } ?: tabDests.indexOf(Dest.Home)

internal fun Dest.isPlayerLayer(): Boolean =
    this is Dest.Play || this is Dest.Live || this is Dest.Local

internal fun composeOverlayLayer(overlay: Dest?, held: Dest?): Boolean =
    overlay != null || held != null

/**
 * 播放层保活：只要栈内还有播放层就返回它（不只是栈顶），播放器组合不被销毁，
 * 从播放器点进 UP 空间/标签搜索/评论详情时播放继续。
 * base 取栈顶的非播放层（用户当前浏览的页面）；栈顶本身是播放层时取其下方。
 */
internal fun playerLayers(stack: List<Dest>): Pair<Dest, Dest?> {
    val top = stack.lastOrNull() ?: Dest.Home
    val player = stack.lastOrNull { it.isPlayerLayer() } ?: return top to null
    // 栈顶不是播放层（用户从播放器进了别的页面）：当前页就是栈顶，播放层保活在下层。
    if (!top.isPlayerLayer()) return top to player
    val playerIdx = stack.lastIndexOf(player)
    val base = stack.take(playerIdx).lastOrNull { !it.isPlayerLayer() } ?: Dest.Home
    return base to player
}

/** 播放层是否在栈顶：决定它可见可交互，还是保活隐藏在内容下层。 */
internal fun playerOnTop(stack: List<Dest>): Boolean = stack.lastOrNull()?.isPlayerLayer() == true

@Composable
fun AppRoot(openDownloads: Boolean = false, onDownloadsOpened: () -> Unit = {}) {
    ProvideWindowForm {
        AppShell(openDownloads, onDownloadsOpened)
    }
}

@Composable
private fun AppShell(openDownloads: Boolean, onDownloadsOpened: () -> Unit) {
    val vm = rememberShellViewModel()
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            if (vm.current != Dest.Downloads) vm.push(Dest.Downloads)
            onDownloadsOpened()
        }
    }
    val haptic = LocalHapticFeedback.current
    val settings by AppGraph.repo.settings.collectAsState()
    // 屏蔽规则只在启动时恢复一次，避免编辑关键词时被设置流回写打断。
    LaunchedEffect(Unit) { ContentFilter.load(settings) }
    val form = LocalWindowForm.current
    var rereadDisclaimer by remember { mutableStateOf(false) }
    var immersiveHome by rememberSaveable { mutableStateOf(false) }
    val (base, overlay) = playerLayers(vm.stack)
    val chrome = if (base.showsShellChrome()) settings.shellLayout.toChrome(form) else BiliChrome.None
    val railItems = listOf(
        BiliRailItem("分区", Icons.Filled.GridView, Icons.Outlined.GridView),
        BiliRailItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        BiliRailItem("动态", Icons.AutoMirrored.Filled.ViewList, Icons.AutoMirrored.Outlined.ViewList),
        BiliRailItem("我的", Icons.Filled.Person, Icons.Outlined.Person),
        BiliRailItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
    )
    val railIndex = railSelection(base)
    val motion = MaterialTheme.motionScheme
    val reducedMotion = LocalAliReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val routeOffset = with(LocalDensity.current) {
        if (isDesktopHost()) IntOffset(0, 10.dp.roundToPx())
        else IntOffset((if (rtl) -26.dp else 26.dp).roundToPx(), 0)
    }
    var heldOverlay by remember { mutableStateOf<Dest?>(null) }
    var enterReady by remember { mutableStateOf(false) }
    LaunchedEffect(overlay) {
        if (overlay != null) {
            if (!enterReady) delay(32)
            heldOverlay = overlay
            enterReady = true
        } else {
            enterReady = false
        }
    }
    val liveOverlay = overlay.takeIf { enterReady }
    // 播放层保活：栈内还有播放层但用户已进别的页面时，播放器继续组合（引擎不断、
    // 音频继续），只是降到内容下层且不可交互；返回时立刻恢复画面。
    val playerAlive = overlay != null
    val playerVisible = playerOnTop(vm.stack)
    val baseSemantics = if (playerVisible && liveOverlay != null) Modifier.clearAndSetSemantics {} else Modifier
    val stageVisible = base == Dest.Home && immersiveHome
    val disclaimerOpen = settings.acceptedDisclaimerVersion < DISCLAIMER_VERSION || rereadDisclaimer
    PlatformThemeEffects(MaterialTheme.colorScheme.surface.luminance() < .5f)

    BackHandler(enabled = vm.stack.size > 1 && !playerVisible) { vm.pop() }
    BackHandler(enabled = stageVisible && !playerAlive && !disclaimerOpen) { immersiveHome = false }

    val sectionState = rememberSaveableStateHolder()
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().then(baseSemantics)) {
            AnimatedContent(
                targetState = base,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                transitionSpec = {
                    val transition = when (vm.kind) {
                        NavKind.Push -> AliNavigationMotion.Open
                        NavKind.Pop -> AliNavigationMotion.Back
                        NavKind.Tab -> AliNavigationMotion.Tab
                    }
                    aliScreenTransition(transition, reducedMotion, routeOffset)
                },
                label = "route",
            ) { dest ->
                val content: @Composable () -> Unit = {
                    Route(
                        dest,
                        onPush = vm::push,
                        onPop = vm::pop,
                        onRereadDisclaimer = { rereadDisclaimer = true },
                        onOpenDynamic = { vm.tab(Dest.Dynamic) },
                        homeImmersive = immersiveHome,
                        onHomeImmersive = { immersiveHome = it },
                        homeActive = dest == base && !playerAlive && heldOverlay == null && !disclaimerOpen,
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .then(if (dest != base) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    if (dest.isTab() || dest is Dest.Space ||
                        dest == Dest.Messages || dest == Dest.Favorites || dest == Dest.History ||
                        dest == Dest.Cache || dest == Dest.Downloads
                    ) {
                        sectionState.SaveableStateProvider(encode(dest), content)
                    } else content()
                }
            }
        }
    }

    val overlayLayer: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            if (composeOverlayLayer(liveOverlay, heldOverlay)) {
                val effect = motion.fastEffectsSpec<Float>()
                AnimatedVisibility(
                    visible = liveOverlay != null,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(effect),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(effect),
                ) {
                    // 播放器只挂一份组合：栈内存在播放层就一直保留（引擎不断、音频继续）。
                    // 栈顶时可见可交互；用户进了别的页面时降到内容下层并透明、屏蔽输入，
                    // 返回时立刻恢复画面。只有真正退出播放（overlay 为空）才销毁。
                    val keepAlive = playerAlive && !playerVisible
                    Box(
                        Modifier
                            .fillMaxSize()
                            .alpha(if (keepAlive) 0f else 1f)
                            .then(if (keepAlive) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        (liveOverlay ?: heldOverlay)?.let { dest ->
                            key(dest) {
                                Route(
                                    dest,
                                    onPush = vm::push,
                                    onPop = vm::pop,
                                    onRereadDisclaimer = { rereadDisclaimer = true },
                                    onOpenDynamic = { vm.tab(Dest.Dynamic) },
                                )
                            }
                        }
                        DisposableEffect(Unit) {
                            onDispose { heldOverlay = null }
                        }
                    }
                }
            }
        }
    }

    val openTab: (Int) -> Unit = { index ->
        val destination = tabDests.getOrElse(index) { Dest.Home }
        if (destination == Dest.Home) immersiveHome = false
        vm.tab(destination)
    }
    // F5 刷新当前列表页：焦点挂在壳上，页面输入框/播放器先消费按键，不会抢。
    val shellFocus = remember { FocusRequester() }
    var feedPaging by remember { mutableStateOf<((Int) -> Boolean)?>(null) }
    val registerFeedPaging: (((Int) -> Boolean)?) -> Unit = remember { { feedPaging = it } }
    LaunchedEffect(stageVisible, playerAlive) {
        if (!playerAlive) runCatching { shellFocus.requestFocus() }
    }
    val quickToast = QuickActions.toast
    LaunchedEffect(quickToast) {
        if (quickToast != null) {
            delay(1800)
            QuickActions.dismissToast(quickToast)
        }
    }
    CompositionLocalProvider(
        LocalWheelSpeed provides settings.wheelSpeedPercent,
        LocalFeedPaging provides registerFeedPaging,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (
                        !playerVisible && base !is Dest.Search && !rereadDisclaimer &&
                        settings.acceptedDisclaimerVersion >= DISCLAIMER_VERSION &&
                        event.type == KeyEventType.KeyDown && event.key == Key.K &&
                        (event.isCtrlPressed xor event.isMetaPressed) && !event.isAltPressed && !event.isShiftPressed &&
                        isDesktopHost()
                    ) {
                        vm.push(Dest.Search(""))
                        return@onPreviewKeyEvent true
                    }
                    // 只认裸 F5：Ctrl/Shift/Alt/Meta+F5 是系统/浏览器组合键，
                    // 吞掉会破坏强制刷新等既有语义。
                    val bare = !event.isCtrlPressed && !event.isShiftPressed &&
                        !event.isAltPressed && !event.isMetaPressed
                    if (!bare || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (event.key == Key.F5) {
                        PageRefresh.refreshCurrent()
                        return@onPreviewKeyEvent true
                    }
                    if (!stageVisible || playerAlive || disclaimerOpen) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            immersiveHome = false
                            true
                        }
                        Key.DirectionUp, Key.PageUp -> feedPaging?.invoke(-1) == true
                        Key.DirectionDown, Key.PageDown -> feedPaging?.invoke(1) == true
                        else -> false
                    }
                }
                .focusRequester(shellFocus)
                .focusable(),
        ) {
            BiliShell(
                chrome = chrome,
                rail = { wide ->
                    BiliRail(
                        items = railItems,
                        selected = railIndex,
                        wide = wide,
                        onSelect = openTab,
                        modifier = baseSemantics,
                    )
                },
                bottomBar = {
                    Box(baseSemantics) {
                        BiliBottomBar(
                            items = railItems,
                            selected = railIndex,
                            onSelect = openTab,
                        )
                    }
                },
                overlay = overlayLayer,
                overlayBelowContent = playerAlive && !playerVisible,
                content = body,
            )
            if (quickToast != null) {
                Surface(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                        .widthIn(max = 420.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        quickToast,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (settings.acceptedDisclaimerVersion < DISCLAIMER_VERSION || rereadDisclaimer) {
        val accepted = settings.acceptedDisclaimerVersion >= DISCLAIMER_VERSION
        AliStageDialog(
            onDismissRequest = {
                if (accepted) rereadDisclaimer = false else exitApp()
            },
            title = { Text("免责声明") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(DISCLAIMER.trim(), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (!accepted) {
                        AppGraph.repo.updateSettings { it.copy(acceptedDisclaimerVersion = DISCLAIMER_VERSION) }
                    }
                    rereadDisclaimer = false
                }) { Text(if (accepted) "关闭" else "同意并继续") }
            },
            dismissButton = if (accepted) {
                null
            } else {
                {
                    TextButton(onClick = { exitApp() }) { Text("不同意") }
                }
            },
        )
    }
}

@Composable
private fun Route(
    dest: Dest,
    onPush: (Dest) -> Unit,
    onPop: () -> Unit,
    onRereadDisclaimer: () -> Unit,
    onOpenDynamic: () -> Unit,
    homeImmersive: Boolean = true,
    onHomeImmersive: (Boolean) -> Unit = {},
    homeActive: Boolean = false,
) {
    /** 列表「播放全部」：整列表作为连播队列，从第一条起播。 */
    fun openPlayAll(items: List<VideoItem>) {
        val queue = items.filter { it.bvid.isNotBlank() && !it.isLive }.map {
            app.ailiaili.core.model.QueueItem(
                bvid = it.bvid,
                cid = it.cid,
                title = it.title,
                cover = it.cover,
                author = it.author,
                ownerMid = it.ownerMid,
                durationSec = it.durationSec,
            )
        }
        val first = queue.firstOrNull() ?: return
        onPush(
            Dest.Play(
                bvid = first.bvid,
                cid = first.cid,
                title = first.title,
                cover = first.cover,
                author = first.author,
                ownerMid = first.ownerMid,
                durationSec = first.durationSec,
                queue = queue,
                queueIndex = 0,
            ),
        )
    }

    fun openVideoAt(item: VideoItem, position: Long, episodeId: Long = 0) {
        when {
            item.isLive && item.roomId > 0 -> onPush(Dest.Live(item.roomId, item.title))
            item.bvid.isNotBlank() -> onPush(
                Dest.Play(
                    bvid = item.bvid,
                    cid = item.cid,
                    aid = item.aid,
                    title = item.title,
                    cover = item.cover,
                    author = item.author,
                    ownerMid = item.ownerMid,
                    durationSec = item.durationSec,
                    viewCount = item.viewCount,
                    likeCount = item.likeCount,
                    danmakuCount = item.danmakuCount,
                    coinCount = item.coinCount,
                    favoriteCount = item.favoriteCount,
                    ownerFace = item.ownerFace,
                    pubdate = item.pubdate,
                    epId = episodeId,
                    startAtMs = position.takeIf { it > 0 },
                ),
            )
        }
    }
    fun openVideo(item: VideoItem) = openVideoAt(item, 0L)
    fun openVideoLink(link: UrlHit.Video) = onPush(
        Dest.Play(bvid = link.bvid, startAtMs = link.positionMs, startPage = link.page),
    )

    fun openAuthor(item: VideoItem) {
        if (item.ownerMid > 0) onPush(Dest.Space(item.ownerMid))
    }
    when (dest) {
        Dest.Home -> HomeRoute(
            onVideo = ::openVideo,
            onAuthor = ::openAuthor,
            onSearch = { onPush(Dest.Search("")) },
            onMessages = { onPush(Dest.Messages) },
            onOpenLink = { hit ->
                when (hit) {
                    is UrlHit.Video -> openVideoLink(hit)
                    is UrlHit.Live -> openVideo(VideoItem(roomId = hit.roomId, title = "直播"))
                    is UrlHit.Space -> onPush(Dest.Space(hit.mid))
                    is UrlHit.Keyword -> Unit
                }
            },
            onHistory = { onPush(Dest.History) },
            onOffline = { onPush(Dest.Cache) },
            immersive = homeImmersive,
            onImmersive = onHomeImmersive,
            active = homeActive,
            onCommentDetail = { aid, rpid -> onPush(Dest.CommentDetail(aid, rpid)) },
            onFeedVideo = { item, position -> openVideoAt(item, position) },
            onResume = { openVideoAt(it.video, it.positionMs, it.episodeId) },
        )
        Dest.Dynamic -> DynamicRoute(
            onVideo = ::openVideo,
            onAuthor = ::openAuthor,
            onLogin = { onPush(Dest.Login) },
            onPost = { onPush(Dest.DynamicPost(it.id)) },
        )
        Dest.Mine -> MineRoute(
            onLogin = { onPush(Dest.Login) },
            onHistory = { onPush(Dest.History) },
            onFavorites = { onPush(Dest.Favorites) },
            onWatchLater = { onPush(Dest.WatchLater) },
            onCache = { onPush(Dest.Cache) },
            onSpace = { onPush(Dest.Space(it)) },
            onOpenFolder = { onPush(Dest.FavList(it.id, it.title)) },
            onOpenDynamic = onOpenDynamic,
            onRelations = { mid, followers -> onPush(Dest.Relations(mid, followers)) },
            onFollowedSeasons = { onPush(Dest.FollowedSeasons) },
        )
        Dest.Settings -> SettingsRoute(
            onLogout = {},
            onOpenCache = { onPush(Dest.Cache) },
            onOpenAuthor = { onPush(Dest.Space(697238372L)) },
            onOpenDisclaimer = onRereadDisclaimer,
        )
        is Dest.Search -> SearchRoute(
            dest.keyword,
            onPop,
            ::openVideo,
            ::openAuthor,
            onVideoLink = ::openVideoLink,
            onBangumi = { onPush(Dest.Bangumi(it.seasonId, it.title)) },
        )
        is Dest.Bangumi -> SeasonRoute(
            dest.seasonId,
            dest.title,
            onPop,
            onLogin = { onPush(Dest.Login) },
            onPlay = { ep, seasonTitle, episodes ->
                val queue = episodes.map {
                    app.ailiaili.core.model.QueueItem(
                        bvid = it.bvid,
                        cid = it.cid,
                        title = bangumiPlayTitle(seasonTitle, it.title, it.longTitle),
                        cover = it.cover,
                        durationSec = it.durationSec,
                        epId = it.epId,
                    )
                }
                onPush(
                    Dest.Play(
                        bvid = ep.bvid,
                        cid = ep.cid,
                        aid = ep.aid,
                        epId = ep.epId,
                        title = bangumiPlayTitle(seasonTitle, ep.title, ep.longTitle),
                        cover = ep.cover,
                        durationSec = ep.durationSec,
                        queue = queue,
                        queueIndex = episodes.indexOf(ep).coerceAtLeast(0),
                    ),
                )
            },
        )
        Dest.Regions -> RegionPickerRoute(
            onPick = { onPush(Dest.RegionRank(it.tid, it.name)) },
            onSearch = { onPush(Dest.Search("")) },
        )
        is Dest.RegionRank -> RegionRoute(dest.tid, dest.name, onPop, ::openVideo, ::openAuthor)
        is Dest.Space -> SpaceRoute(
            dest.mid,
            onPop,
            ::openVideo,
            onOpenFolder = { onPush(Dest.FavList(it.id, it.title)) },
            onPlayAll = ::openPlayAll,
            onRelations = { mid, followers -> onPush(Dest.Relations(mid, followers)) },
            onPost = { onPush(Dest.DynamicPost(it)) },
        )
        Dest.FollowedSeasons -> app.ailiaili.feature.search.FollowedSeasonsRoute(
            onBack = onPop,
            onSeason = { onPush(Dest.Bangumi(it.seasonId, it.title)) },
        )
        is Dest.Relations -> app.ailiaili.feature.space.RelationsRoute(
            mid = dest.mid,
            followers = dest.followers,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
        )
        Dest.History -> HistoryRoute(onPop, ::openVideo, ::openAuthor, onLogin = { onPush(Dest.Login) })
        Dest.Favorites -> FavoritesRoute(onPop, onLogin = { onPush(Dest.Login) }) { onPush(Dest.FavList(it.id, it.title)) }
        is Dest.FavList -> FavVideosRoute(
            dest.id, dest.title, onPop, ::openVideo, ::openAuthor,
            onLogin = { onPush(Dest.Login) },
            onPlayAll = ::openPlayAll,
        )
        Dest.WatchLater -> WatchLaterRoute(
            onPop, ::openVideo, ::openAuthor,
            onLogin = { onPush(Dest.Login) },
            onPlayAll = ::openPlayAll,
        )
        Dest.Messages -> MessagesRoute(
            onBack = onPop,
            onOpen = { onPush(Dest.Chat(it.talkerId, it.name)) },
            onLogin = { onPush(Dest.Login) },
        )
        is Dest.Chat -> ChatRoute(dest.talkerId, dest.name, onPop)
        Dest.Cache -> CacheRoute(onPop) { onPush(Dest.Local(it.bvid, it.cid)) }
        Dest.Downloads -> CacheRoute(onPop, openQueue = true) { onPush(Dest.Local(it.bvid, it.cid)) }
        Dest.Login -> LoginRoute(onBack = onPop, onSuccess = onPop)
        is Dest.Play -> PlayerRoute(
            bvid = dest.bvid,
            liveRoom = null,
            liveTitle = "",
            local = null,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
            seedCid = dest.cid,
            seedAid = dest.aid,
            seedTitle = dest.title,
            seedCover = dest.cover,
            seedAuthor = dest.author,
            seedOwnerMid = dest.ownerMid,
            seedDurationSec = dest.durationSec,
            seedViewCount = dest.viewCount,
            seedLikeCount = dest.likeCount,
            seedDanmakuCount = dest.danmakuCount,
            seedCoinCount = dest.coinCount,
            seedFavoriteCount = dest.favoriteCount,
            seedOwnerFace = dest.ownerFace,
            seedPubdate = dest.pubdate,
            seedEpId = dest.epId,
            queue = dest.queue,
            queueIndex = dest.queueIndex,
            startAtMs = dest.startAtMs,
            startPage = dest.startPage,
            onOpenVideo = ::openVideo,
            onTag = { onPush(Dest.Search(it)) },
            onOpenCommentDetail = { aid, comment -> onPush(Dest.CommentDetail(aid, comment.rpid)) },
            onOpenDownloads = { onPush(Dest.Downloads) },
        )
        is Dest.Live -> PlayerRoute(
            bvid = null,
            liveRoom = dest.roomId,
            liveTitle = dest.title,
            local = null,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
        )
        is Dest.Local -> {
            val lookup by produceState<Result<CacheEntry>?>(null, dest) {
                value = withContext(ioDispatcher) {
                    runCatching {
                        checkNotNull(AppGraph.cache.list().firstOrNull {
                            it.bvid == dest.bvid && (dest.cid == null || it.cid == dest.cid)
                        })
                    }
                }
            }
            val entry = lookup?.getOrNull()
            if (entry != null) {
                PlayerRoute(
                    bvid = null,
                    liveRoom = null,
                    liveTitle = "",
                    local = entry,
                    onBack = onPop,
                    onSpace = { onPush(Dest.Space(it)) },
                    onOpenOriginal = { position ->
                        onPush(
                            Dest.Play(
                                bvid = entry.bvid,
                                cid = entry.cid,
                                epId = entry.episodeId,
                                cover = entry.cover,
                                startAtMs = position.takeIf { it > 0 },
                            ),
                        )
                    },
                )
            } else {
                AliStageBody {
                    AliStagePageHeader("离线播放", onBack = onPop)
                    if (lookup == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            BiliLoadingState(label = "正在打开缓存…")
                        }
                    } else {
                        AliStageNotice(
                            "离线缓存不可用",
                            "文件可能已移动或删除。返回离线缓存页后刷新列表。",
                            Icons.Filled.Download,
                            actionLabel = "返回离线缓存",
                            onAction = onPop,
                        )
                    }
                }
            }
        }
        is Dest.DynamicPost -> app.ailiaili.feature.dynamic.DynamicDetailRoute(
            id = dest.id,
            seed = null,
            onBack = onPop,
            onAuthor = { onPush(Dest.Space(it)) },
            onVideo = ::openVideo,
        )
        is Dest.CommentDetail -> app.ailiaili.feature.player.CommentDetailRoute(
            aid = dest.aid,
            rootRpid = dest.rpid,
            seed = null,
            onBack = onPop,
            onSpace = { onPush(Dest.Space(it)) },
            onVideo = ::openVideo,
        )
    }
}
