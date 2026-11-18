package app.ailiaili.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.model.SubtitleCue
import app.ailiaili.core.model.subtitleCueAt
import app.ailiaili.core.player.PlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 字幕浮层：Compose 层按播放时钟二分查找当前行，Android/桌面同一套渲染，
 * 不依赖 VLC/Media3 的字幕管线（B 站字幕是外挂 JSON，不是内封轨）。
 * 100ms 轮询对字幕足够顺滑，且不逐帧重组整个播放器。
 */
@Composable
internal fun SubtitleOverlay(
    engine: PlayerEngine,
    cues: List<SubtitleCue>,
    delaySec: Double,
    visible: Boolean,
    modifier: Modifier = Modifier,
    fontSp: Int = 17,
    bottomPercent: Int = 4,
) {
    if (!visible || cues.isEmpty()) return
    var current by remember(cues) { mutableStateOf<SubtitleCue?>(null) }
    LaunchedEffect(engine, cues, delaySec) {
        var lastPos = -1L
        while (isActive) {
            val pos = runCatching { engine.currentPosition }.getOrDefault(0L)
            // 位置没动（暂停/卡顿）就不重复二分；播放中 100ms 足够顺滑，
            // 暂停时降到 500ms 只等恢复，长暂停几乎零开销。
            if (pos != lastPos) {
                lastPos = pos
                val hit = subtitleCueAt(cues, pos / 1000.0, delaySec)
                if (hit != current) current = hit
            }
            val playing = runCatching { engine.isPlaying }.getOrDefault(false)
            delay(if (playing) 100 else 500)
        }
    }
    val size = fontSp.coerceIn(12, 36)
    val bottom = bottomPercent.coerceIn(0, 40)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val bottomPad = maxHeight * (bottom / 100f)
        Box(Modifier.fillMaxSize().padding(bottom = bottomPad), contentAlignment = Alignment.BottomCenter) {
            current?.let { cue ->
                Text(
                    cue.text,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = size.sp,
                    lineHeight = (size * 1.4f).sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
