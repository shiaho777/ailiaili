package app.ailiaili.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DownloadRequest
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.VideoDetail
import app.ailiaili.core.model.VideoPage
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.model.qnLabel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PlayerDownloadDialog(
    detail: VideoDetail,
    info: PlayInfo?,
    currentCid: Long,
    episodeId: Long,
    defaultQn: Int,
    onDismiss: () -> Unit,
    onOpenQueue: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val parts = remember(detail, currentCid) {
        detail.pages.filter { it.cid > 0 }.distinctBy { it.cid }.ifEmpty {
            listOf(VideoPage(currentCid, 1, detail.title, 0)).filter { it.cid > 0 }
        }
    }
    val currentQn = info?.actualQn?.takeIf { it > 0 } ?: 80
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var includeDanmaku by remember { mutableStateOf(AppGraph.repo.settings.value.downloadDanmaku) }
    var includeSubtitles by remember { mutableStateOf(AppGraph.repo.settings.value.downloadSubtitles) }
    val available = (info?.qualities.orEmpty().map { it.qn } + currentQn + defaultQn)
        .filter { it > 0 }.distinct().sorted()
    var quality by remember(currentCid) { mutableIntStateOf(defaultQn.takeIf { it > 0 } ?: currentQn) }
    var selected by remember(currentCid) {
        mutableStateOf(parts.firstOrNull { it.cid == currentCid }?.let { setOf(it.cid) }
            ?: parts.firstOrNull()?.let { setOf(it.cid) }.orEmpty())
    }
    fun toggle(cid: Long) {
        selected = if (cid in selected) selected - cid else selected + cid
    }

    AliStageDialog(
        onDismissRequest = onDismiss,
        title = { Text("缓存视频") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下载画质", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    available.forEach { qn ->
                        BiliFilterChip(qn == quality, { quality = qn }, { Text(qnLabel(qn)) })
                    }
                }
                if (parts.size > 1) {
                    TextButton(onClick = {
                        selected = if (selected.size == parts.size) emptySet() else parts.mapTo(hashSetOf()) { it.cid }
                    }) { Text(if (selected.size == parts.size) "取消全选" else "全选 ${parts.size} 个分 P") }
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(parts, key = { it.cid }) { part ->
                            BiliSettingsRow(
                                title = "P${part.page} ${part.part}",
                                onClick = { toggle(part.cid) },
                                trailing = { Checkbox(part.cid in selected, onCheckedChange = null) },
                            )
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BiliFilterChip(includeSubtitles, { includeSubtitles = !includeSubtitles }, { Text("同时缓存字幕") })
                    BiliFilterChip(includeDanmaku, { includeDanmaku = !includeDanmaku }, { Text("同时缓存弹幕") })
                }
                Text("加入离线缓存的下载队列。画质以账号和视频实际可用的规格为准。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !submitting && selected.isNotEmpty() && detail.bvid.isNotBlank(), onClick = {
                val destination = AppGraph.repo.settings.value.downloadDir
                val requests = parts.filter { it.cid in selected }.map { part ->
                    DownloadRequest(
                        bvid = detail.bvid,
                        cid = part.cid,
                        title = if (parts.size == 1) detail.title else "${detail.title} · P${part.page} ${part.part}",
                        cover = detail.pic,
                        qn = quality,
                        epId = if (part.cid == currentCid) episodeId else 0,
                        aid = detail.aid,
                        destination = destination,
                        includeDanmaku = includeDanmaku,
                        includeSubtitles = includeSubtitles,
                    )
                }
                submitting = true
                scope.launch {
                    withContext(ioDispatcher) { runCatching { AppGraph.downloads.enqueue(requests) } }
                    .onSuccess { count ->
                        onMessage(if (count == 0) "已在下载队列中" else "已加入 $count 项下载")
                        onDismiss()
                        onOpenQueue()
                    }
                    .onFailure { onMessage("无法保存下载任务，请检查存储空间") }
                    submitting = false
                }
            }) { Text(if (selected.size > 1) "下载 ${selected.size} 项" else "加入队列") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
