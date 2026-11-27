package app.ailiaili.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.BiliSearchField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.designsystem.AliStageHeader
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliPaginationFooter
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.FilterAutoLoad
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.LocalWindowForm
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.BangumiItem
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.UrlParser
import app.ailiaili.core.model.SearchUser
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.model.searchEmptyMessage
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchRoute(
    initial: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onVideoLink: (UrlHit.Video) -> Unit,
    onBangumi: (BangumiItem) -> Unit = {},
    vm: SearchViewModel = appViewModel(key = "search-$initial") { SearchViewModel(initial) },
) {
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::refresh)
    val history by AppGraph.searchHistory.items.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val desktop = remember { isDesktopHost() }
    var inputFocused by remember { mutableStateOf(false) }
    val link = remember(vm.draft) { linkSuggestion(vm.draft, UrlParser.parse(vm.draft)) }
    val suggestions = remember(vm.draft, history, vm.hot, link) {
        if (link != null) listOf(link) else searchSuggestions(vm.draft, history, vm.hot)
    }
    var selectedSuggestion by remember(suggestions, vm.draft) { mutableIntStateOf(-1) }
    var suggestionsDismissed by remember(vm.draft) { mutableStateOf(false) }
    val panelVisible = inputFocused && !suggestionsDismissed && suggestions.isNotEmpty() &&
        (link != null || vm.draft.trim() != vm.results?.query?.keyword)
    val focusOnEntry = remember(vm) { initial.isBlank() && vm.results == null }
    val keyboardDismissal = remember(focus, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (keyboard != null && inputFocused && source == NestedScrollSource.UserInput && available.y != 0f) {
                    focus.clearFocus()
                    keyboard.hide()
                }
                return Offset.Zero
            }
        }
    }
    fun submit(raw: String) {
        if (raw.isBlank()) return
        focus.clearFocus()
        keyboard?.hide()
        when (val hit = UrlParser.parse(raw)) {
            is UrlHit.Video -> onVideoLink(hit)
            is UrlHit.Space -> onAuthor(VideoItem(ownerMid = hit.mid))
            is UrlHit.Live -> onVideo(VideoItem(roomId = hit.roomId, title = "直播"))
            is UrlHit.Keyword -> vm.search(hit.word)
        }
    }
    LaunchedEffect(vm) {
        vm.takeInitialLink()?.let(::submit)
    }
    val kinds = ContentKind.entries
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AliStageHeader {
                Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 960.dp).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        AliStageIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                        BiliSearchField(
                            value = vm.draft,
                            onValueChange = vm::editDraft,
                            onSearch = { submit(vm.draft) },
                            placeholder = "搜索关键词或链接",
                            modifier = Modifier.weight(1f)
                                .onFocusChanged {
                                    if (!it.isFocused) selectedSuggestion = -1
                                    if (it.isFocused && !inputFocused) suggestionsDismissed = false
                                    inputFocused = it.isFocused
                                }
                                .onPreviewKeyEvent { event ->
                                    if (!desktop || !panelVisible || event.type != KeyEventType.KeyDown ||
                                        event.isCtrlPressed || event.isAltPressed || event.isMetaPressed || event.isShiftPressed
                                    ) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionDown -> selectedSuggestion = (selectedSuggestion + 1) % suggestions.size
                                        Key.DirectionUp -> selectedSuggestion = if (selectedSuggestion <= 0) suggestions.lastIndex else selectedSuggestion - 1
                                        Key.Enter, Key.NumPadEnter -> {
                                            val selected = suggestions.getOrNull(selectedSuggestion) ?: return@onPreviewKeyEvent false
                                            submit(selected.value)
                                        }
                                        Key.Escape -> {
                                            selectedSuggestion = -1
                                            suggestionsDismissed = true
                                        }
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    true
                                },
                            autoFocus = focusOnEntry,
                            submitLabel = if (link == null) "搜索" else "打开",
                            onPaste = {
                                val text = clipboard.getText()?.text.orEmpty().trim()
                                if (text.isEmpty()) QuickActions.showToast("剪贴板里没有文本")
                                else vm.editDraft(text)
                            },
                        )
                    }
                    AliStageTabs(
                        labels = listOf("视频", "直播", "番剧", "用户"),
                        selected = kinds.indexOf(vm.kind),
                        onSelect = { vm.selectKind(kinds[it]) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AliStageBody(Modifier.nestedScroll(keyboardDismissal), maxWidth = 1180.dp) {
                val results = vm.results
                if (results == null) {
                    SearchDiscovery(history, vm.hot, searchIdleHint(vm.kind), ::submit, AppGraph.searchHistory::remove, AppGraph.searchHistory::clear)
                } else {
                    key(results.query) {
                        SearchResultContent(results, vm, onVideo, onAuthor, onBangumi)
                    }
                }
            }
            if (panelVisible) {
                SearchSuggestionPanel(
                    suggestions,
                    selectedSuggestion,
                    ::submit,
                    Modifier.align(Alignment.TopCenter).widthIn(max = 960.dp).fillMaxWidth()
                        .padding(start = 65.dp, end = 12.dp, top = 3.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultContent(
    results: SearchResults,
    vm: SearchViewModel,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onBangumi: (BangumiItem) -> Unit,
) {
    val content = results.content
    val kind = results.query.kind
    if (vm.draft.trim() != results.query.keyword) {
        Text(
            text = "当前结果：${results.query.keyword}",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (kind == ContentKind.Video) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchOrder.entries.forEach { order ->
                BiliFilterChip(
                    selected = results.query.order == order,
                    onClick = { vm.selectOrder(order) },
                    label = { Text(order.label) },
                )
            }
        }
    }
    val shown = app.ailiaili.core.designsystem.rememberFilteredVideos(content.items)
    val shownUsers = app.ailiaili.core.designsystem.rememberFilteredEntries(content.userItems) {
        ContentFilter.allows(VideoItem(author = it.name, ownerMid = it.mid))
    }
    val blockedOut = when (kind) {
        ContentKind.Bangumi -> false
        ContentKind.User -> content.userItems.isNotEmpty() && shownUsers.isEmpty()
        else -> content.items.isNotEmpty() && shown.isEmpty()
    } && ContentFilter.active
    val empty = when (kind) {
        ContentKind.Bangumi -> content.bangumiItems.isEmpty()
        ContentKind.User -> shownUsers.isEmpty()
        else -> shown.isEmpty()
    } && !blockedOut
    val updating = results.page > 0 && results.requestedPage == 1
    if (updating && !empty) {
        if (results.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        } else if (results.error != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    results.error,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = vm::retry) { Text("重试刷新") }
            }
        }
    }
    val autoLoadMore = if (content.hasMore && results.error == null) vm::loadMore else null
    val footer: (@Composable () -> Unit)? = if (content.hasMore && !updating) {
        {
            BiliPaginationFooter(
                results.loading,
                results.error,
                if (results.error == null) vm::loadMore else vm::retry,
            )
        }
    } else {
        null
    }
    FilterAutoLoad(
        shownEmpty = blockedOut,
        hasMore = autoLoadMore != null,
        loading = results.loading,
        onLoadMore = vm::loadMore,
    )
    LoadBox(
        loading = results.loading && (results.page == 0 || empty),
        error = results.error,
        empty = empty,
        emptyText = searchEmptyMessage(kind),
        onRetry = vm::retry,
        emptyMessage = "试试缩短关键词、切换搜索类型，或粘贴 B 站链接",
    ) {
        when {
            blockedOut -> BiliEmptyState(
                title = "这些结果都被屏蔽了",
                message = ContentFilter.summary(),
                icon = Icons.Filled.FilterList,
                modifier = Modifier.fillMaxSize(),
                action = {
                    TextButton(onClick = ContentFilter::clear) { Text("清除屏蔽") }
                    footer?.invoke()
                },
            )
            kind == ContentKind.Bangumi -> BangumiGrid(content.bangumiItems, onBangumi, autoLoadMore, footer)
            kind == ContentKind.User -> UserSearchList(
                users = shownUsers,
                onUser = { user ->
                    onAuthor(VideoItem(ownerMid = user.mid, author = user.name, ownerFace = user.face))
                },
                onLoadMore = autoLoadMore,
                footer = footer,
            )
            else -> VideoGrid(shown, onVideo, onAuthor, onLoadMore = autoLoadMore, footer = footer)
        }
    }
}

@Composable
internal fun BangumiGrid(
    items: List<BangumiItem>,
    onClick: (BangumiItem) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    val loadLatest by rememberUpdatedState(onLoadMore)
    if (onLoadMore != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (items.isNotEmpty() && last >= items.size - 4) loadLatest?.invoke()
                }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(LocalWindowForm.current.gridMin),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.seasonId }) { item ->
            BangumiCard(item, onClick, Modifier.animateItem())
        }
        if (footer != null) item(span = { GridItemSpan(maxLineSpan) }) { footer() }
    }
}

