package app.ailiaili.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.model.HistoryCursor
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class HistoryViewModel(private val live: Boolean) : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var page = 1
    private var cursor: HistoryCursor? = null
    private var request: Job? = null
    private var mutating = false
    private val removed = mutableSetOf<String>()

    init { reload() }

    fun reload() {
        if (mutating) return
        request?.cancel()
        page = 1
        cursor = null
        hasMore = false
        removed.clear()
        load(reset = true)
    }

    fun loadMore() {
        if (!loading && hasMore && !mutating) load(reset = false)
    }

    private fun load(reset: Boolean) {
        loading = true
        error = null
        request = viewModelScope.launch {
            val result = if (live) AppGraph.repo.liveHistory(cursor) else AppGraph.repo.history(page)
            if (!isActive) return@launch
            result.onSuccess { batch ->
                val incoming = batch.items.filterNot { it.stableKey in removed }
                val base = if (reset) emptyList() else items
                val merged = (base + incoming).distinctBy { it.stableKey }
                hasMore = batch.hasMore && (live || merged.size > base.size || batch.items.isEmpty())
                items = merged
                cursor = batch.next
                page++
            }.onFailure { error = it.message ?: "历史加载失败" }
            loading = false
        }
    }

    fun delete(selected: List<VideoItem>) {
        if (selected.isEmpty() || mutating) return
        mutating = true
        viewModelScope.launch {
            var failures = 0
            for (item in selected) {
                val id = if (item.isLive) item.roomId else item.aid
                if (id > 0 && AppGraph.repo.deleteHistory(id, live = item.isLive).isSuccess) {
                    removed.add(item.stableKey)
                    items = items.filterNot { it.stableKey == item.stableKey }
                } else {
                    failures++
                }
            }
            mutating = false
            if (failures > 0) QuickActions.showToast("$failures 条观看记录删除失败")
        }
    }

    fun clear() {
        if (mutating) return
        mutating = true
        viewModelScope.launch {
            val result = AppGraph.repo.clearHistory()
            mutating = false
            result.onSuccess { reload() }.onFailure { QuickActions.showToast(it.message ?: "清空历史失败") }
        }
    }
}
