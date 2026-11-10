package app.ailiaili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.network.JSONObject
import app.ailiaili.core.network.SharedHttp
import app.ailiaili.core.network.get
import app.ailiaili.core.model.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val CURRENT_VERSION = "1.3.0"

private data class UpdateNote(val tag: String, val page: String)

@Composable
fun UpdatePrompt(enabled: Boolean, skippedVersion: String) {
    var note by remember { mutableStateOf<UpdateNote?>(null) }
    LaunchedEffect(enabled, skippedVersion) {
        if (!enabled) return@LaunchedEffect
        delay(6_000)
        note = newerRelease(skippedVersion)
    }
    val message = note ?: return
    AlertDialog(
        onDismissRequest = { note = null },
        title = { Text("发现新版本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BiliBadge(
                        text = "当前 $CURRENT_VERSION",
                        tone = BiliBadgeTone.Neutral,
                        style = BiliBadgeStyle.Soft,
                    )
                    Text("→")
                    BiliBadge(
                        text = message.tag,
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
                Text("新版本已经发布，可以前往下载页查看更新内容。")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (message.page.isNotBlank()) openUrl(message.page)
                note = null
            }) { Text("前往下载") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { note = null }) { Text("以后再说") }
                TextButton(
                    onClick = {
                        AppGraph.repo.updateSettings { it.copy(skippedVersion = message.tag) }
                        note = null
                    },
                ) { Text("跳过此版本") }
            }
        },
    )
}

private suspend fun newerRelease(skipped: String): UpdateNote? = withContext(ioDispatcher) {
    runCatching {
        val json = JSONObject(
            SharedHttp.api.get(
                "https://api.github.com/repos/shiaho777/ailiaili/releases/latest",
                mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "ailiaili",
                ),
            ).bodyText,
        )
        val tag = json.optString("tag_name").removePrefix("v")
        val page = json.optString("html_url")
        if (tag.isBlank() || tag == CURRENT_VERSION) return@runCatching null
        if (tag.equals(skipped.removePrefix("v"), ignoreCase = true)) return@runCatching null
        UpdateNote(tag, page)
    }.getOrNull()
}
