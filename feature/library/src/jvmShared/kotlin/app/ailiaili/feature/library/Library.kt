package app.ailiaili.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import app.ailiaili.core.designsystem.AliStageDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Person
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStageNotice
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.smoothWheelScroll
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import app.ailiaili.core.designsystem.BiliAssistChip
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.CreateFavoriteFolderDialog
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.QuickActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ListViewModel(private val loader: suspend () -> Result<List<VideoItem>>) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    init { reload() }
    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            loader().onSuccess { items = it; loading = false }.onFailure { loading = false; error = it.message }
        }
    }
    fun remove(item: VideoItem) { items = items.filterNot { it.stableKey == item.stableKey } }

    fun delete(item: VideoItem, remote: suspend () -> Result<Unit>) {
        val before = items
        remove(item)
        viewModelScope.launch {
            // 乐观删除失败要回滚：否则本地没了、云端还在，且无任何提示。
            runCatching { remote() }.getOrElse { Result.failure(it) }.onFailure {
                items = before
                error = it.message ?: "删除失败，已恢复"
            }
        }
    }

    fun deleteAll(remote: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            remote().onSuccess { items = emptyList() }.onFailure { error = it.message }
        }
    }
}

@Composable
private fun BackTitle(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    AliStagePageHeader(title, onBack = onBack, actions = actions)
}

