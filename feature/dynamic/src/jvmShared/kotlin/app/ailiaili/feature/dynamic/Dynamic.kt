package app.ailiaili.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliPageTitle
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.VideoGrid
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.designsystem.shellNavigationBottom
import app.ailiaili.core.model.FollowUser
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.formatCount
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class DynamicViewModel : ViewModel() {
    var users by mutableStateOf<List<FollowUser>>(emptyList())
    var usersHasMore by mutableStateOf(false)
    var selected by mutableStateOf<FollowUser?>(null)
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(false)
    var liveNow by mutableStateOf<List<VideoItem>>(emptyList())
    var liveLoading by mutableStateOf(false)
    var liveOpen by mutableStateOf(true)
    private var liveTicket = 0
    private var usersPage by mutableIntStateOf(1)
    private var usersLoading = false
    private var offset: String? = null
    private var upPage = 1
    private var selfMid = 0L
    private var ticket = 0
    private val seen = mutableSetOf<String>()

    fun refresh() {
        if (!AppGraph.repo.session.value.loggedIn) {
            users = emptyList()
            items = emptyList()
            liveNow = emptyList()
            error = "请先登录后查看动态"
            return
        }
        viewModelScope.launch {
            val mid = AppGraph.repo.session.value.dedeUserId?.toLongOrNull()
                ?: AppGraph.repo.userState().getOrNull()?.mid
                ?: 0L
            if (mid <= 0) {
                error = "请先登录后查看动态"
                return@launch
            }
            selfMid = mid
            loadLive()
            AppGraph.repo.followings(mid, 1).onSuccess { page ->
                users = page.items
                usersPage = 1
                usersHasMore = page.hasMore
                if (selected != null && page.items.none { it.mid == selected?.mid }) selected = null
            }
            loadFeed(reset = true)
        }
    }

    /**
     * 关注直播看板。与动态流并行加载，失败静默：看板是锦上添花，
     * 不能因为它给整页报错。
     */
    private fun loadLive() {
        val current = ++liveTicket
        liveLoading = true
        viewModelScope.launch {
            val result = AppGraph.repo.followingLive()
            if (current != liveTicket) return@launch
            liveLoading = false
            result.onSuccess { liveNow = it }
        }
    }

    fun selectAll() {
        if (selected == null) return
        selected = null
        loadFeed(reset = true)
    }

    fun select(user: FollowUser) {
        if (selected?.mid == user.mid) return
        selected = user
        loadFeed(reset = true)
    }

    fun loadMoreUsers() {
        if (usersLoading || !usersHasMore || selfMid <= 0) return
        val next = usersPage + 1
        usersLoading = true
        viewModelScope.launch {
            AppGraph.repo.followings(selfMid, next).onSuccess { page ->
                usersPage = next
                val known = users.map { it.mid }.toSet()
                users = users + page.items.filter { it.mid !in known }
                usersHasMore = page.hasMore
            }
            usersLoading = false
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loadFeed(reset = false)
    }

    private fun loadFeed(reset: Boolean) {
        val current = if (reset) ++ticket else ticket
        val up = selected
        if (!reset && !hasMore) return
        loading = true
        if (reset) {
            error = null
            items = emptyList()
            seen.clear()
            offset = null
            upPage = 1
            hasMore = false
        }
        viewModelScope.launch {
            if (up == null) {
                val result = AppGraph.repo.dynamics(if (reset) null else offset)
                if (current != ticket) return@launch
                loading = false
                result.onSuccess { page ->
                    val previous = offset
                    offset = page.nextOffset
                    val fresh = page.items.filter { it.bvid.isNotBlank() && seen.add(it.bvid) }
                    items = if (reset) fresh else items + fresh
                    hasMore = !page.nextOffset.isNullOrBlank() && page.nextOffset != previous
                    if (items.isEmpty()) error = "关注的 UP 最近没有视频投稿"
                }.onFailure {
                    if (items.isEmpty()) error = it.message
                    hasMore = false
                }
            } else {
                val page = if (reset) 1 else upPage
                val result = AppGraph.repo.spaceVideos(up.mid, page)
                if (current != ticket) return@launch
                loading = false
                result.onSuccess { list ->
                    upPage = page + 1
                    val fresh = list.filter { it.bvid.isNotBlank() && seen.add(it.bvid) }
                    items = if (reset) fresh else items + fresh
                    hasMore = list.size >= 30
                    if (items.isEmpty()) error = "该 UP 暂时没有投稿视频"
                }.onFailure {
                    if (items.isEmpty()) error = it.message
                    hasMore = false
                }
            }
        }
    }
}

@Composable
fun DynamicRoute(
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onLogin: () -> Unit,
    vm: DynamicViewModel = appViewModel { DynamicViewModel() },
) {
    val session by AppGraph.repo.session.collectAsState()
    val navBottom = shellNavigationBottom()
    val haptic = LocalHapticFeedback.current
    val userRow = rememberLazyListState()
    LaunchedEffect(session.loggedIn) { vm.refresh() }
    LaunchedEffect(userRow, vm.users.size, vm.usersHasMore) {
        snapshotFlow { userRow.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = userRow.layoutInfo.totalItemsCount
                if (total > 0 && last >= total - 3) vm.loadMoreUsers()
            }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BiliPageTitle("动态", Modifier.weight(1f))
            IconButton(onClick = vm::refresh, enabled = session.loggedIn) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
            }
        }
        if (!session.loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "登录后可查看你关注的 UP 主的动态",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
                Button(onClick = onLogin) { Text("登录哔哩哔哩") }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 720.dp && vm.users.isNotEmpty()
            Row(Modifier.fillMaxSize()) {
            if (wide) {
                LazyColumn(Modifier.width(232.dp).fillMaxHeight().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    item(key = "all-users") {
                        UserFilterRow(
                            name = "全部动态",
                            face = null,
                            selected = vm.selected == null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.selectAll()
                            },
                        )
                    }
                    items(vm.users, key = { it.mid }) { user ->
                        UserFilterRow(
                            name = user.name,
                            face = user.face,
                            selected = vm.selected?.mid == user.mid,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.select(user)
                            },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
            LiveBoard(
                liveNow = vm.liveNow,
                loading = vm.liveLoading,
                open = vm.liveOpen,
                onToggle = { vm.liveOpen = !vm.liveOpen },
                onVideo = onVideo,
            )
            if (!wide && (vm.users.isNotEmpty() || vm.selected != null)) {
                LazyRow(
                    state = userRow,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "all") {
                        BiliFilterChip(
                            selected = vm.selected == null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.selectAll()
                            },
                            label = { Text("全部") },
                        )
                    }
                    items(vm.users, key = { it.mid }) { user ->
                        BiliFilterChip(
                            selected = vm.selected?.mid == user.mid,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                vm.select(user)
                            },
                            leadingIcon = {
                                if (user.face.isNotBlank()) {
                                    AsyncImage(
                                        user.face,
                                        user.name,
                                        Modifier.size(22.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    user.name,
                                    fontWeight = if (vm.selected?.mid == user.mid) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (vm.selected == null) "全部动态" else "${vm.selected?.name} 的视频",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val picked = vm.selected
                    if (picked != null) {
                        Text(
                            "主页",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onAuthor(VideoItem(ownerMid = picked.mid, author = picked.name))
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = vm.loading && vm.items.isNotEmpty(),
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "关注的 UP 还没有新动态", vm::refresh) {
                    VideoGrid(
                        vm.items,
                        onVideo,
                        onAuthor,
                        onLoadMore = if (vm.hasMore) vm::loadMore else null,
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 16.dp + navBottom),
                    )
                }
            }
            }
            }
            }
        }
    }
}

@Composable
private fun UserFilterRow(
    name: String,
    face: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (face.isNullOrBlank()) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = face,
                    contentDescription = name,
                    modifier = Modifier.size(30.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = name,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveBoard(
    liveNow: List<VideoItem>,
    loading: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onVideo: (VideoItem) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "关注直播",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (liveNow.isNotEmpty()) {
                Text(
                    " · ${liveNow.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (open) "收起" else "展开",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        }
        if (open) {
            if (loading && liveNow.isEmpty()) {
                Text(
                    "正在查询关注主播…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else if (liveNow.isEmpty()) {
                Text(
                    "关注的主播暂时都没有在直播",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    items(liveNow, key = { it.roomId }) { item ->
                        LiveCard(item, onClick = { onVideo(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(item: VideoItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            BiliBadge(
                text = "直播中",
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                tone = BiliBadgeTone.Accent,
                style = BiliBadgeStyle.Solid,
            )
        }
        Text(
            text = item.title.ifBlank { "直播" },
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MetaRow(
            items = listOfNotNull(
                item.author.takeIf { it.isNotBlank() }?.let {
                    MetaSpec(Icons.Filled.Person, it, MetricTone.Neutral)
                },
                formatCount(item.viewCount).takeIf { it.isNotBlank() }?.let {
                    MetaSpec(Icons.Filled.Visibility, it, MetricTone.Online)
                },
            ),
            maxItems = 2,
        )
    }
}
