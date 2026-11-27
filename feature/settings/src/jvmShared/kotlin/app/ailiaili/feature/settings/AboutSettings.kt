package app.ailiaili.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.APP_ISSUES_URL
import app.ailiaili.core.data.APP_PROJECT_URL
import app.ailiaili.core.data.APP_RELEASES_URL
import app.ailiaili.core.data.AppRelease
import app.ailiaili.core.data.Diagnostics
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.designsystem.BiliSwitchRow
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.appIconPainter
import app.ailiaili.core.model.APP_VERSION
import app.ailiaili.core.model.isNewerVersion

@Composable
internal fun AboutSettings(
    autoCheckUpdate: Boolean,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    checkingUpdate: Boolean,
    updateResult: Result<AppRelease?>?,
    onCheckUpdate: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenDisclaimer: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val newerRelease = updateResult?.getOrNull()?.takeIf { isNewerVersion(it.tag, APP_VERSION) }
    val updateStatus = if (checkingUpdate) {
        "正在检查…"
    } else {
        updateResult?.fold(
            onSuccess = { release ->
                when {
                    release == null -> "GitHub 暂无正式版本"
                    newerRelease != null -> "发现新版本 v${newerRelease.tag}"
                    else -> "当前版本无需更新（最新正式版 v${release.tag}）"
                }
            },
            onFailure = { "检查失败：${it.message ?: "网络不可用"}" },
        ) ?: "查询 GitHub 上发布的正式版本"
    }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(appIconPainter(), contentDescription = null, modifier = Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("ailiaili", style = MaterialTheme.typography.titleLarge)
                Text(
                    "版本 $APP_VERSION",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "非官方哔哩哔哩客户端",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AliStageIconButton(Icons.Filled.ContentCopy, "复制版本号", onClick = {
                clipboard.setText(AnnotatedString(APP_VERSION))
                QuickActions.showToast("版本号已复制")
            })
        }
    }

    BiliSectionTitle("应用更新", Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        BiliSettingsRow(
            title = "检查更新",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            supporting = updateStatus,
            icon = Icons.Filled.Refresh,
            tone = MetricTone.Danmaku,
            onClick = onCheckUpdate.takeUnless { checkingUpdate },
        )
        BiliSettingsRow(
            title = if (newerRelease == null) "发布记录" else "下载新版本",
            supporting = if (newerRelease == null) "在 GitHub 查看发布说明和安装包" else "在 GitHub 查看 v${newerRelease.tag}",
            icon = Icons.Filled.Download,
            onClick = { openUrl(newerRelease?.page ?: APP_RELEASES_URL) },
        )
        BiliSwitchRow(
            title = "启动时检查更新",
            checked = autoCheckUpdate,
            onCheckedChange = onAutoCheckUpdateChange,
            supporting = "应用启动后自动查询正式版本",
            icon = Icons.Filled.SystemUpdate,
            tone = MetricTone.Online,
        )
    }

    BiliSectionTitle("项目与反馈", Modifier.padding(horizontal = 20.dp))
    SettingsCard {
        BiliSettingsRow(
            title = "反馈问题",
            supporting = "在 GitHub Issues 搜索或提交反馈",
            icon = Icons.Filled.BugReport,
            onClick = { openUrl(APP_ISSUES_URL) },
        )
        BiliSettingsRow(
            title = "复制诊断信息",
            supporting = "可用于反馈播放或网络问题，发送前可检查内容",
            icon = Icons.Filled.Info,
            tone = MetricTone.Neutral,
            onClick = {
                clipboard.setText(AnnotatedString(Diagnostics.build(APP_VERSION)))
                QuickActions.showToast("诊断信息已复制到剪贴板")
            },
        )
        BiliSettingsRow(
            title = "项目主页",
            supporting = "在 GitHub 查看源码和贡献指南",
            icon = Icons.Filled.Code,
            tone = MetricTone.Neutral,
            onClick = { openUrl(APP_PROJECT_URL) },
        )
        BiliSettingsRow(
            title = "开源许可",
            supporting = "GPL-3.0",
            icon = Icons.Filled.Description,
            tone = MetricTone.Neutral,
            onClick = { openUrl("$APP_PROJECT_URL/blob/main/LICENSE") },
        )
        BiliSettingsRow(
            title = "MiSans 字体",
            supporting = "由小米提供 · 查看字体许可",
            icon = Icons.Filled.FontDownload,
            tone = MetricTone.Neutral,
            onClick = { openUrl("https://hyperos.mi.com/font/download/") },
        )
        BiliSettingsRow(
            title = "原作者 ABing",
            supporting = "打开哔哩哔哩主页",
            icon = Icons.Filled.Person,
            tone = MetricTone.Like,
            onClick = onOpenAuthor,
        )
        BiliSettingsRow(
            title = "非官方声明",
            icon = Icons.Filled.Info,
            tone = MetricTone.Coin,
            onClick = onOpenDisclaimer,
        )
    }
}
