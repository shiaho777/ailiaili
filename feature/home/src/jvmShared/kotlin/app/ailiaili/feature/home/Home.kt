package app.ailiaili.feature.home

import androidx.compose.foundation.layout.widthIn
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.AliStageTabs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.ResumePlayback
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.BiliFloatButton
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliPaginationFooter
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.FilterAutoLoad
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.BiliRuleChip
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.shellBottomClearance
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.model.ThemeMode
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.VideoItem

import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeRoute(
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
    onOpenLink: (UrlHit) -> Unit,
    onHistory: () -> Unit,
    onOffline: () -> Unit,
    immersive: Boolean,
    onImmersive: (Boolean) -> Unit,
    active: Boolean,
    onCommentDetail: (Long, Long) -> Unit,
    onFeedVideo: (VideoItem, Long) -> Unit,
    onResume: (ResumePlayback) -> Unit,
    vm: HomeViewModel = appViewModel { HomeViewModel() },
) {
    if (active) app.ailiaili.core.designsystem.RegisterPageRefresh(vm::refresh)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by AppGraph.repo.settings.collectAsState()
    LaunchedEffect(settings.recommendSource) { vm.onSourceChanged() }
    val feed = when (vm.tab) {
        HomeTab.Live -> vm.liveAreaId
        HomeTab.Recommend -> settings.recommendSource
        else -> vm.tab
    }
    val grid = key(feed) { rememberLazyGridState() }
    val session by AppGraph.repo.session.collectAsState()
    var unread by remember(session.dedeUserId) { mutableIntStateOf(0) }
    var importingBlocks by remember(session.dedeUserId) { mutableStateOf(false) }
    LaunchedEffect(session.dedeUserId, session.loggedIn) {
        if (!session.loggedIn) return@LaunchedEffect
        while (true) {
            AppGraph.repo.unreadMessages().onSuccess { unread = it }
            kotlinx.coroutines.delay(90_000)
        }
    }
    if (immersive) {
        ImmersiveFeed(
            vm, feed, active, settings.playerVolume, unread, { onImmersive(false) },
            onSearch, onMessages, onOpenLink, onHistory, onOffline, onFeedVideo, onAuthor, onCommentDetail,
        )
        return
    }

    val reducedMotion = LocalAliReducedMotion.current
    val away by remember(grid) {
        derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 240 }
    }
    val navBottom = shellBottomClearance()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BiliPageHeader(
                title = "首页",
                actions = {
                    BiliHeaderAction(Icons.Filled.PlayCircle, "片流", onClick = { onImmersive(true) })
                    val (themeIcon, themeLabel) = when (settings.theme) {
                        ThemeMode.System -> Icons.Filled.BrightnessAuto to "主题：跟随系统"
                        ThemeMode.Light -> Icons.Filled.LightMode to "主题：浅色"
                        ThemeMode.Dark -> Icons.Filled.DarkMode to "主题：深色"
                    }
                    BiliHeaderAction(themeIcon, themeLabel, onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        val next = when (settings.theme) {
                            ThemeMode.System -> ThemeMode.Light
                            ThemeMode.Light -> ThemeMode.Dark
                            ThemeMode.Dark -> ThemeMode.System
                        }
                        AppGraph.repo.updateSettings { it.copy(theme = next) }
                    })
                    BiliHeaderAction(Icons.Filled.Refresh, "刷新", onClick = vm::refresh, enabled = !vm.loading)
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            if (unread > 0) androidx.compose.material3.Badge {
                                Text(if (unread > 99) "99+" else unread.toString())
                            }
                        },
                    ) {
                        BiliHeaderAction(Icons.AutoMirrored.Filled.Message, "消息", onClick = onMessages)
                    }
                },
            )
            HomeDiscovery(onSearch, onOpenLink, onHistory, onOffline)
            var filterOpen by remember { mutableStateOf(false) }
            LaunchedEffect(ContentFilter.title, ContentFilter.up) {
                ContentFilter.persist()
            }
            Row(
                Modifier.widthIn(max = 490.dp).fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                AliStageTabs(
                    labels = HomeTab.entries.map { it.label },
                    selected = vm.tab.ordinal,
                    onSelect = { vm.select(HomeTab.entries[it]) },
                    modifier = Modifier.weight(1f),
                )
                AliStageIconButton(
                    Icons.Filled.FilterList,
                    if (ContentFilter.active) "内容屏蔽已开启" else "内容屏蔽",
                    onClick = { filterOpen = !filterOpen },
                    emphasized = ContentFilter.active || filterOpen,
                )
            }
            AnimatedVisibility(
                visible = filterOpen,
                enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
            ) {
                AliStagePanel(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    if (ContentFilter.blockedUps.isNotEmpty()) {
                        Text(
                            "已屏蔽的 UP 主",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(ContentFilter.blockedUps) { rule ->
                                BiliRuleChip(
                                    label = rule.name.ifBlank { "UID ${rule.mid}" },
                                    onRemove = { ContentFilter.removeBlockedUp(rule) },
                                )
                            }
                        }
                    }
                    if (ContentFilter.blockedTitles.isNotEmpty()) {
                        Text(
                            "已屏蔽的标题",
                            modifier = Modifier.padding(top = 10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(ContentFilter.blockedTitles) { blocked ->
                                BiliRuleChip(
                                    label = blocked.take(24),
                                    onRemove = { ContentFilter.removeBlockedTitle(blocked) },
                                )
                            }
                        }
                    }
                    Text(
                        "命中任意关键词就隐藏，多个词用空格或逗号分开",
                        modifier = Modifier.padding(top = if (ContentFilter.blockedUps.isEmpty() && ContentFilter.blockedTitles.isEmpty()) 0.dp else 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = ContentFilter.title,
                        onValueChange = { ContentFilter.title = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        label = { Text("标题关键词") },
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = ContentFilter.up,
                        onValueChange = { ContentFilter.up = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        label = { Text("UP 主名称") },
                        shape = MaterialTheme.shapes.medium,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (ContentFilter.active) {
                            TextButton(onClick = {
                                ContentFilter.clear()
                                filterOpen = false
                            }) { Text("清除屏蔽") }
                        }
                        if (session.loggedIn) {
                            TextButton(
                                enabled = !importingBlocks,
                                onClick = {
                                    val uid = session.dedeUserId
                                    importingBlocks = true
                                    scope.launch {
                                        AppGraph.repo.blockedUsers().onSuccess { rules ->
                                            if (AppGraph.repo.session.value.dedeUserId == uid) {
                                                val count = ContentFilter.importBlockedUps(rules)
                                                app.ailiaili.core.designsystem.QuickActions.showToast(
                                                    if (count > 0) "已导入 $count 个屏蔽项" else "没有可添加的屏蔽项",
                                                )
                                            }
                                        }.onFailure {
                                            app.ailiaili.core.designsystem.QuickActions.showToast(it.message ?: "导入黑名单失败")
                                        }
                                        importingBlocks = false
                                    }
                                },
                            ) { Text(if (importingBlocks) "导入中…" else "导入 B 站黑名单") }
                        }
                        if (ContentFilter.active) {
                            TextButton(onClick = ContentFilter::clear) { Text("清除全部屏蔽") }
                        }
                    }
                }
            }
            // 直播分区浏览：热门 + 官方大分区，横向滚动选择。
            if (vm.tab == HomeTab.Live && vm.liveAreas.isNotEmpty()) {
                val areaRow = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.foundation.lazy.LazyRow(
                    state = areaRow,
                    modifier = Modifier.fillMaxWidth()
                        .smoothWheelScroll(areaRow, app.ailiaili.core.designsystem.LocalWheelSpeed.current)
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = -1) {
                        BiliFilterChip(
                            selected = vm.liveAreaId == -1,
                            onClick = { vm.selectLiveArea(-1) },
                            label = { Text("热门") },
                        )
                    }
                    items(vm.liveAreas, key = { it.id }) { area ->
                        BiliFilterChip(
                            selected = vm.liveAreaId == area.id,
                            onClick = { vm.selectLiveArea(area.id) },
                            label = { Text(area.name) },
                        )
                    }
                }
            }
            val shown = app.ailiaili.core.designsystem.rememberFilteredVideos(vm.items)
            val resume by AppGraph.playbackMemory.resume.collectAsState()
            val resumeCard: (@Composable () -> Unit)? = resume
                ?.takeIf { vm.tab == HomeTab.Recommend && ContentFilter.allows(it.video) }
                ?.let { playback ->
                    {
                        HomeResumeCard(
                            playback,
                            onResume = { onResume(playback) },
                            onDismiss = { AppGraph.playbackMemory.dismiss(playback.video.bvid, playback.video.cid) },
                        )
                    }
                }
            val blockedOut = vm.items.isNotEmpty() && shown.isEmpty() && ContentFilter.active
            val autoLoadMore = vm.hasMore && vm.paginationError == null && vm.error == null
            val footer: (@Composable () -> Unit)? = if (vm.hasMore && vm.error == null) {
                { BiliPaginationFooter(vm.loading && !vm.refreshing, vm.paginationError, vm::loadMore) }
            } else {
                null
            }
            FilterAutoLoad(
                shownEmpty = blockedOut,
                hasMore = autoLoadMore,
                loading = vm.loading,
                onLoadMore = vm::loadMore,
            )
            if (vm.items.isNotEmpty() && vm.error != null) {
                AliStagePanel(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "刷新失败，可以继续浏览已加载内容",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = vm::refresh) { Text("重试") }
                    }
                }
            }
            if (shown.isEmpty() && resumeCard != null) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) { resumeCard() }
            }
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadBox(vm.loading && vm.items.isEmpty(), vm.error, shown.isEmpty() && !blockedOut, "这里还没有内容", vm::refresh) {
                    if (blockedOut) {
                        BiliEmptyState(
                            title = "这一页都被屏蔽了",
                            message = ContentFilter.summary(),
                            icon = Icons.Filled.FilterList,
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                TextButton(onClick = { ContentFilter.clear() }) { Text("清除屏蔽") }
                                footer?.invoke()
                            },
                        )
                    } else VideoGrid(
                        items = shown,
                        onClick = onVideo,
                        onAuthor = onAuthor,
                        state = grid,
                        onLoadMore = if (autoLoadMore) vm::loadMore else null,
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 64.dp + navBottom),
                        onDislike = if (vm.tab == HomeTab.Recommend) vm::dismiss else null,
                        footer = footer,
                        header = resumeCard,
                    )
                }
            }
        }
        if (away) {
            BiliFloatButton(
                icon = Icons.Filled.KeyboardArrowUp,
                label = "回到顶部",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp + navBottom),
                onClick = { scope.launch { grid.scrollToItem(0) } },
            )
        }
    }
}
