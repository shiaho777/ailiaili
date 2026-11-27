package app.ailiaili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliSearchEntry
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.UrlParser

@Composable
internal fun HomeDiscovery(
    onSearch: () -> Unit,
    onOpenLink: (UrlHit) -> Unit,
    onHistory: () -> Unit,
    onOffline: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val searchHint = when {
            maxWidth < (318 * fontScale).dp -> "搜索"
            maxWidth < (420 * fontScale).dp -> "搜索视频或链接"
            else -> "搜索视频、直播、番剧或 UP 主"
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BiliSearchEntry(searchHint, onSearch, Modifier.weight(1f))
            BiliHeaderAction(Icons.Filled.Link, "粘贴链接", onClick = {
                val hit = UrlParser.parse(clipboard.getText()?.text.orEmpty().trim())
                if (hit is UrlHit.Keyword) {
                    QuickActions.showToast("复制 B 站视频、直播间或 UP 主链接后再试")
                } else {
                    onOpenLink(hit)
                }
            })
            BiliHeaderAction(Icons.Filled.History, "历史", onHistory)
            BiliHeaderAction(Icons.Filled.Download, "离线", onOffline)
        }
    }
}
