package app.ailiaili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.ailiaili.core.designsystem.AliStageDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.AppRelease
import app.ailiaili.core.data.latestAppRelease
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.model.isNewerVersion
import kotlinx.coroutines.delay

/** 版本号由 Gradle 从 gradle.properties 生成，与安装包版本永远一致。 */
private const val CURRENT_VERSION = app.ailiaili.core.model.APP_VERSION

@Composable
fun UpdatePrompt(enabled: Boolean, skippedVersion: String) {
    var note by remember { mutableStateOf<AppRelease?>(null) }
    LaunchedEffect(enabled, skippedVersion) {
        if (!enabled) return@LaunchedEffect
        delay(6_000)
        note = latestAppRelease().getOrNull()?.takeIf {
            isNewerVersion(it.tag, CURRENT_VERSION) && !it.tag.equals(skippedVersion.removePrefix("v"), ignoreCase = true)
        }
    }
    val message = note ?: return
    AliStageDialog(
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
                        style = BiliBadgeStyle.Solid,
                    )
                }
                if (message.notes.isNotBlank()) ReleaseNotes(message.notes)
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

private val inlineReleaseFormat = Regex("""\[([^\]]+)]\((https://[^)\s]+)\)|\*\*([^*]+)\*\*""")

@Composable
private fun ReleaseNotes(notes: String) {
    val color = MaterialTheme.colorScheme.primary
    val lines = remember(notes) { notes.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(120).toList() }
    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(lines) { line ->
            val heading = line.startsWith("#")
            val text = when {
                heading -> line.trimStart('#').trimStart()
                line.startsWith("- ") || line.startsWith("* ") -> "• " + line.drop(2)
                else -> line
            }
            Text(
                buildAnnotatedString {
                    var cursor = 0
                    for (match in inlineReleaseFormat.findAll(text)) {
                        append(text.substring(cursor, match.range.first))
                        val link = match.groups[2]?.value
                        if (link != null) {
                            withLink(LinkAnnotation.Url(link, TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)))) {
                                append(match.groupValues[1])
                            }
                        } else {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[3]) }
                        }
                        cursor = match.range.last + 1
                    }
                    append(text.substring(cursor))
                },
                style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
