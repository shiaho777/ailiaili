package app.ailiaili.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.SearchPage
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.UrlParser
import app.ailiaili.core.model.searchPageError
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class SearchOrder(val value: String, val label: String) {
    Relevance("totalrank", "综合"),
    Plays("click", "最多播放"),
    Latest("pubdate", "最新发布"),
    Danmaku("dm", "最多弹幕"),
}

internal data class SearchQuery(
    val keyword: String,
    val kind: ContentKind,
    val order: SearchOrder,
)

internal data class SearchResults(
    val query: SearchQuery,
    val content: SearchPage = SearchPage(emptyList(), hasMore = false),
    val page: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val requestedPage: Int? = null,
) {
    val nextPage get() = page + 1

    fun receive(requestedPage: Int, result: Result<SearchPage>): SearchResults {
        val received = result.getOrElse {
            return copy(loading = false, error = it.message ?: "搜索失败，请重试", requestedPage = requestedPage)
        }
        searchPageError(received.blocked)?.let {
            return copy(loading = false, error = it, requestedPage = requestedPage)
        }
        val previous = content.takeUnless { requestedPage == 1 }
        val merged = received.copy(
            items = (previous?.items.orEmpty() + received.items).distinctBy { it.stableKey },
            bangumiItems = (previous?.bangumiItems.orEmpty() + received.bangumiItems).distinctBy { it.seasonId },
            userItems = (previous?.userItems.orEmpty() + received.userItems).distinctBy { it.mid },
        )
        return copy(
            content = merged,
            page = requestedPage,
            loading = false,
            error = null,
            requestedPage = null,
        )
    }
}

class SearchViewModel(initial: String) : ViewModel() {
    var draft by mutableStateOf(initial)
        private set
    var kind by mutableStateOf(AppGraph.repo.settings.value.lastSearchKind)
        private set
    internal var order by mutableStateOf(SearchOrder.Relevance)
        private set
    var hot by mutableStateOf<List<String>>(emptyList())
        private set
    internal var results by mutableStateOf<SearchResults?>(null)
        private set
    private var loadJob: Job? = null
    private var hotJob: Job? = null
    private var initialLink: String? = null

    init {
        val hit = UrlParser.parse(initial)
        if (hit is UrlHit.Keyword) {
            if (hit.word.isNotBlank()) search(hit.word)
        } else {
            initialLink = initial
        }
        loadHotSearch()
    }

    fun takeInitialLink(): String? = initialLink.also { initialLink = null }

    fun editDraft(value: String) {
        draft = value
        if (value.isEmpty()) {
            loadJob?.cancel()
            results = null
        }
    }

    fun search(word: String) {
        val keyword = word.trim()
        if (keyword.isEmpty()) {
            editDraft("")
            return
        }
        draft = keyword
        AppGraph.searchHistory.add(keyword)
        startSearch(SearchQuery(keyword, kind, order))
    }

    fun selectKind(next: ContentKind) {
        if (next == kind) return
        kind = next
        AppGraph.repo.updateSettings { it.copy(lastSearchKind = next) }
        results?.query?.let { startSearch(it.copy(kind = next, order = order)) }
    }

    internal fun selectOrder(next: SearchOrder) {
        if (next == order || kind != ContentKind.Video) return
        order = next
        results?.query?.let { startSearch(it.copy(order = next)) }
    }

    fun refresh() {
        val current = results
        if (current == null) loadHotSearch() else load(current, page = 1)
    }

    private fun loadHotSearch() {
        if (hotJob?.isActive == true) return
        hotJob = viewModelScope.launch { AppGraph.repo.hotSearch().onSuccess { hot = it } }
    }

    fun retry() {
        val current = results ?: return
        val failedPage = current.requestedPage ?: return
        if (!current.loading) load(current, page = failedPage)
    }

    fun loadMore() {
        val current = results ?: return
        if (current.loading || !current.content.hasMore) return
        load(current)
    }

    private fun startSearch(query: SearchQuery) = load(SearchResults(query))

    private fun load(current: SearchResults, page: Int = current.nextPage) {
        loadJob?.cancel()
        results = current.copy(loading = true, error = null, requestedPage = page)
        loadJob = viewModelScope.launch {
            val query = current.query
            val result = AppGraph.repo.search(query.keyword, page, query.kind, query.order.value)
            if (isActive) results = current.receive(page, result)
        }
    }
}
