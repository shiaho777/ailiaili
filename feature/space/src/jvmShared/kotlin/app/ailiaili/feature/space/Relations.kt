package app.ailiaili.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.RegisterPageRefresh
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.model.FollowUser
import kotlinx.coroutines.delay

@Composable
fun RelationsRoute(mid: Long, followers: Boolean, onBack: () -> Unit, onSpace: (Long) -> Unit) {
    val session by AppGraph.repo.session.collectAsState()
    val title = if (followers) "粉丝" else "关注"
    var refresh by remember { mutableIntStateOf(0) }
    RegisterPageRefresh { refresh++ }
    var page by remember(refresh, session.dedeUserId) { mutableIntStateOf(1) }
    var users by remember(refresh, session.dedeUserId) { mutableStateOf<List<FollowUser>>(emptyList()) }
    var loading by remember(refresh, session.dedeUserId) { mutableStateOf(true) }
    var more by remember(refresh, session.dedeUserId) { mutableStateOf(false) }
    var error by remember(refresh, session.dedeUserId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var query by remember(session.dedeUserId) { mutableStateOf("") }
    val list = rememberLazyListState()
    fun next() {
        if (!loading && more && error == null) {
            loading = true
            page++
        }
    }
    LaunchedEffect(page, refresh, retry, session.dedeUserId) {
        loading = true
        error = null
        val result = if (followers) AppGraph.repo.followers(mid, page) else AppGraph.repo.followings(mid, page)
        result.onSuccess { loaded ->
            val merged = (users + loaded.items).distinctBy { it.mid }
            more = loaded.hasMore && merged.size > users.size
            users = merged
        }.onFailure { error = it.message ?: "列表加载失败" }
        loading = false
    }
    val shown = remember(users, query) {
        val needle = query.trim()
        users.filter { it.name.contains(needle, ignoreCase = true) }
    }
    LaunchedEffect(query, users.size, more, loading, error) {
        if (query.isNotBlank() && more && !loading && error == null) {
            delay(300)
            next()
        }
    }
    LaunchedEffect(list, shown.size, more) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { index ->
            if (index >= 0 && index >= shown.size - 4) next()
        }
    }
    Scaffold(topBar = { BiliBackHeader(title, onBack) }) { padding ->
        AliStageBody(Modifier.padding(padding), maxWidth = 800.dp) {
            BiliSearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = ::next,
                placeholder = "搜索$title",
                accessibilityLabel = "搜索$title",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LoadBox(loading || more && query.isNotBlank() && error == null, error.takeIf { shown.isEmpty() }, shown.isEmpty(), "没有匹配的用户", { retry++ }) {
                LazyColumn(
                    Modifier.fillMaxSize().smoothWheelScroll(list, LocalWheelSpeed.current),
                    state = list,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.mid }) { user ->
                        VideoListRow(title = user.name, cover = user.face, onClick = { onSpace(user.mid) }, coverWidth = 56.dp, panel = true) {
                            Text(user.sign, maxLines = 1)
                        }
                    }
                    if (error != null) {
                        item { TextButton(onClick = { retry++ }) { Text(error.orEmpty() + " · 重试") } }
                    }
                }
            }
        }
    }
}
