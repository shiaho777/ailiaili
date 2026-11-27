package app.ailiaili.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.RegisterPageRefresh
import app.ailiaili.core.model.BangumiItem

@Composable
fun FollowedSeasonsRoute(onBack: () -> Unit, onSeason: (BangumiItem) -> Unit) {
    val session by AppGraph.repo.session.collectAsState()
    var type by remember { mutableIntStateOf(1) }
    var refresh by remember { mutableIntStateOf(0) }
    RegisterPageRefresh { refresh++ }
    var page by remember(type, refresh, session.dedeUserId) { mutableIntStateOf(1) }
    var items by remember(type, refresh, session.dedeUserId) { mutableStateOf<List<BangumiItem>>(emptyList()) }
    var loading by remember(type, refresh, session.dedeUserId) { mutableStateOf(true) }
    var hasMore by remember(type, refresh, session.dedeUserId) { mutableStateOf(false) }
    var error by remember(type, refresh, session.dedeUserId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(type, page, refresh, retry, session.dedeUserId) {
        loading = true
        error = null
        AppGraph.repo.followedBangumi(page, type, fresh = page == 1).onSuccess { result ->
            val merged = (items + result.items).distinctBy { it.seasonId }
            hasMore = result.hasMore && merged.size > items.size
            items = merged
        }.onFailure { error = it.message ?: "追番列表加载失败" }
        loading = false
    }
    Scaffold(topBar = { BiliBackHeader("我的追番", onBack) }) { padding ->
        AliStageBody(Modifier.padding(padding), maxWidth = 1050.dp) {
            AliStageTabs(
                labels = listOf("番剧", "影视"),
                selected = type - 1,
                onSelect = { type = it + 1 },
                modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().padding(horizontal = 12.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LoadBox(loading, error.takeIf { items.isEmpty() }, items.isEmpty(), "还没有追看的内容", { retry++ }) {
                    BangumiGrid(items, onSeason, onLoadMore = {
                        if (!loading && hasMore && error == null) {
                            loading = true
                            page++
                        }
                    })
                }
            }
            if (error != null && items.isNotEmpty()) {
                TextButton(onClick = { retry++ }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(error.orEmpty() + " · 重试")
                }
            }
        }
    }
}
