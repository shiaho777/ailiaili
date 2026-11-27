package app.ailiaili.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DownloadNetwork
import app.ailiaili.core.data.DownloadPhase
import app.ailiaili.core.data.DownloadTask
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.designsystem.AliStageNotice
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.model.cdnThumb
import app.ailiaili.core.model.formatClock
import app.ailiaili.core.model.qnLabel
import coil3.compose.AsyncImage

internal enum class DownloadTaskFilter(val label: String, val emptyMessage: String) {
    All("全部", "队列为空"),
    Active("进行中", "没有进行中的任务"),
    Paused("已暂停", "没有暂停的任务"),
    Failed("失败", "没有失败的任务");

    fun matches(phase: DownloadPhase): Boolean = when (this) {
        All -> true
        Active -> phase.isWorking || phase == DownloadPhase.Queued
        Paused -> phase == DownloadPhase.Paused
        Failed -> phase == DownloadPhase.Failed
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadTaskList(
    tasks: List<DownloadTask>,
    network: DownloadNetwork,
    list: LazyListState,
    filter: DownloadTaskFilter,
    onFilter: (DownloadTaskFilter) -> Unit,
    onOpenCache: () -> Unit,
) {
    var discard by remember { mutableStateOf<DownloadTask?>(null) }
    if (tasks.isEmpty()) {
        AliStageNotice(
            "队列为空", "可从播放器添加视频或分 P。完成后会显示在「已缓存」。", Icons.Filled.Download,
            actionLabel = "查看已缓存", onAction = onOpenCache,
        )
        return
    }
    val visible = tasks.filter { filter.matches(it.phase) }
    LazyColumn(
        modifier = Modifier.smoothWheelScroll(list, LocalWheelSpeed.current),
        state = list,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "batch-actions") {
            AliStagePanel(Modifier.fillMaxWidth(), PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DownloadTaskFilter.entries.forEach { entry ->
                        val count = tasks.count { entry.matches(it.phase) }
                        BiliFilterChip(
                            selected = filter == entry,
                            onClick = { onFilter(entry) },
                            label = { Text("${entry.label} $count") },
                        )
                    }
                }
                if (network != DownloadNetwork.Available && tasks.any { it.phase.canPause || it.phase == DownloadPhase.Pausing }) {
                    Text(
                        if (network == DownloadNetwork.Metered) "等待非计费网络，连接后自动继续" else "等待网络连接，恢复后自动继续",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (tasks.any { it.phase.canPause }) {
                        TextButton(onClick = AppGraph.downloads::pauseAll) { Text("暂停全部") }
                    }
                    if (tasks.any { it.phase == DownloadPhase.Paused }) {
                        TextButton(onClick = AppGraph.downloads::resumeAll) { Text("继续已暂停") }
                    }
                    if (tasks.any { it.phase == DownloadPhase.Failed }) {
                        TextButton(onClick = AppGraph.downloads::retryFailed) { Text("重试失败任务") }
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "empty-filter") {
                AliStagePanel(Modifier.fillMaxWidth()) {
                    Text(filter.emptyMessage, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onFilter(DownloadTaskFilter.All) }) { Text("查看全部任务") }
                }
            }
        }
        items(visible, key = { it.request.key }) { task ->
            DownloadTaskCard(task, network, onDiscard = { discard = task })
        }
    }
    discard?.let { task ->
        AliStageDialog(
            onDismissRequest = { discard = null },
            title = { Text("移除下载任务？") },
            text = { Text("将清除「${task.request.title}」的未完成文件。") },
            confirmButton = {
                TextButton(onClick = {
                    discard = null
                    AppGraph.downloads.discard(task.request.key)
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { discard = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadTaskCard(task: DownloadTask, network: DownloadNetwork, onDiscard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val transferring = task.phase == DownloadPhase.Video || task.phase == DownloadPhase.Audio
    val bytesKnown = task.total > 0 && (transferring || task.done > 0)
    val speed = task.bytesPerSecond.takeIf { transferring && it > 0 }
    val bytesLeft = task.total - task.done
    val remaining = if (speed != null && bytesLeft > 0) formatClock((bytesLeft * 1000.0 / speed).toLong()) else null
    AliStagePanel(Modifier.fillMaxWidth(), PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(80.dp, 48.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Download, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    if (task.request.cover.isNotBlank()) {
                        AsyncImage(cdnThumb(task.request.cover, 192), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.request.title.ifBlank { task.request.bvid }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BiliBadge(text = phaseLabel(task.phase, network), style = BiliBadgeStyle.Soft)
                        BiliBadge(text = qnLabel(task.request.qn), style = BiliBadgeStyle.Outline)
                    }
                }
            }
            task.error?.let { Text(it.removePrefix("下载失败，"), color = colors.error, style = MaterialTheme.typography.bodySmall) }
            if (bytesKnown) {
                LinearProgressIndicator(
                    progress = { (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    trackColor = colors.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else if (transferring || task.phase == DownloadPhase.Finalizing) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), trackColor = colors.surfaceContainerHighest)
            }
            val details = listOfNotNull(
                if (bytesKnown) "${formatBytes(task.done)} / ${formatBytes(task.total)}" else null,
                speed?.let { "${formatBytes(it)}/s" },
                remaining?.let { "当前轨道约 $it" },
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            if (task.phase.canPause || task.phase.canResume) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (task.phase.canPause) {
                        TextButton(onClick = { AppGraph.downloads.pause(task.request.key) }) { Text("暂停") }
                    } else {
                        TextButton(onClick = { AppGraph.downloads.resume(task.request.key) }) {
                            Text(if (task.phase == DownloadPhase.Failed) "重试" else "继续")
                        }
                        TextButton(onClick = onDiscard) { Text("移除任务") }
                    }
                }
            }
        }
    }
}

private fun phaseLabel(phase: DownloadPhase, network: DownloadNetwork): String = when (phase) {
    DownloadPhase.Queued -> when (network) {
        DownloadNetwork.Available -> "等待下载"
        DownloadNetwork.Offline -> "等待连接"
        DownloadNetwork.Metered -> "等待非计费网络"
    }
    DownloadPhase.Resolving -> "正在获取线路"
    DownloadPhase.Video -> "正在下载画面"
    DownloadPhase.Audio -> "正在下载音轨"
    DownloadPhase.Pausing -> "正在暂停"
    DownloadPhase.Paused -> "已暂停"
    DownloadPhase.Finalizing -> "正在保存文件"
    DownloadPhase.Failed -> "下载失败"
    DownloadPhase.Removing -> "正在清理"
}