@Composable
private fun BangumiCard(
    item: BangumiItem,
    onClick: (BangumiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    AliStagePanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.clickable { onClick(item) }) {
            Box {
                AsyncImage(
                    model = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    contentScale = ContentScale.Crop,
                )
                if (item.badge.isNotBlank()) {
                    BiliBadge(
                        text = item.badge,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        tone = BiliBadgeTone.Dark,
                        style = BiliBadgeStyle.Solid,
                    )
                }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.seasonTypeName.isNotBlank()) {
                        BiliBadge(
                            text = item.seasonTypeName,
                            tone = BiliBadgeTone.Neutral,
                            style = BiliBadgeStyle.Outline,
                        )
                    }
                    if (item.indexShow.isNotBlank()) {
                        MetaRow(
                            items = listOf(
                                MetaSpec(Icons.Filled.Tv, item.indexShow, MetricTone.Neutral),
                            ),
                            maxItems = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearchList(
    users: List<SearchUser>,
    onUser: (SearchUser) -> Unit,
    onLoadMore: (() -> Unit)?,
    footer: (@Composable () -> Unit)?,
) {
    val state = rememberLazyListState()
    val loadLatest by rememberUpdatedState(onLoadMore)
    if (onLoadMore != null) {
        LaunchedEffect(state, users.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (users.isNotEmpty() && last >= users.size - 4) loadLatest?.invoke()
                }
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().smoothWheelScroll(state, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(users, key = { it.mid }) { user ->
            AliStagePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onUser(user) },
                contentPadding = PaddingValues(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user.face.isNotBlank()) {
                        AsyncImage(
                            model = app.ailiaili.core.model.cdnThumb(user.face, 110),
                            contentDescription = user.name,
                            modifier = Modifier.size(52.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                user.name.take(1).ifBlank { "用" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                user.name.ifBlank { "用户" },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (user.level > 0) {
                                BiliBadge(
                                    text = "LV${user.level}",
                                    tone = BiliBadgeTone.Neutral,
                                    style = BiliBadgeStyle.Soft,
                                )
                            }
                            if (user.official.isNotBlank()) {
                                BiliBadge(
                                    text = user.official.take(8),
                                    style = BiliBadgeStyle.Outline,
                                )
                            }
                        }
                        if (user.sign.isNotBlank()) {
                            Text(
                                user.sign,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        MetaRow(
                            items = listOf(
                                MetaSpec(Icons.Filled.Person, "${formatCount(user.fans)} 粉丝", MetricTone.Neutral),
                                MetaSpec(Icons.Filled.PlayArrow, "${formatCount(user.videos)} 投稿", MetricTone.Neutral),
                            ),
                            maxItems = 2,
                        )
                    }
                }
            }
        }
        if (footer != null) item { footer() }
    }
}

private fun searchIdleHint(kind: ContentKind): String = when (kind) {
    ContentKind.Video -> "输入关键词，搜索你想看的视频"
    ContentKind.Live -> "输入关键词，搜索你想看的直播间"
    ContentKind.Bangumi -> "输入关键词，搜索你想看的番剧"
    ContentKind.User -> "输入关键词，搜索 UP 主"
}
