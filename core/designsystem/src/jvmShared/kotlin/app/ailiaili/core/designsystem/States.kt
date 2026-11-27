package app.ailiaili.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BiliLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun BiliEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = Icons.Filled.Inbox,
    action: (@Composable () -> Unit)? = null,
) {
    BiliStatusPanel(title, message, icon, modifier, action)
}

@Composable
fun BiliErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BiliStatusPanel("加载失败", message, Icons.Filled.CloudOff, modifier) {
        BiliPrimaryButton(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun BiliStatusPanel(
    title: String,
    message: String?,
    icon: ImageVector?,
    modifier: Modifier,
    action: (@Composable () -> Unit)?,
) {
    Box(modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
        val colors = MaterialTheme.colorScheme
        AliStagePanel(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            contentPadding = PaddingValues(18.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (icon != null) {
                    Box(
                        Modifier.size(44.dp).background(colors.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, modifier = Modifier.size(22.dp), tint = colors.onSurfaceVariant)
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (!message.isNullOrBlank() && message != title) {
                    Text(
                        text = message,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                action?.invoke()
            }
        }
    }
}

@Composable
fun BiliPaginationFooter(
    loading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在加载更多…", style = MaterialTheme.typography.bodySmall)
            }
            error != null -> {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onLoadMore) { Text("重试加载更多") }
            }
            else -> TextButton(onClick = onLoadMore) { Text("加载更多") }
        }
    }
}
