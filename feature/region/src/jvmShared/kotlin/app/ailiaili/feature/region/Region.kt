package app.ailiaili.feature.region

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStageNotice
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.BiliPaginationFooter
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.BiliSectionHeader
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.BiliFloatButton
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.FilterAutoLoad
import app.ailiaili.core.designsystem.shellBottomClearance
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.Partition
import app.ailiaili.core.model.PartitionCatalog
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.afterRegionPage
import app.ailiaili.core.network.RegionFeedDecode
import kotlinx.coroutines.launch

class RegionViewModel(private val tid: Int) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    var displayId = 1
    val seen: MutableSet<String> = mutableSetOf()
    private var loadGen = 0

    init { reload() }

    fun reload() {
        val token = ++loadGen
        displayId = 1
        seen.clear()
        items = emptyList()
        loading = true
        loadingMore = false
        hasMore = true
        error = null
        viewModelScope.launch {
            val result = AppGraph.repo.regionFeed(tid, 1)
            if (token != loadGen) return@launch
            loading = false
            result.onSuccess { page -> applyPage(page, reset = true) }
                .onFailure { error = it.message }
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore || items.isEmpty()) return
        val token = loadGen
        loadingMore = true
        viewModelScope.launch {
            val result = AppGraph.repo.regionFeed(tid, displayId)
            if (token != loadGen) return@launch
            loadingMore = false
            result.onSuccess { page -> applyPage(page, reset = false) }
                .onFailure { error = it.message }
        }
    }

    private fun applyPage(page: RegionFeedDecode, reset: Boolean) {
        val fetched = page.items
        val outcome = afterRegionPage(displayId, fetched.map { it.bvid }, seen)
        displayId = outcome.nextDisplayId
        seen.addAll(outcome.addedBvids)
        val added = outcome.addedBvids.toSet()
        items = if (reset) fetched else items + fetched.filter { it.bvid in added }
        hasMore = !page.ended && !outcome.noMore
        error = null
    }
}

@Composable
fun RegionPickerRoute(onPick: (Partition) -> Unit, onSearch: () -> Unit) {
    val navBottom = shellBottomClearance()
    val grid = rememberLazyGridState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val parts = remember(query) {
        val keyword = query.trim()
        PartitionCatalog.all.filter { it.name.contains(keyword, ignoreCase = true) }
    }
    LaunchedEffect(parts) { if (parts.isNotEmpty()) grid.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        AliStagePageHeader("分区") {
            AliStageIconButton(Icons.Filled.Search, "全站搜索", onSearch)
        }
        AliStageBody(Modifier.weight(1f), maxWidth = 1040.dp) {
            BiliSearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { focus.clearFocus() },
                placeholder = "查找分区",
                accessibilityLabel = "查找分区",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            )
            if (parts.isEmpty()) {
                AliStageNotice("没有匹配的分区", null, Icons.Filled.GridView, actionLabel = "清除搜索", onAction = { query = "" })
            } else {
                BiliSectionHeader(
                    if (query.isBlank()) "全部分区" else "匹配的分区",
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(132.dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize().smoothWheelScroll(grid, LocalWheelSpeed.current),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 16.dp + navBottom),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    gridItems(parts, key = { it.tid }) { part ->
                        PartitionTile(part, onClick = { onPick(part) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PartitionTile(part: Partition, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = Color(part.tint)
    Surface(
        onClick = onClick,
        modifier = Modifier.aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 67.dp).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.size(33.dp).background(tint.copy(alpha = .16f), CircleShape)
                    .border(1.dp, tint.copy(alpha = .31f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(part.initial, color = colors.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                part.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionRoute(
    tid: Int,
    name: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    vm: RegionViewModel = appViewModel(key = "region-$tid") { RegionViewModel(tid) },
) {
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listMode by rememberSaveable(tid) { mutableStateOf(false) }
    val shown = app.ailiaili.core.designsystem.rememberFilteredVideos(vm.items)
    val canLoadMore = vm.hasMore && vm.error == null
    val footer: (@Composable () -> Unit)? = if (vm.hasMore && vm.items.isNotEmpty()) {
        { BiliPaginationFooter(vm.loadingMore, vm.error, vm::loadMore) }
    } else null
    if (listMode && canLoadMore) {
        LaunchedEffect(listState, shown.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                if (last >= 0 && last >= shown.size - 4) vm.loadMore()
            }
        }
    }
    Scaffold(
        topBar = {
            Column {
                AliStagePageHeader(name, onBack = onBack) {
                    AliStageIconButton(Icons.Filled.Refresh, "刷新", vm::reload, enabled = !vm.loading)
                }
                AliStageTabs(
                    labels = listOf("封面", "列表"),
                    selected = if (listMode) 1 else 0,
                    onSelect = { listMode = it == 1 },
                    modifier = Modifier.widthIn(max = 290.dp).fillMaxWidth().padding(horizontal = 12.dp),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            FilterAutoLoad(
                shownEmpty = vm.items.isNotEmpty() && shown.isEmpty(),
                hasMore = canLoadMore,
                loading = vm.loading || vm.loadingMore,
                onLoadMore = vm::loadMore,
            )
            LoadBox(
                vm.loading || vm.loadingMore,
                vm.error.takeIf { vm.items.isEmpty() },
                vm.items.isEmpty(),
                "这个分区暂时没有内容",
                vm::reload,
            ) {
                when {
                    shown.isEmpty() -> Column(Modifier.fillMaxWidth()) {
                        AliStageNotice("这些视频已被屏蔽", ContentFilter.summary(), Icons.Filled.FilterList)
                        footer?.invoke()
                    }
                    listMode -> AliStageBody {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().smoothWheelScroll(listState, LocalWheelSpeed.current),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            items(shown, key = { it.stableKey }) { video ->
                                VideoListRow(
                                    title = video.title,
                                    cover = video.cover,
                                    onClick = { onVideo(video) },
                                    badge = video.durationText,
                                    author = video.author,
                                    ownerMid = video.ownerMid,
                                    aid = video.aid,
                                    coverWidth = 104.dp,
                                    panel = true,
                                    meta = {
                                        if (video.author.isNotBlank()) {
                                            MetaRow(
                                                listOf(MetaSpec(Icons.Filled.Person, video.author, MetricTone.Neutral)),
                                                modifier = Modifier.clickable(enabled = video.ownerMid > 0) { onAuthor(video) },
                                            )
                                        }
                                    },
                                )
                            }
                            if (footer != null) item { footer() }
                        }
                    }
                    else -> VideoGrid(
                        shown, onVideo, onAuthor, state = gridState,
                        onLoadMore = if (canLoadMore) vm::loadMore else null,
                        footer = footer,
                    )
                }
            }
            val away = if (listMode) listState.firstVisibleItemIndex > 0 else gridState.firstVisibleItemIndex > 0
            if (away) {
                BiliFloatButton(
                    Icons.Filled.KeyboardArrowUp,
                    "回到顶部",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    onClick = {
                        scope.launch {
                            if (listMode) listState.animateScrollToItem(0) else gridState.animateScrollToItem(0)
                        }
                    },
                )
            }
        }
    }
}
