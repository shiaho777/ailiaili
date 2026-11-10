package app.ailiaili.feature.region

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliFloatButton
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.FilterAutoLoad
import app.ailiaili.core.designsystem.shellNavigationBottom
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val navBottom = shellNavigationBottom()
    Column(Modifier.fillMaxSize()) {
        BiliPageHeader("分区") {
            BiliHeaderAction(Icons.Filled.Search, "搜索", onSearch)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + navBottom),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(PartitionCatalog.all, key = { it.tid }) { part ->
                PartitionTile(part, onClick = { onPick(part) })
            }
        }
    }
}

@Composable
private fun PartitionTile(part: Partition, onClick: () -> Unit) {
    val tint = Color(part.tint)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(part.initial, color = tint, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                part.name,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
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
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            BiliBackHeader(name, onBack)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                val shown = vm.items.filter { ContentFilter.allows(it) }
                FilterAutoLoad(
                    shownEmpty = vm.items.isNotEmpty() && shown.isEmpty(),
                    hasMore = vm.hasMore,
                    loading = vm.loading || vm.loadingMore,
                    onLoadMore = vm::loadMore,
                )
                LoadBox(
                    vm.loading || vm.loadingMore,
                    if (vm.items.isNotEmpty()) null else vm.error,
                    shown.isEmpty(),
                    "这个分区暂时没有内容",
                    vm::reload,
                    Modifier.weight(1f),
                ) {
                    if (shown.isEmpty()) {
                        BiliEmptyState(
                            title = "这一页都被屏蔽了",
                            message = ContentFilter.summary(),
                            icon = Icons.Filled.FilterList,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else VideoGrid(
                        shown,
                        onVideo,
                        onAuthor,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        state = gridState,
                    )
                }
                val moreError = vm.error
                if (moreError != null && vm.items.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            moreError,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = vm::loadMore) { Text("重试") }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BiliFloatButton(Icons.Filled.Refresh, "刷新", onClick = vm::reload)
                BiliFloatButton(
                    Icons.Filled.KeyboardArrowUp,
                    "回到顶部",
                    onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                )
            }
        }
    }
}