@Composable
private fun LibrarySignIn(title: String, subtitle: String, icon: ImageVector, onBack: () -> Unit, onLogin: () -> Unit) {
    Scaffold(topBar = { BackTitle(title, onBack) }) { padding ->
        AliStageNotice(
            "登录后查看$title", subtitle, icon, modifier = Modifier.padding(padding),
            actionLabel = "登录哔哩哔哩", onAction = onLogin,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onAuthor: (VideoItem) -> Unit, onLogin: () -> Unit) {
    val session by AppGraph.repo.session.collectAsState()
    if (!session.loggedIn) {
        LibrarySignIn("历史", "登录后同步视频和直播的观看记录。", Icons.Filled.History, onBack, onLogin)
        return
    }
    var live by rememberSaveable(session.dedeUserId) { mutableStateOf(false) }
    var videoQuery by rememberSaveable(session.dedeUserId) { mutableStateOf("") }
    var liveQuery by rememberSaveable(session.dedeUserId) { mutableStateOf("") }
    val videoListState = rememberLazyListState()
    val liveListState = rememberLazyListState()
    val historyQuery = if (live) liveQuery else videoQuery
    val historyState = if (live) liveListState else videoListState
    val vm = app.ailiaili.core.designsystem.accountViewModel(key = if (live) "history-live" else "history-video") {
        HistoryViewModel(live)
    }
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    fun drop(item: VideoItem) { vm.delete(listOf(item)) }
    if (confirmClear) {
        AliStageDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空观看历史") },
            text = { Text("会删除账号下的全部云端历史，而且不能撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    editing = false
                    picked = emptySet()
                    vm.clear()
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    Scaffold(topBar = {
        BackTitle("历史", onBack) {
            AliStageIconButton(
                androidx.compose.material.icons.Icons.Filled.Refresh, "刷新", vm::reload, enabled = !vm.loading,
            )
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding)) {
            AliStageTabs(
                labels = listOf("视频", "直播"),
                selected = if (live) 1 else 0,
                onSelect = {
                    if (live != (it == 1)) {
                        live = it == 1
                        editing = false
                        picked = emptySet()
                    }
                },
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().padding(horizontal = 10.dp),
            )
            app.ailiaili.core.designsystem.BiliSearchField(
                value = historyQuery,
                onValueChange = {
                    if (live) liveQuery = it else videoQuery = it
                    picked = emptySet()
                    historyState.requestScrollToItem(0)
                },
                onSearch = vm::loadMore,
                placeholder = if (live) "搜索直播观看记录" else "搜索视频观看记录",
                accessibilityLabel = "搜索观看记录",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val filtered = app.ailiaili.core.designsystem.rememberFilteredVideos(vm.items)
            val shown = remember(filtered, historyQuery) {
                app.ailiaili.core.model.historySearch(filtered, historyQuery)
            }
            LaunchedEffect(historyState, shown.size, historyQuery, vm.hasMore) {
                snapshotFlow {
                    historyState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                }.collect { last ->
                    val total = historyState.layoutInfo.totalItemsCount
                    if (last >= 0 && last >= total - 4 && vm.error == null) vm.loadMore()
                }
            }
            LaunchedEffect(historyQuery, shown.isEmpty(), vm.hasMore, vm.loading, vm.error) {
                if (historyQuery.isNotBlank() && shown.isEmpty() && vm.hasMore && !vm.loading && vm.error == null) {
                    delay(300)
                    vm.loadMore()
                }
            }
            val dayGroups = remember(shown) {
                val today = java.time.LocalDate.now()
                app.ailiaili.core.model.historyDayGroups(
                    shown,
                    todayKey = today.toString(),
                    yesterdayKey = today.minusDays(1).toString(),
                )
            }
            EditBar(
                editing = editing,
                selectionCount = picked.size,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty(),
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    vm.delete(shown.filter { it.stableKey in picked })
                    picked = emptySet()
                },
                onClear = { confirmClear = true },
            )
            LoadBox(
                vm.loading || historyQuery.isNotBlank() && shown.isEmpty() && vm.hasMore && vm.error == null,
                vm.error.takeIf { shown.isEmpty() },
                vm.items.isEmpty() || historyQuery.isNotBlank() && shown.isEmpty(),
                when {
                    historyQuery.isNotBlank() -> "没有匹配的观看记录"
                    live -> "云端没有直播观看记录"
                    else -> "还没有云端历史"
                },
                { if (vm.hasMore) vm.loadMore() else vm.reload() },
            ) {
                if (shown.isEmpty()) {
                    FilteredEmptyState()
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize()
                            .smoothWheelScroll(historyState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                        state = historyState,
                    ) {
                    dayGroups.forEach { (day, groupItems) ->
                        stickyHeader(key = "day-$day") {
                            Text(
                                day,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        }
                        items(groupItems, key = { it.stableKey }) { item ->
                        val state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) drop(item)
                                false
                            },
                        )
                        SwipeToDismissBox(
                            state = state,
                            modifier = Modifier.animateItem(),
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text("删除", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            },
                        ) {
                            val isPicked = item.stableKey in picked
                            VideoListRow(
                                title = item.title,
                                cover = item.cover,
                                author = item.author,
                                ownerMid = item.ownerMid,
                                aid = if (item.isLive) 0 else item.aid,
                                badge = item.durationText,
                                selected = editing && isPicked,
                                coverWidth = 104.dp,
                                panel = true,
                                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                                onClick = {
                                    if (editing) {
                                        picked = if (isPicked) picked - item.stableKey else picked + item.stableKey
                                    } else {
                                        onVideo(item)
                                    }
                                },
                                onLongClick = if (editing) null else {
                                    {
                                        editing = true
                                        picked = picked + item.stableKey
                                    }
                                },
                                leading = if (!editing) null else {
                                    {
                                        Checkbox(
                                            checked = isPicked,
                                            onCheckedChange = {
                                                picked = if (isPicked) picked - item.stableKey else picked + item.stableKey
                                            },
                                        )
                                    }
                                },
                                meta = {
                                    MetaRow(
                                        items = listOfNotNull(
                                            item.author.takeIf { it.isNotBlank() }?.let {
                                                MetaSpec(Icons.Filled.Person, it, MetricTone.Neutral)
                                            },
                                        ),
                                        maxItems = 1,
                                    )
                                },
                            )
                        }
                        }
                    }
                    if (vm.error != null) {
                        item { TextButton(onClick = { if (vm.hasMore) vm.loadMore() else vm.reload() }) {
                            Text(vm.error.orEmpty() + " · 重试")
                        } }
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesRoute(onBack: () -> Unit, onLogin: () -> Unit, onOpen: (FavFolder) -> Unit) {
    val session by AppGraph.repo.session.collectAsState()
    if (!session.loggedIn) {
        LibrarySignIn("收藏", "登录后查看收藏夹和账号里的收藏视频。", Icons.Filled.Folder, onBack, onLogin)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FavFolder?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    app.ailiaili.core.designsystem.RegisterPageRefresh { attempt++ }
    if (creating) {
        CreateFavoriteFolderDialog(onDismiss = { creating = false }) {
            creating = false
            attempt++
        }
    }
    deleting?.let { folder ->
        AliStageDialog(
            onDismissRequest = { if (!deleteBusy) deleting = null },
            title = { Text("删除收藏夹") },
            text = { Text("删除「${folder.title}」及其收藏记录？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteBusy = true
                    scope.launch {
                        AppGraph.repo.deleteFavFolder(folder.id).onSuccess {
                            deleting = null
                            attempt++
                        }.onFailure { QuickActions.showToast(it.message ?: "删除收藏夹失败") }
                        deleteBusy = false
                    }
                }, enabled = !deleteBusy) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, enabled = !deleteBusy) { Text("取消") } },
        )
    }
    LaunchedEffect(attempt) {
        loading = true
        error = null
        AppGraph.repo.favFolders().onSuccess { folders = it; loading = false }.onFailure { error = it.message; loading = false }
    }
    Scaffold(topBar = {
        BackTitle("收藏", onBack) {
            AliStageIconButton(Icons.Filled.Add, "新建收藏夹", { creating = true })
            AliStageIconButton(Icons.Filled.Refresh, "刷新", { attempt++ }, enabled = !loading)
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding), maxWidth = 840.dp) {
            val matches = remember(folders, query) { folders.filter { it.title.ifBlank { "收藏夹" }.contains(query.trim(), ignoreCase = true) } }
            BiliSearchField(
                value = query, onValueChange = { query = it }, onSearch = {},
                placeholder = "查找收藏夹", accessibilityLabel = "查找收藏夹",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            )
            LoadBox(loading, error, folders.isEmpty(), "没有收藏夹", { attempt++ }) {
                if (matches.isEmpty()) {
                    AliStageNotice(
                        "没有匹配的收藏夹", null, Icons.Filled.Folder,
                        actionLabel = "清除搜索", onAction = { query = "" },
                    )
                    return@LoadBox
                }
                val foldersState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = foldersState,
                    modifier = Modifier.smoothWheelScroll(foldersState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                ) {
                    items(matches, key = { it.id }) { folder ->
                        VideoListRow(
                            title = folder.title.ifBlank { "收藏夹" },
                            cover = folder.cover,
                            onClick = { onOpen(folder) },
                            modifier = Modifier.animateItem(),
                            coverWidth = 104.dp,
                            coverPlaceholder = false,
                            panel = true,
                            extraMenuItems = if (folder.isDefault) null else { close ->
                                DropdownMenuItem(
                                    text = { Text("删除收藏夹") },
                                    onClick = { close(); deleting = folder },
                                )
                            },
                            leading = if (folder.cover.isBlank()) {
                                {
                                    Box(
                                        Modifier
                                            .size(44.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            meta = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BiliBadge(
                                        text = "${folder.count} 个视频",
                                        tone = BiliBadgeTone.Neutral,
                                        style = BiliBadgeStyle.Soft,
                                    )
                                    if (folder.privateFolder) {
                                        BiliBadge(
                                            text = "私密",
                                            tone = BiliBadgeTone.Warning,
                                            style = BiliBadgeStyle.Soft,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FavVideosRoute(
    mediaId: Long,
    title: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onLogin: () -> Unit,
    onPlayAll: (List<VideoItem>) -> Unit = {},
) {
    val session by AppGraph.repo.session.collectAsState()
    if (!session.loggedIn) {
        LibrarySignIn(title, "登录后查看这个收藏夹。", Icons.Filled.Folder, onBack, onLogin)
        return
    }
    val vm = app.ailiaili.core.designsystem.accountViewModel(key = "fav-$mediaId") { FavoriteVideosViewModel(mediaId) }
    val settings by AppGraph.repo.settings.collectAsState()
    app.ailiaili.core.designsystem.RegisterPageRefresh { vm.load() }
    var editing by remember(vm) { mutableStateOf(false) }
    var picked by remember(vm, vm.page, vm.query) { mutableStateOf(setOf<String>()) }
    var queueJob by remember(vm) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = {
        BackTitle(title, onBack) {
            if (!editing) {
                AliStageIconButton(
                    if (settings.favoriteGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    if (settings.favoriteGrid) "切换列表" else "切换封面",
                    { AppGraph.repo.updateSettings { it.copy(favoriteGrid = !it.favoriteGrid) } },
                )
            }
            if (queueJob?.isActive == true) {
                AliStageIconButton(Icons.Filled.Close, "取消准备播放", { queueJob?.cancel(); queueJob = null })
            } else if (vm.items.isNotEmpty() && !editing && !vm.loading) {
                AliStageIconButton(
                    Icons.Filled.PlayArrow, if (vm.query.isBlank()) "播放全部" else "播放搜索结果",
                    {
                        queueJob = scope.launch {
                            QuickActions.showToast("正在准备播放队列…")
                            vm.playableQueue().onSuccess { videos ->
                                if (videos.isNotEmpty()) onPlayAll(videos)
                                else QuickActions.showToast("没有可播放的视频")
                            }.onFailure { QuickActions.showToast(it.message ?: "播放队列加载失败") }
                            queueJob = null
                        }
                    },
                )
            }
            AliStageIconButton(
                androidx.compose.material.icons.Icons.Filled.Refresh, "刷新", { vm.load() }, enabled = !vm.loading,
            )
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding)) {
            BiliSearchField(
                value = vm.query,
                onValueChange = {
                    queueJob?.cancel()
                    queueJob = null
                    vm.search(it)
                },
                onSearch = { vm.load(1) },
                placeholder = "搜索整个收藏夹",
                accessibilityLabel = "搜索整个收藏夹",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            val shown = app.ailiaili.core.designsystem.rememberFilteredVideos(vm.items)
            EditBar(
                editing = editing,
                selectionCount = picked.size,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty() && !vm.mutating && !vm.loading,
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    val aids = shown.filter { it.stableKey in picked }.map { it.aid }
                    vm.remove(aids) { picked = emptySet() }
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(vm.page, vm.appliedQuery) {
                    LoadBox(
                        vm.loading, vm.error, vm.items.isEmpty(),
                        if (vm.query.isBlank()) "这个收藏夹是空的" else "没有匹配的视频",
                        { vm.load() },
                    ) {
                        if (shown.isEmpty()) {
                            FilteredEmptyState()
                        } else if (editing) {
                            val favEditState = androidx.compose.foundation.lazy.rememberLazyListState()
                            LazyColumn(
                                state = favEditState,
                                modifier = Modifier.smoothWheelScroll(favEditState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(shown, key = { it.stableKey }) { item ->
                                    val toggle = {
                                        picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                                    }
                                    VideoListRow(
                                        title = item.title,
                                        cover = item.cover,
                                        author = item.author,
                                        ownerMid = item.ownerMid,
                                        aid = item.aid,
                                        extraMenuItems = { close ->
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("移出收藏") },
                                                onClick = {
                                                    close()
                                                    vm.remove(listOf(item.aid)) { picked = picked - item.stableKey }
                                                },
                                            )
                                        },
                                        onClick = toggle,
                                        modifier = Modifier.animateItem(),
                                        badge = item.durationText,
                                        selected = item.stableKey in picked,
                                        coverWidth = 104.dp,
                                        panel = true,
                                        leading = {
                                            Checkbox(
                                                checked = item.stableKey in picked,
                                                onCheckedChange = { toggle() },
                                            )
                                        },
                                        meta = {
                                            MetaRow(
                                                items = listOfNotNull(
                                                    item.author.takeIf { it.isNotBlank() }?.let {
                                                        MetaSpec(Icons.Filled.Person, it, MetricTone.Neutral)
                                                    },
                                                ),
                                                maxItems = 1,
                                            )
                                        },
                                    )
                                }
                            }
                        } else if (settings.favoriteGrid) {
                            VideoGrid(shown, onVideo, onAuthor)
                        } else {
                            val list = rememberLazyListState()
                            LazyColumn(
                                state = list,
                                modifier = Modifier.smoothWheelScroll(list, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                            ) {
                                items(shown, key = { it.stableKey }) { item ->
                                    VideoListRow(
                                        title = item.title,
                                        cover = item.cover,
                                        author = item.author,
                                        ownerMid = item.ownerMid,
                                        aid = item.aid,
                                        badge = item.durationText,
                                        panel = true,
                                        onClick = { onVideo(item) },
                                        meta = {
                                            Text(item.author, style = MaterialTheme.typography.bodySmall)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (vm.page > 1 || vm.hasMore || vm.totalPages > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ready = !vm.loading && !vm.mutating
                    TextButton(onClick = { vm.load(1) }, enabled = ready && vm.page > 1) { Text("首页") }
                    TextButton(onClick = { vm.load(vm.page - 1) }, enabled = ready && vm.page > 1) { Text("上一页") }
                    Text(if (vm.totalPages > 0) "${vm.page} / ${vm.totalPages}" else "第 ${vm.page} 页")
                    TextButton(onClick = { vm.load(vm.page + 1) }, enabled = ready && vm.hasMore) { Text("下一页") }
                    if (vm.totalPages > 1) {
                        TextButton(onClick = { vm.load(vm.totalPages) }, enabled = ready && vm.page < vm.totalPages) { Text("末页") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterRoute(
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onLogin: () -> Unit,
    onPlayAll: (List<VideoItem>) -> Unit = {},
) {
    val session by AppGraph.repo.session.collectAsState()
    if (!session.loggedIn) {
        LibrarySignIn("稍后再看", "登录后继续观看你保存的视频。", Icons.Filled.Schedule, onBack, onLogin)
        return
    }
    val vm = app.ailiaili.core.designsystem.accountViewModel(key = "later") { ListViewModel { AppGraph.repo.watchLater() } }
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    var editing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AliStageDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空稍后再看") },
            text = { Text("会删掉账号里的整个列表，而且不能撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    editing = false
                    picked = emptySet()
                    vm.deleteAll { AppGraph.repo.clearWatchLater() }
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    Scaffold(topBar = {
        BackTitle("稍后再看", onBack) {
            if (vm.items.isNotEmpty() && !editing) {
                AliStageIconButton(
                    androidx.compose.material.icons.Icons.Filled.PlayArrow, "播放全部",
                    { onPlayAll(vm.items) },
                )
            }
            AliStageIconButton(
                androidx.compose.material.icons.Icons.Filled.Refresh, "刷新", vm::reload, enabled = !vm.loading,
            )
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding)) {
            val shown = app.ailiaili.core.designsystem.rememberFilteredVideos(vm.items)
            EditBar(
                editing = editing,
                selectionCount = picked.size,
                allSelected = shown.isNotEmpty() && picked.size == shown.size,
                canDelete = picked.isNotEmpty(),
                onEdit = {
                    editing = !editing
                    if (!editing) picked = emptySet()
                },
                onToggleAll = {
                    picked = if (picked.size == shown.size) emptySet() else shown.map { it.stableKey }.toSet()
                },
                onDelete = {
                    shown.filter { it.stableKey in picked }.forEach { item ->
                        vm.delete(item) {
                            if (item.aid > 0) AppGraph.repo.removeWatchLater(item.aid)
                            else Result.failure(IllegalStateException("这条内容不能移出"))
                        }
                    }
                    picked = emptySet()
                },
                onClear = { confirmClear = true },
            )
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "稍后再看是空的", vm::reload) {
                if (shown.isEmpty() && ContentFilter.active) {
                    FilteredEmptyState()
                } else {
                    val laterState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyColumn(
                        state = laterState,
                        modifier = Modifier.smoothWheelScroll(laterState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                    items(shown, key = { it.stableKey }) { item ->
                        val toggle = {
                            picked = if (item.stableKey in picked) picked - item.stableKey else picked + item.stableKey
                        }
                        VideoListRow(
                            title = item.title,
                            cover = item.cover,
                            author = item.author,
                            ownerMid = item.ownerMid,
                            aid = item.aid,
                            extraMenuItems = { close ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("移出稍后再看") },
                                    onClick = {
                                        close()
                                        vm.delete(item) {
                                            if (item.aid > 0) AppGraph.repo.removeWatchLater(item.aid)
                                            else Result.failure(IllegalStateException("这条内容不能移出"))
                                        }
                                    },
                                )
                            },
                            badge = item.durationText,
                            selected = editing && item.stableKey in picked,
                            coverWidth = 104.dp,
                            panel = true,
                            onClick = { if (editing) toggle() else onVideo(item) },
                            onLongClick = {
                                if (!editing) editing = true
                                toggle()
                            },
                            modifier = Modifier.animateItem(),
                            leading = if (!editing) null else {
                                { Checkbox(checked = item.stableKey in picked, onCheckedChange = { toggle() }) }
                            },
                            meta = {
                                MetaRow(
                                    items = listOfNotNull(
                                        item.author.takeIf { it.isNotBlank() }?.let {
                                            MetaSpec(Icons.Filled.Person, it, MetricTone.Neutral)
                                        },
                                    ),
                                    maxItems = 1,
                                )
                            },
                            trailing = if (editing) null else {
                                {
                                    IconButton(onClick = {
                                        vm.delete(item) {
                                            if (item.aid > 0) AppGraph.repo.removeWatchLater(item.aid)
                                            else Result.failure(IllegalStateException("这条内容不能移出"))
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "移除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(
    editing: Boolean,
    selectionCount: Int,
    allSelected: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        BiliFilterChip(
            selected = editing,
            onClick = onEdit,
            label = { Text(if (editing) "完成" else "编辑") },
            leadingIcon = {
                Icon(
                    if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        if (editing) {
            BiliAssistChip(
                label = if (allSelected) "取消全选" else "全选",
                onClick = onToggleAll,
                leadingIcon = {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            if (onClear != null) {
                BiliAssistChip(
                    label = "清空",
                    onClick = onClear,
                    leadingIcon = {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
            Text(
                "已选 $selectionCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            FilledTonalButton(
                onClick = onDelete,
                enabled = canDelete,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("删除所选", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun FilteredEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BiliEmptyState(
            title = "这些内容已被屏蔽",
            message = "当前屏蔽词：${ContentFilter.summary()}",
            icon = Icons.Filled.FilterList,
            action = {
                FilledTonalButton(onClick = { ContentFilter.clear() }) { Text("清除屏蔽") }
            },
        )
    }
}
