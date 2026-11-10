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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.BiliSectionHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliAssistChip
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliSurfaceCard
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.FilterAutoLoad
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.LocalWindowForm
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
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
import app.ailiaili.core.model.searchKindSwitch
import app.ailiaili.core.model.searchKindSwitchShouldLoad
import app.ailiaili.core.model.searchPageError
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class SearchViewModel(initial: String) : ViewModel() {
    var keyword by mutableStateOf(initial)
    var kind by mutableStateOf(AppGraph.repo.settings.value.lastSearchKind)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var bangumiItems by mutableStateOf<List<BangumiItem>>(emptyList())
    var userItems by mutableStateOf<List<SearchUser>>(emptyList())
    var hot by mutableStateOf<List<String>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    var searched by mutableStateOf(initial.isNotBlank())
    private var loadGen = 0

    init {
        val hit = UrlParser.parse(initial)
        if (hit is UrlHit.Keyword && hit.word.isNotBlank()) search(hit.word)
        viewModelScope.launch { hot = AppGraph.repo.hotSearch().getOrDefault(emptyList()) }
    }

    fun search(word: String = keyword) {
        val q = word.trim()
        if (!searchKindSwitchShouldLoad(q)) {
            loadGen += 1
            loading = false
            searched = false
            return
        }
        keyword = q
        searched = true
        page = 1
        AppGraph.searchHistory.add(q)
        load(reset = true)
    }

    fun selectKind(next: ContentKind) {
        if (next == kind) return
        AppGraph.repo.updateSettings { it.copy(lastSearchKind = next) }
        val switched = searchKindSwitch(keyword, loadGen, searched)
        loadGen = switched.loadGen
        kind = next
        items = emptyList()
        bangumiItems = emptyList()
        userItems = emptyList()
        error = null
        hasMore = false
        loading = switched.loading
        searched = switched.searched
        if (switched.loading) search()
    }

    fun loadMore() {
        if (loading || !hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        val token = ++loadGen
        val requestPage = if (reset) 1 else page + 1
        loading = true
        error = null
        val q = keyword
        val searchKind = kind
        viewModelScope.launch {
            val result = AppGraph.repo.search(q, requestPage, searchKind)
            if (token != loadGen) return@launch
            loading = false
            result.onSuccess { pageData ->
                page = requestPage
                when (searchKind) {
                    ContentKind.Bangumi -> {
                        bangumiItems = if (reset) {
                            pageData.bangumiItems
                        } else {
                            mergeBangumiBySeason(bangumiItems, pageData.bangumiItems)
                        }
                        if (reset) {
                            items = emptyList()
                            userItems = emptyList()
                        }
                    }
                    ContentKind.Video, ContentKind.Live -> {
                        items = if (reset) pageData.items else items + pageData.items
                        if (reset) {
                            bangumiItems = emptyList()
                            userItems = emptyList()
                        }
                    }
                    ContentKind.User -> {
                        userItems = if (reset) pageData.userItems else userItems + pageData.userItems
                        if (reset) {
                            items = emptyList()
                            bangumiItems = emptyList()
                        }
                    }
                }
                hasMore = pageData.hasMore
                error = searchPageError(pageData.blocked)
            }.onFailure { error = it.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchRoute(
    initial: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onBangumi: (BangumiItem) -> Unit = {},
    vm: SearchViewModel = appViewModel(key = "search-$initial") { SearchViewModel(initial) },
) {
    val history by AppGraph.searchHistory.items.collectAsState()
    fun submit(raw: String) {
        when (val hit = UrlParser.parse(raw)) {
            is UrlHit.Video -> onVideo(VideoItem(bvid = hit.bvid))
            is UrlHit.Space -> onAuthor(VideoItem(ownerMid = hit.mid))
            is UrlHit.Live -> onVideo(VideoItem(roomId = hit.roomId, title = "直播"))
            is UrlHit.Keyword -> vm.search(hit.word)
        }
    }
    LaunchedEffect(initial) {
        val hit = UrlParser.parse(initial)
        if (hit !is UrlHit.Keyword) submit(initial)
    }
    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 56.dp)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BiliHeaderAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                BiliSearchField(
                    value = vm.keyword,
                    onValueChange = { vm.keyword = it },
                    onSearch = { submit(vm.keyword) },
                    placeholder = "视频、UP 主、直播，也可以粘贴链接",
                    modifier = Modifier.weight(1f),
                    autoFocus = !vm.searched,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BiliFilterChip(
                    selected = vm.kind == ContentKind.Video,
                    onClick = { vm.selectKind(ContentKind.Video) },
                    label = { Text("视频") },
                )
                BiliFilterChip(
                    selected = vm.kind == ContentKind.Live,
                    onClick = { vm.selectKind(ContentKind.Live) },
                    label = { Text("直播") },
                )
                BiliFilterChip(
                    selected = vm.kind == ContentKind.Bangumi,
                    onClick = { vm.selectKind(ContentKind.Bangumi) },
                    label = { Text("番剧") },
                )
                BiliFilterChip(
                    selected = vm.kind == ContentKind.User,
                    onClick = { vm.selectKind(ContentKind.User) },
                    label = { Text("用户") },
                )
            }
            if (!vm.searched) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        searchIdleHint(vm.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (history.isNotEmpty()) {
                        BiliSectionHeader(
                            title = "最近搜索",
                            action = "清空",
                            onAction = { AppGraph.searchHistory.clear() },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            history.forEach { word ->
                                BiliAssistChip(
                                    label = word,
                                    onClick = { vm.search(word) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (vm.hot.isNotEmpty()) {
                        BiliSectionHeader(title = "热搜", modifier = Modifier.padding(top = 8.dp))
                        vm.hot.forEachIndexed { index, word ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { vm.search(word) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BiliBadge(
                                    text = "${index + 1}",
                                    tone = if (index < 3) BiliBadgeTone.Accent else BiliBadgeTone.Neutral,
                                    style = if (index < 3) BiliBadgeStyle.Solid else BiliBadgeStyle.Soft,
                                )
                                Text(
                                    text = word,
                                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                )
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (index < 3) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                val shown = vm.items.filter { ContentFilter.allows(it) }
                val shownUsers = vm.userItems.filter {
                    ContentFilter.allows(VideoItem(author = it.name, ownerMid = it.mid))
                }
                val blockedOut = when (vm.kind) {
                    ContentKind.Bangumi -> false
                    ContentKind.User -> vm.userItems.isNotEmpty() && shownUsers.isEmpty() && ContentFilter.active
                    else -> vm.items.isNotEmpty() && shown.isEmpty() && ContentFilter.active
                }
                val empty = when (vm.kind) {
                    ContentKind.Bangumi -> vm.bangumiItems.isEmpty()
                    ContentKind.User -> shownUsers.isEmpty() && !blockedOut
                    else -> shown.isEmpty() && !blockedOut
                }
                FilterAutoLoad(
                    shownEmpty = blockedOut,
                    hasMore = vm.hasMore,
                    loading = vm.loading,
                    onLoadMore = vm::loadMore,
                )
                LoadBox(vm.loading, vm.error, empty, searchEmptyMessage(vm.kind), { vm.search() }) {
                    when {
                        blockedOut -> BiliEmptyState(
                            title = "这些结果都被屏蔽了",
                            message = ContentFilter.summary(),
                            icon = Icons.Filled.FilterList,
                            modifier = Modifier.fillMaxSize(),
                            action = {
                                androidx.compose.material3.TextButton(onClick = { ContentFilter.clear() }) {
                                    Text("清除屏蔽")
                                }
                            },
                        )
                        vm.kind == ContentKind.Bangumi -> BangumiGrid(
                            vm.bangumiItems,
                            onBangumi,
                            onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        )
                        vm.kind == ContentKind.User -> UserSearchList(
                            users = shownUsers,
                            onUser = { user ->
                                onAuthor(VideoItem(ownerMid = user.mid, author = user.name, ownerFace = user.face))
                            },
                            onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        )
                        else -> VideoGrid(shown, onVideo, onAuthor, onLoadMore = if (vm.hasMore) vm::loadMore else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun BangumiGrid(
    items: List<BangumiItem>,
    onClick: (BangumiItem) -> Unit,
    onLoadMore: (() -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    if (onLoadMore != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (items.isNotEmpty() && last >= items.size - 4) onLoadMore()
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
    }
}

@Composable
private fun BangumiCard(
    item: BangumiItem,
    onClick: (BangumiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
                                MetaSpec(Icons.Filled.Tv, item.indexShow, MetricTone.Accent),
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
) {
    val state = rememberLazyListState()
    if (onLoadMore != null) {
        LaunchedEffect(state, users.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (users.isNotEmpty() && last >= users.size - 4) onLoadMore()
                }
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(users, key = { it.mid }) { user ->
            BiliSurfaceCard(
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
                            model = user.face,
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
                                    tone = BiliBadgeTone.Accent,
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
                                MetaSpec(Icons.Filled.Person, "${formatCount(user.fans)} 粉丝", MetricTone.Accent),
                                MetaSpec(Icons.Filled.PlayArrow, "${formatCount(user.videos)} 投稿", MetricTone.Neutral),
                            ),
                            maxItems = 2,
                        )
                    }
                }
            }
        }
    }
}

private fun searchIdleHint(kind: ContentKind): String = when (kind) {
    ContentKind.Video -> "输入关键词，搜索你想看的视频"
    ContentKind.Live -> "输入关键词，搜索你想看的直播间"
    ContentKind.Bangumi -> "输入关键词，搜索你想看的番剧"
    ContentKind.User -> "输入关键词，搜索 UP 主"
}

private fun mergeBangumiBySeason(
    current: List<BangumiItem>,
    extra: List<BangumiItem>,
): List<BangumiItem> {
    val seen = current.mapTo(mutableSetOf()) { it.seasonId }
    return current + extra.filter { seen.add(it.seasonId) }
}
