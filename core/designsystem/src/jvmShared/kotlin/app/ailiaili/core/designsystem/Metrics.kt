package app.ailiaili.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数据展示的统一语言。
 *
 * 所有“图标 + 数值 + 标签”的统计都走 [MetricChip] / [MetricFlow]；
 * 所有“数值 + 标签”的大数字（空间页粉丝、我的页动态数）走 [MetricStat]；
 * 视频卡底部这种轻量元信息走 [MetaRow]。
 */
enum class MetricTone {
    Neutral,
    Accent,
    Play,
    Danmaku,
    Like,
    Coin,
    Favorite,
    Online,
    Date,
}

data class MetricSpec(
    val icon: ImageVector,
    val value: String,
    val label: String = "",
    val tone: MetricTone = MetricTone.Neutral,
)

enum class MetricChipSize { Compact, Regular }

@Composable
fun metricToneColor(tone: MetricTone): Color = when (tone) {
    MetricTone.Neutral, MetricTone.Date -> MaterialTheme.colorScheme.onSurfaceVariant
    MetricTone.Accent, MetricTone.Play -> MaterialTheme.colorScheme.primary
    MetricTone.Danmaku -> Color(0xFF4FC3F7)
    MetricTone.Like -> Color(0xFFFF6B81)
    MetricTone.Coin -> Color(0xFFFFB74D)
    MetricTone.Favorite -> Color(0xFF4DD0E1)
    MetricTone.Online -> Color(0xFF81C784)
}

@Composable
fun MetricChip(
    spec: MetricSpec,
    modifier: Modifier = Modifier,
    size: MetricChipSize = MetricChipSize.Regular,
    onClick: (() -> Unit)? = null,
) {
    val compact = size == MetricChipSize.Compact
    val interaction = if (onClick != null) remember { MutableInteractionSource() } else null
    val clickable = if (onClick == null || interaction == null) {
        Modifier
    } else {
        Modifier
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .biliPressScale(interaction)
    }
    Surface(
        modifier = modifier.then(clickable),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
                tint = metricToneColor(spec.tone),
            )
            Text(
                text = spec.value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (spec.label.isNotBlank()) {
                Text(
                    text = spec.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (compact) 10.sp else 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricFlow(
    metrics: List<MetricSpec>,
    modifier: Modifier = Modifier,
    size: MetricChipSize = MetricChipSize.Regular,
    onMetricClick: ((MetricSpec) -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (size == MetricChipSize.Compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (size == MetricChipSize.Compact) 6.dp else 8.dp),
    ) {
        metrics.forEach { spec ->
            MetricChip(
                spec = spec,
                size = size,
                onClick = if (onMetricClick == null) null else ({ onMetricClick(spec) }),
            )
        }
    }
}

@Composable
fun MetricStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interaction = if (onClick != null) remember { MutableInteractionSource() } else null
    val clickable = if (onClick == null || interaction == null) {
        Modifier
    } else {
        Modifier
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .biliPressScale(interaction)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(clickable)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

data class MetaSpec(
    val icon: ImageVector,
    val text: String,
    val tone: MetricTone = MetricTone.Neutral,
)

@Composable
fun MetaRow(
    items: List<MetaSpec>,
    modifier: Modifier = Modifier,
    maxItems: Int = 2,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.take(maxItems).forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = metricToneColor(item.tone),
                )
                Text(
                    text = item.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun BiliSurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColorFor(color),
        border = border,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun BiliSectionHeader(
    title: String,
    action: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!action.isNullOrBlank() && onAction != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = action,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAction()
                    }
                    .biliPressScale(interaction)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
