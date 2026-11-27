package app.ailiaili.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 统一筛选胶囊：视频/直播/番剧、热门/时间、清晰度、线路、分P、关注用户筛选。
 * 选中态用主题色浅底 + 细边框，未选中用 surfaceContainerHigh。
 */
@Composable
fun BiliFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.outlineVariant.copy(alpha = .62f),
            selectedBorderColor = colors.primary.copy(alpha = 0.36f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/** 历史记录等轻量可点击标签。 */
@Composable
fun BiliAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = {
            androidx.compose.material3.Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 小圆角图标占位，保证 chip 前缀图标尺寸一致。 */
@Composable
fun BiliChipIcon(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.size(18.dp)) { content() }
}

/** 屏蔽规则胶囊：点一下（或点右侧 ×）即可撤销。 */
@Composable
fun BiliRuleChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.InputChip(
        selected = false,
        onClick = onRemove,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        label = {
            androidx.compose.material3.Text(
                label,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        trailingIcon = {
            androidx.compose.material3.Icon(
                Icons.Filled.Close,
                contentDescription = "移除屏蔽",
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
