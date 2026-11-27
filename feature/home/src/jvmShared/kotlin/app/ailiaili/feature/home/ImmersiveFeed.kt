package app.ailiaili.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import app.ailiaili.core.designsystem.LocalShellDockClearance
import app.ailiaili.core.designsystem.aliAmbient
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.AliStageMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.BiliSearchEntry
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.appIconPainter
import app.ailiaili.core.designsystem.rememberFilteredVideos
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.UrlParser
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds

val LocalFeedPaging = compositionLocalOf<(((Int) -> Boolean)?) -> Unit> { {} }

@Composable
internal fun ImmersiveFeed(
    vm: HomeViewModel,
    feedKey: Any,
    active: Boolean,
    volume: Float,
    unread: Int,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
    onOpenLink: (UrlHit) -> Unit,
    onHistory: () -> Unit,
    onOffline: () -> Unit,
    onVideo: (VideoItem, Long) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onCommentDetail: (Long, Long) -> Unit,
) {
    val items = rememberFilteredVideos(vm.items).filter { it.bvid.isNotBlank() || it.isLive }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canPlay = active && lifecycle.isAtLeast(Lifecycle.State.RESUMED)

    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().aliAmbient()) {
        val overlay = !isDesktopHost() && maxWidth < 600.dp && maxHeight >= 440.dp &&
            maxHeight > maxWidth && LocalDensity.current.fontScale <= 1.35f
        val header: @Composable (Modifier) -> Unit = { modifier ->
            FeedHeader(vm, unread, onBrowse, onSearch, onMessages, onOpenLink, onHistory, onOffline, overlay, modifier)
        }
        val content: @Composable (Modifier) -> Unit = { modifier ->
            if (items.isEmpty()) {
                Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        vm.loading -> CircularProgressIndicator(Modifier.size(30.dp))
                        vm.error != null -> FeedNotice("内容加载失败", vm.error, "重新加载", vm::refresh)
                        vm.paginationError != null -> FeedNotice("下一页加载失败", null, "重试", vm::loadMore)
                        vm.items.isNotEmpty() ->
                            FeedNotice("当前内容已被屏蔽", null, "管理屏蔽", onBrowse)
                        else -> FeedNotice("暂时没有视频", null, "刷新", vm::refresh)
                    }
                }
                LaunchedEffect(canPlay, vm.loading, vm.hasMore, vm.paginationError) {
                    if (canPlay && vm.error == null && vm.paginationError == null &&
                        vm.items.isNotEmpty() && vm.hasMore && !vm.loading
                    ) vm.loadMore()
                }
            } else {
                key(feedKey, vm.feedRevision) {
                    FeedPager(
                        items, modifier, vm, active, canPlay, overlay, volume, onVideo, onAuthor, onCommentDetail,
                    )
                }
            }
        }
        if (overlay) {
            content(Modifier.fillMaxSize())
            header(Modifier.align(Alignment.TopCenter))
            val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            if (navigationInset > 0.dp) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navigationInset + 8.dp)
                        .background(Brush.verticalGradient(0f to colors.background.copy(alpha = 0f), .45f to colors.background)),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                header(Modifier.fillMaxWidth())
                content(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeedHeader(
    vm: HomeViewModel,
    unread: Int,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
    onOpenLink: (UrlHit) -> Unit,
    onHistory: () -> Unit,
    onOffline: () -> Unit,
    overlay: Boolean,
    modifier: Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val menuButton: @Composable () -> Unit = {
        Box {
            if (overlay) {
                Box(
                    Modifier.size(44.dp).aliMaterial(AliMaterial.Floating, CircleShape)
                        .clickable(role = Role.Button) { menu = true }
                        .semantics { contentDescription = "首页菜单" },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(appIconPainter(), null, Modifier.size(28.dp))
                }
            } else {
                AliStageIconButton(Icons.Filled.MoreHoriz, "首页菜单", { menu = true })
            }
            AliStageMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (overlay) FeedMenuEntry("网格浏览", Icons.Filled.GridView) { menu = false; onBrowse() }
                FeedMenuEntry("刷新片流", Icons.Filled.Refresh) { menu = false; vm.refresh() }
                FeedMenuEntry("粘贴链接", Icons.Filled.Link) {
                    menu = false
                    when (val hit = UrlParser.parse(clipboard.getText()?.text.orEmpty().trim())) {
                        is UrlHit.Keyword -> QuickActions.showToast("复制 B 站视频、直播间或 UP 主链接后再试")
                        else -> onOpenLink(hit)
                    }
                }
                FeedMenuEntry("历史", Icons.Filled.History) { menu = false; onHistory() }
                FeedMenuEntry("离线", Icons.Filled.Download) { menu = false; onOffline() }
                val label = if (unread > 0) "消息（${unread.coerceAtMost(99)}${if (unread > 99) "+" else ""}）" else "消息"
                FeedMenuEntry(label, Icons.AutoMirrored.Filled.Message) { menu = false; onMessages() }
            }
        }
    }
    val scrim = if (overlay) Modifier.drawWithCache {
        val shades = Brush.verticalGradient(
            0f to colors.background,
            .68f to colors.background.copy(alpha = .90f),
            1f to colors.background.copy(alpha = 0f),
        )
        onDrawBehind { drawRect(shades) }
    } else Modifier
    BoxWithConstraints(modifier.fillMaxWidth().then(scrim).statusBarsPadding()) {
        val expandedSearch = !overlay && maxWidth >= 860.dp
        if (overlay || maxWidth >= 680.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (overlay) 10.dp else 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(if (overlay) 5.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (overlay) {
                    menuButton()
                } else {
                    FeedBrand(Modifier.padding(start = 11.dp, end = 22.dp))
                }
                FeedTabs(
                    vm,
                    if (overlay) Modifier.weight(1f) else Modifier.widthIn(max = 380.dp).weight(1f, fill = false),
                    floating = overlay,
                )
                if (!overlay) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (expandedSearch) {
                            BiliSearchEntry("搜索视频、直播、番剧或 UP 主", onSearch, Modifier.widthIn(max = 300.dp).fillMaxWidth())
                        }
                    }
                    AliStageIconButton(Icons.Filled.GridView, "网格浏览", onBrowse)
                }
                if (!expandedSearch) {
                    val searchSurface = if (overlay) Modifier.aliMaterial(AliMaterial.Floating, CircleShape) else Modifier
                    AliStageIconButton(Icons.Filled.Search, "搜索", onSearch, modifier = searchSurface)
                }
                if (!overlay) menuButton()
            }
        } else {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedBrand(Modifier.weight(1f))
                    AliStageIconButton(Icons.Filled.GridView, "网格浏览", onBrowse)
                    AliStageIconButton(Icons.Filled.Search, "搜索", onSearch)
                    menuButton()
                }
                FeedTabs(vm, Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 10.dp))
            }
        }
        if (!overlay) {
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colors.outlineVariant)
        }
    }
}

