package app.ailiaili.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class FavoriteVideosViewModel(private val mediaId: Long) : ViewModel() {
    var query by mutableStateOf("")
        private set
    var appliedQuery by mutableStateOf("")
        private set
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var page by mutableIntStateOf(1)
        private set
    var totalPages by mutableIntStateOf(0)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var mutating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var request: Job? = null

    init { load() }

    fun search(text: String) {
        query = text
        load(1, debounce = true)
    }

    fun load(target: Int = page, debounce: Boolean = false) {
        request?.cancel()
        val keyword = query.trim()
        val requestedPage = if (keyword == appliedQuery) target.coerceAtLeast(1) else 1
        loading = true
        error = null
        request = viewModelScope.launch {
            if (debounce) delay(300)
            val result = AppGraph.repo.favVideos(mediaId, requestedPage, keyword)
            if (!isActive) return@launch
            result.onSuccess { resultPage ->
                if (requestedPage > 1 && resultPage.total == 0L && resultPage.items.isEmpty() && !resultPage.hasMore) {
                    load(requestedPage - 1)
                    return@launch
                }
                appliedQuery = keyword
                page = requestedPage
                items = resultPage.items.distinctBy { it.stableKey }
                totalPages = ((resultPage.total + 19) / 20).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                if (totalPages in 1 until page) totalPages = page
                hasMore = resultPage.hasMore || totalPages > page
            }.onFailure { error = it.message ?: "收藏加载失败" }
            loading = false
        }
    }

    fun remove(aids: List<Long>, onRemoved: () -> Unit) {
        if (aids.isEmpty() || mutating || loading) return
        mutating = true
        viewModelScope.launch {
            AppGraph.repo.batchRemoveFav(mediaId, aids)
                .onSuccess { onRemoved(); load() }
                .onFailure { QuickActions.showToast(it.message ?: "移出收藏失败") }
            mutating = false
        }
    }

    suspend fun playableQueue(): Result<List<VideoItem>> {
        val keyword = query.trim()
        val videos = linkedMapOf<String, VideoItem>()
        var pageNumber = 1
        val seenPages = mutableSetOf<List<String>>()
        while (true) {
            val response = AppGraph.repo.favVideos(mediaId, pageNumber, keyword)
            val page = response.getOrElse {
                if (it is CancellationException) throw it
                return Result.failure(it)
            }
            val identifiers = page.items.map { it.stableKey }
            if (identifiers.isNotEmpty() && !seenPages.add(identifiers)) break
            page.items.forEach { videos.putIfAbsent(it.stableKey, it) }
            if (!page.hasMore && (page.total <= 0 || pageNumber * 20L >= page.total)) break
            pageNumber++
        }
        return Result.success(videos.values.toList())
    }
}
