package app.ailiaili.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.BiliFloatButton
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.BiliSurfaceCard
import app.ailiaili.core.designsystem.BiliSearchEntry
import app.ailiaili.core.designsystem.BiliUnderlineTabs
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.shellBottomPadding
import app.ailiaili.core.designsystem.shellNavigationBottom
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.ThemeMode
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) { Recommend("推荐"), Popular("热门"), Ranking("排行榜"), Live("直播") }

class HomeViewModel : ViewModel() {
    var tab by mutableStateOf(HomeTab.Recommend)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(true)

    init { refresh() }

    fun select(next: HomeTab) {
        if (tab == next) return
        tab = next
        refresh()
    }

    fun refresh() {
        page = 1
        hasMore = true
        refreshing = true
        load(reset = true)
    }

    fun loadMore() {
        if (loading || !hasMore || tab == HomeTab.Ranking) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val current = tab
        val requestPage = if (reset) 1 else page + 1
        loading = true
        error = null
        viewModelScope.launch {
            val result = when (current) {
                HomeTab.Recommend -> AppGraph.repo.recommend(requestPage)
                HomeTab.Popular -> AppGraph.repo.popular(requestPage)
                HomeTab.Ranking -> AppGraph.repo.ranking()
                HomeTab.Live -> AppGraph.repo.liveList(requestPage)
            }
            if (tab != current) return@launch
            loading = false
            refreshing = false
            result.onSuccess { list ->
                page = requestPage
                val merged = if (reset) list else (items + list).distinctBy { it.stableKey }
                val grew = reset || merged.size > items.size
                items = merged
                hasMore = when (current) {
                    HomeTab.Ranking -> false
                    HomeTab.Recommend -> list.isNotEmpty() && grew
                    else -> list.isNotEmpty()
                }
                if (list.isEmpty() && reset) error = null
            }.onFailure {
                if (reset) items = emptyList()
                error = it.message ?: "加载失败"
                hasMore = false
                refreshing = false
            }
        }
    }
}

@Composable
fun HomeRoute(
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onSearch: () -> Unit,
    onMessages: () -> Unit,
    vm: HomeViewModel = appViewModel { HomeViewModel() },
) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by AppGraph.repo.settings.collectAsState()
    val greeting = remember { greetingNow() }
    val away by remember {
        derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 240 }
    }
    val navBottom = shellNavigationBottom()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BiliPageHeader(
                title = "首页",
                subtitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(greeting.first)
                        Text(
                            greeting.second,
                            modifier = Modifier.padding(start = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
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
                    Spacer(Modifier.width(8.dp))
                    BiliHeaderAction(Icons.AutoMirrored.Filled.Message, "消息", onClick = onMessages)
                },
            )
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                BiliSearchEntry(
                    placeholder = "搜索你感兴趣的视频",
                    onClick = onSearch,
                    modifier = Modifier
                        .width(minOf(maxWidth, 440.dp))
                        .align(Alignment.Center),
                )
            }
            BiliUnderlineTabs(
                labels = HomeTab.entries.map { it.label },
                selected = vm.tab.ordinal,
                onSelect = { vm.select(HomeTab.entries[it]) },
            )
            var filterOpen by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BiliFilterChip(
                    selected = ContentFilter.active || filterOpen,
                    onClick = { filterOpen = !filterOpen },
                    label = { Text(if (ContentFilter.active) "屏蔽中" else "屏蔽") },
                    leadingIcon = {
                        Icon(Icons.Filled.FilterList, contentDescription = null, Modifier.size(18.dp))
                    },
                )
                if (ContentFilter.active) {
                    Text(
                        ContentFilter.summary(),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = {
                        ContentFilter.clear()
                        filterOpen = false
                    }) {
                        Text("清除")
                    }
                }
            }
            AnimatedVisibility(
                visible = filterOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                BiliSurfaceCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    Text(
                        "命中任意关键词就隐藏，多个词用空格或逗号分开",
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
                }
            }
            val shown = vm.items.filter { ContentFilter.allows(it) }
            val blockedOut = vm.items.isNotEmpty() && shown.isEmpty() && ContentFilter.active
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadBox(vm.loading, vm.error, shown.isEmpty() && !blockedOut, "这里还没有内容", vm::refresh) {
                    if (blockedOut) {
                        BiliEmptyState(
                            title = "这一页都被屏蔽了",
                            message = ContentFilter.summary(),
                            icon = Icons.Filled.FilterList,
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                TextButton(onClick = { ContentFilter.clear() }) { Text("清除屏蔽") }
                            },
                        )
                    } else VideoGrid(
                        items = shown,
                        onClick = onVideo,
                        onAuthor = onAuthor,
                        state = grid,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp + navBottom),
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .shellBottomPadding()
                .padding(end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BiliFloatButton(
                icon = Icons.Filled.KeyboardArrowUp,
                label = "回到顶部",
                enabled = away,
                onClick = { scope.launch { grid.scrollToItem(0) } },
            )
            BiliFloatButton(Icons.Filled.Refresh, "刷新", onClick = vm::refresh)
        }
    }
}

private fun greetingNow(): Pair<String, String> {
    val hour = app.ailiaili.core.model.currentHourOfDay()
    val (text, faces) = when (hour) {
        in 5..10 -> "早上好" to arrayOf("(｡･ω･｡)", "(＾▽＾)", "(・ω・)")
        in 11..12 -> "中午好" to arrayOf("(￣▽￣)", "(＾ω＾)", "(・ω・)")
        in 13..17 -> "下午好" to arrayOf("(´・ω・`)", "(・ω・)ノ", "(￣ω￣)")
        in 18..22 -> "晚上好" to arrayOf("(￣▽￣)ノ", "(・ω・)ﾉ", "(＾▽＾)")
        else -> "夜深了" to arrayOf("(。-ω-)zzz", "(-ω-)zZ", "(´-ω-`)")
    }
    return text to faces.random()
}
