package app.ailiaili.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val accent = MaterialTheme.colorScheme.primary
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = accent.copy(alpha = 0.16f),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = accent.copy(alpha = 0.35f),
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
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 小圆角图标占位，保证 chip 前缀图标尺寸一致。 */
@Composable
fun BiliChipIcon(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.size(18.dp)) { content() }
}
