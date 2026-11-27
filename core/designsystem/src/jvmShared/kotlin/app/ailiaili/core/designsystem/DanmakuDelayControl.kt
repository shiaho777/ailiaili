package app.ailiaili.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DanmakuDelayControl(delayMs: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val label = when {
        delayMs > 0 -> "延后 ${delayMs / 1000.0} 秒"
        delayMs < 0 -> "提前 ${-delayMs / 1000.0} 秒"
        else -> "与视频同步"
    }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
                onClick = { onChange((delayMs - 500).coerceAtLeast(-30_000)) },
                enabled = delayMs > -30_000,
            ) { Text("提前 0.5 秒") }
            TextButton(onClick = { onChange(0) }, enabled = delayMs != 0) { Text("重置") }
            TextButton(
                onClick = { onChange((delayMs + 500).coerceAtMost(30_000)) },
                enabled = delayMs < 30_000,
            ) { Text("延后 0.5 秒") }
        }
    }
}
