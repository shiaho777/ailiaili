package app.ailiaili.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.formatClock
import app.ailiaili.core.model.qnLabel
import app.ailiaili.core.player.PlayerEngine

@Composable
internal fun PlaybackInfoDialog(
    info: PlayInfo?,
    local: CacheEntry?,
    live: Boolean,
    desktop: Boolean,
    engine: PlayerEngine,
    speed: Float,
    onDismiss: () -> Unit,
) {
    val rows = buildList {
        add("来源" to if (local != null) "离线缓存" else if (live) "在线直播" else "在线视频")
        add("播放引擎" to if (desktop) "VLC" else "Media3")
        (local?.qn ?: info?.actualQn)?.takeIf { it > 0 }?.let { add("实际画质" to qnLabel(it)) }
        if (info != null && info.width > 0 && info.height > 0) add("分辨率" to "${info.width} × ${info.height}")
        info?.videoCodecs?.takeIf { it.isNotBlank() }?.let { add("视频编码" to it) }
        info?.audioCodecs?.takeIf { it.isNotBlank() }?.let { add("音频编码" to it) }
        if (local != null) add("文件格式" to if (local.audioPath != null) "视频与音轨分开" else "单文件")
        if (!live && engine.duration > 0) add("时长" to formatClock(engine.duration))
        if (!live) add("倍速" to "${speed}x")
    }
    AliStageDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { (label, value) ->
                    Text("$label · $value", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
