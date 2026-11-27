package app.ailiaili.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.model.LiveArea
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) { Recommend("推荐"), Popular("热门"), Ranking("排行榜"), Live("直播") }

class HomeViewModel : ViewModel() {
    var feedRevision by mutableIntStateOf(0)
        private set
    var feedPage by mutableIntStateOf(0)
    var feedMuted by mutableStateOf(true)
    var tab by mutableStateOf(HomeTab.Recommend)
        private set
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var paginationError by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var liveAreas by mutableStateOf<List<LiveArea>>(emptyList())
        private set
    var liveAreaId by mutableIntStateOf(-1)
        private set
    private var page = 0
    private var source = AppGraph.repo.settings.value.recommendSource
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            AppGraph.repo.liveAreas().onSuccess { liveAreas = it }
        }
    }

    fun selectLiveArea(id: Int) {
        if (liveAreaId == id) return
        liveAreaId = id
        if (tab == HomeTab.Live) switchFeed()
    }

    fun onSourceChanged() {
        val next = AppGraph.repo.settings.value.recommendSource
        if (next == source) return
        source = next
        if (tab == HomeTab.Recommend) switchFeed()
    }

    fun select(next: HomeTab) {
        if (tab == next) return
        tab = next
        switchFeed()
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        if (loading || !hasMore) return
        load(reset = false)
    }

    fun dismiss(item: VideoItem) {
        items = items.filterNot { it.stableKey == item.stableKey }
        QuickActions.showToast("已减少这类推荐")
        if (item.feedbackId.isBlank()) return
        viewModelScope.launch {
            if (AppGraph.repo.session.value.loggedIn) {
                AppGraph.repo.feedDislike(item.feedbackGoto.ifBlank { "av" }, item.feedbackId)
            }
        }
    }

    private fun switchFeed() {
        feedPage = 0
        items = emptyList()
        page = 0
        hasMore = false
        refresh()
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        val current = tab
        val area = liveAreaId
        val requestPage = if (reset) 1 else page + 1
        loading = true
        refreshing = reset
        if (reset) error = null
        paginationError = null
        loadJob = viewModelScope.launch {
            val result = when (current) {
                HomeTab.Recommend -> AppGraph.repo.recommend(requestPage)
                HomeTab.Popular -> AppGraph.repo.popular(requestPage)
                HomeTab.Ranking -> AppGraph.repo.ranking()
                HomeTab.Live -> AppGraph.repo.liveList(requestPage, area)
            }
            if (!isActive) return@launch
            loading = false
            refreshing = false
            result.onSuccess { list ->
                page = requestPage
                val merged = (if (reset) list else items + list).distinctBy { it.stableKey }
                val grew = reset || merged.size > items.size
                if (reset && items.isNotEmpty()) {
                    feedPage = 0
                    feedRevision++
                }
                items = merged
                hasMore = when (current) {
                    HomeTab.Ranking -> false
                    else -> list.isNotEmpty() && grew
                }
            }.onFailure {
                val message = it.message ?: "加载失败，请重试"
                if (reset) error = message else paginationError = message
            }
        }
    }
}