@Composable
private fun FeedBrand(modifier: Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalShellDockClearance.current != null) Image(appIconPainter(), null, Modifier.size(30.dp))
        Text("片流", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedTabs(vm: HomeViewModel, modifier: Modifier, floating: Boolean = false) {
    AliStageTabs(
        labels = HomeTab.entries.map { it.label },
        selected = vm.tab.ordinal,
        onSelect = { vm.select(HomeTab.entries[it]) },
        modifier = modifier,
        floating = floating,
    )
}

@Composable
private fun FeedMenuEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun FeedPager(
    items: List<VideoItem>,
    modifier: Modifier,
    vm: HomeViewModel,
    active: Boolean,
    canPlay: Boolean,
    overlay: Boolean,
    volume: Float,
    onVideo: (VideoItem, Long) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onCommentDetail: (Long, Long) -> Unit,
) {
    val pager = rememberPagerState(initialPage = vm.feedPage.coerceIn(items.indices)) { items.size }
    val reducedMotion = LocalAliReducedMotion.current
    val scope = rememberCoroutineScope()
    val desktop = isDesktopHost()
    var commentItem by remember { mutableStateOf<VideoItem?>(null) }
    val page = pager.settledPage.coerceIn(items.indices)
    val next = page < items.lastIndex
    val previous = page > 0

    fun step(delta: Int): Boolean {
        if (pager.isScrollInProgress || commentItem != null) return false
        val destination = (page + delta).coerceIn(items.indices)
        if (page == destination) return false
        scope.launch {
            if (reducedMotion) pager.scrollToPage(destination)
            else pager.animateScrollToPage(destination)
        }
        return true
    }

    LaunchedEffect(page) { vm.feedPage = page }
    LaunchedEffect(canPlay, page, items.size, vm.loading, vm.hasMore, vm.paginationError, vm.error) {
        if (canPlay && page >= items.lastIndex - 2 && vm.hasMore &&
            !vm.loading && vm.paginationError == null && vm.error == null
        ) vm.loadMore()
    }
    val paginationRetry: (() -> Unit)? = when {
        !next && vm.paginationError != null -> vm::loadMore
        vm.error != null -> vm::refresh
        else -> null
    }
    val stepLatest by rememberUpdatedState(::step)
    val registerPaging = LocalFeedPaging.current
    if (desktop && active) {
        DisposableEffect(registerPaging) {
            registerPaging { stepLatest(it) }
            onDispose { registerPaging(null) }
        }
    }
    val desktopControls = if (desktop && active) Modifier.feedWheel(pager) { stepLatest(it) } else Modifier

    Box(modifier.fillMaxWidth().then(desktopControls)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].stableKey },
            flingBehavior = if (overlay) PagerDefaults.flingBehavior(pager, snapPositionalThreshold = .28f)
            else PagerDefaults.flingBehavior(pager),
            userScrollEnabled = commentItem == null,
        ) { index ->
            val item = items[index]
            FeedCard(
                item = item,
                overlay = overlay,
                active = active && index == page,
                autoplay = canPlay && !pager.isScrollInProgress && commentItem == null,
                muted = vm.feedMuted,
                volume = volume,
                onMute = { vm.feedMuted = !vm.feedMuted },
                onVideo = { position -> onVideo(item, position) },
                onAuthor = { onAuthor(it) },
                onComments = { commentItem = item },
                onDismiss = if (vm.tab == HomeTab.Recommend) ({ vm.dismiss(item) }) else null,
                onPrevious = if (previous) ({ step(-1) }) else null,
                onNext = if (next) ({ step(1) }) else null,
                onRetryPage = paginationRetry,
            )
        }
        commentItem?.let { item ->
            FeedComments(item, onClose = { commentItem = null }, onCommentDetail = { aid, rpid ->
                commentItem = null
                onCommentDetail(aid, rpid)
            })
        }
    }
}

private fun Modifier.feedWheel(
    pager: PagerState,
    step: (Int) -> Boolean,
): Modifier = pointerInput(pager) {
    awaitPointerEventScope {
        var lastStep = TimeSource.Monotonic.markNow() - 380.milliseconds
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val movement = event.changes.firstOrNull() ?: continue
            val delta = movement.scrollDelta
            if (delta.y == 0f || abs(delta.x) > abs(delta.y)) continue
            val direction = if (delta.y > 0) 1 else -1
            if (lastStep.elapsedNow() >= 380.milliseconds && step(direction)) lastStep = TimeSource.Monotonic.markNow()
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
internal fun FeedNotice(title: String, detail: String?, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!detail.isNullOrBlank()) {
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        TextButton(onClick = onAction) { Text(action) }
    }
}
