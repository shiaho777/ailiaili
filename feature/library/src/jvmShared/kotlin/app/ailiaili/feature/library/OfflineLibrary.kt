package app.ailiaili.feature.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.RegisterPageRefresh
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.formatDay
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.model.qnLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

@Composable
fun CacheRoute(onBack: () -> Unit, openQueue: Boolean = false, onPlay: (CacheEntry) -> Unit) {
    var entries by remember { mutableStateOf<List<CacheEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<CacheEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var queueSelected by rememberSaveable { mutableStateOf(openQueue) }
    var taskFilter by rememberSaveable { mutableStateOf(DownloadTaskFilter.All) }
    val queueList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    val settings by AppGraph.repo.settings.collectAsState()
    val tasks by AppGraph.downloads.tasks.collectAsState()
    val network by AppGraph.downloads.network.collectAsState()
    val completedRevision by AppGraph.downloads.completedRevision.collectAsState()
    val refresh = { reload++; Unit }
    RegisterPageRefresh(refresh)
    LaunchedEffect(reload, settings.downloadDir, completedRevision) {
        loading = true
        error = null
        withContext(ioDispatcher) { runCatching { AppGraph.cache.list() } }
            .onSuccess { entries = it }
            .onFailure { error = "读取离线缓存失败，请重试" }
        loading = false
    }
    val shown = remember(entries, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) entries else entries.filter {
            it.title.contains(keyword, ignoreCase = true) || it.bvid.contains(keyword, ignoreCase = true)
        }
    }
    Scaffold(topBar = {
        AliStagePageHeader("离线缓存", onBack = onBack) {
            AliStageIconButton(Icons.Filled.Refresh, "刷新缓存", refresh, enabled = !loading && !deleting)
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding)) {
            AliStageTabs(
                labels = listOf("已缓存", if (tasks.isEmpty()) "下载任务" else "下载任务 · ${tasks.size}"),
                selected = if (queueSelected) 1 else 0,
                onSelect = { queueSelected = it == 1 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (queueSelected) {
                DownloadTaskList(
                    tasks, network, queueList, taskFilter,
                    onFilter = {
                        taskFilter = it
                        queueList.requestScrollToItem(0)
                    },
                    onOpenCache = { queueSelected = false },
                )
                return@AliStageBody
            }
            if (entries.isNotEmpty() || query.isNotEmpty()) {
                BiliSearchField(
                    value = query,
                    onValueChange = { query = it; list.requestScrollToItem(0) },
                    onSearch = { focus.clearFocus() },
                    placeholder = "搜索标题或 BV 号",
                    accessibilityLabel = "搜索离线缓存",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    if (query.isBlank()) "${entries.size} 个缓存" else "${shown.size} 个匹配 · 共 ${entries.size} 个缓存",
                    Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (error != null && shown.isNotEmpty()) {
                TextButton(onClick = refresh, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Text("读取离线缓存失败，重试")
                }
            }
            LoadBox(
                loading = loading,
                error = error,
                empty = shown.isEmpty(),
                emptyText = if (query.isBlank()) "还没有缓存" else "没有匹配的离线缓存",
                emptyMessage = if (query.isBlank()) "可在播放器里下载视频，离线时在这里观看。"
                else "试试缩短关键词，或通过 BV 号搜索。",
                onRetry = refresh,
            ) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.smoothWheelScroll(list, LocalWheelSpeed.current),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(shown, key = { it.key }) { entry ->
                        VideoListRow(
                            title = entry.title.ifBlank { entry.bvid },
                            cover = entry.cover,
                            onClick = { onPlay(entry) },
                            panel = true,
                            coverWidth = 104.dp,
                            meta = {
                                BiliBadge(
                                    text = qnLabel(entry.qn),
                                    style = BiliBadgeStyle.Soft,
                                )
                                Text(
                                    cacheMetadata(entry),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailing = {
                                AliStageIconButton(
                                    Icons.Filled.Delete, "删除缓存",
                                    onClick = { confirmDelete = entry },
                                    enabled = !deleting,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    confirmDelete?.let { entry ->
        AliStageDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除离线缓存？") },
            text = { Text(entry.title.ifBlank { entry.bvid }) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    deleting = true
                    scope.launch {
                        withContext(ioDispatcher) { runCatching { AppGraph.cache.delete(entry.bvid, entry.cid) } }
                            .onFailure { QuickActions.showToast("未能删除缓存，请重试") }
                        deleting = false
                        refresh()
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

private fun cacheMetadata(entry: CacheEntry): String {
    val day = formatDay(entry.storedAtMs / 1000)
    return listOf(day, formatBytes(entry.storedBytes)).filter { it.isNotEmpty() }.joinToString(" · ")
}

internal fun formatBytes(bytes: Long): String =
    if (bytes < 1024 * 1024) {
        "${(bytes / 1024.0).roundToLong()} KB"
    } else {
        "${(bytes / (1024.0 * 1024.0) * 10).roundToLong() / 10.0} MB"
    }
