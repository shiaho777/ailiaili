package app.ailiaili.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun AliStageHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = if (colors.background.luminance() < .36f) colors.surface else colors.background
    Column(modifier.fillMaxWidth().background(background).statusBarsPadding(), content = content)
}

@Composable
fun AliStagePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AliStageHeader(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) AliStageIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
            else if (LocalShellDockClearance.current != null) Image(appIconPainter(), null, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodySmall,
                        content = subtitle,
                    )
                }
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun AliStageTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    floating: Boolean = false,
) {
    if (labels.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedIndex = selected.coerceIn(labels.indices)
    val position = animateFloatAsState(
        targetValue = if (rtl) labels.lastIndex - selectedIndex.toFloat() else selectedIndex.toFloat(),
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "stage-tab-position",
    )
    val selection = if (onMedia) lerp(AliColors.StagePanel, aliPlaybackAccent(), .20f) else colors.primaryContainer
    val selectedInk = if (onMedia) AliColors.StageInk else colors.onPrimaryContainer
    val labelInk = if (onMedia) AliColors.StageMuted else colors.onSurfaceVariant
    val material = when {
        onMedia -> AliMaterial.Playback
        floating -> AliMaterial.Floating
        else -> AliMaterial.Inset
    }
    Row(
        modifier
            .padding(vertical = 3.dp)
            .then(
                if (floating || onMedia) Modifier.aliMaterial(
                    material, MaterialTheme.shapes.medium,
                ) else Modifier,
            )
            .padding(horizontal = if (floating || onMedia) 4.dp else 0.dp, vertical = 2.dp)
            .drawWithCache {
                val segment = size.width / labels.size
                val inset = 3.dp.toPx()
                val highlightSize = Size((segment - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f))
                val radius = CornerRadius(14.dp.toPx())
                onDrawBehind {
                    if (selected in labels.indices) {
                        val x = position.value.coerceIn(0f, labels.lastIndex.toFloat()) * segment + inset
                        drawRoundRect(selection, Offset(x, inset), highlightSize, radius)
                    }
                }
            }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(MaterialTheme.shapes.small)
                    .selectable(active, interaction, LocalIndication.current, role = Role.Tab) {
                        if (!active) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    }
                    .padding(horizontal = 3.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    modifier = Modifier.biliPressScale(interaction, .96f),
                    color = if (active) selectedInk else labelInk,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AliStageIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    selected: Boolean? = null,
    overVideo: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = selected == true
    val playbackAccent = if ((overVideo && highlighted) || (onMedia && emphasized)) aliPlaybackAccent() else Color.Unspecified
    val foreground = when {
        overVideo && highlighted -> playbackAccent
        overVideo -> AliColors.StageInk
        highlighted -> colors.onPrimaryContainer
        emphasized && onMedia -> AliColors.Ink
        onMedia -> AliColors.StageInk
        emphasized -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val shape = if (highlighted || emphasized) MaterialTheme.shapes.small else CircleShape
    Box(
        modifier.size(44.dp)
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val feedback = animateFloatAsState(
            targetValue = when {
                focused -> .12f
                hovered -> .06f
                else -> 0f
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "stage-button-highlight",
        )
        Box(
            Modifier.size(38.dp).biliPressScale(interaction, .95f)
                .then(
                    when {
                        overVideo -> Modifier.clip(shape)
                        highlighted -> Modifier.clip(shape).background(colors.primaryContainer)
                        emphasized && onMedia -> Modifier.clip(shape).background(playbackAccent)
                        emphasized -> Modifier.clip(shape).background(colors.primaryContainer)
                        else -> Modifier.clip(shape)
                    },
                )
                .drawWithCache {
                    onDrawBehind { drawCircle(foreground.copy(alpha = feedback.value)) }
                }
                .then(if (focused) Modifier.border(1.dp, foreground.copy(alpha = .8f), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = if (enabled) foreground else foreground.copy(alpha = .38f))
        }
    }
}

@Composable
fun AliStagePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier.aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.large).padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun AliStageBody(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 960.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().aliAmbient(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}

@Composable
fun AliStageNotice(
    title: String,
    message: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.TopCenter) {
        AliStagePanel(Modifier.widthIn(max = 540.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!message.isNullOrBlank()) {
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                BiliPrimaryButton(onClick = onAction, modifier = Modifier.align(Alignment.End).padding(top = 14.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

fun Modifier.aliVideoScrim(top: Boolean): Modifier = drawWithCache {
    val dark = AliColors.StageMedia.copy(alpha = .84f)
    val soft = AliColors.StageMedia.copy(alpha = if (top) .60f else .46f)
    val brush = if (top) Brush.verticalGradient(0f to dark, .58f to soft, 1f to Color.Transparent)
    else Brush.verticalGradient(0f to Color.Transparent, .46f to soft, 1f to dark)
    onDrawBehind { drawRect(brush) }
}

@Composable
fun AliStageSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buffered: Float? = null,
    onMedia: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val inactive = if (onMedia) AliColors.StageMuted.copy(alpha = .32f) else colors.outlineVariant
    val cachedTrack = if (onMedia) AliColors.StageInk.copy(alpha = .28f) else colors.outline.copy(alpha = .68f)
    val accent = when {
        onMedia && enabled -> aliPlaybackAccent()
        onMedia -> AliColors.StageMuted
        enabled -> colors.primary
        else -> colors.outline
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        enabled = enabled,
        thumb = { Spacer(Modifier.size(20.dp, 10.dp)) },
        track = {
            Box(Modifier.fillMaxWidth().height(14.dp).drawWithCache {
                val thickness = 3.dp.toPx()
                onDrawBehind {
                    val start = Offset(0f, center.y)
                    val fraction = value.coerceIn(0f, 1f)
                    val head = Offset(fraction * size.width, center.y)
                    drawLine(inactive, start, Offset(size.width, center.y), thickness, StrokeCap.Round)
                    val cached = buffered?.coerceIn(0f, 1f) ?: 0f
                    if (cached > fraction) {
                        drawLine(cachedTrack, start, Offset(cached * size.width, center.y), thickness, StrokeCap.Round)
                    }
                    if (fraction > 0f) drawLine(accent, start, head, thickness, StrokeCap.Round)
                    drawCircle(accent, (if (enabled) 4.dp else 3.dp).toPx(), head)
                }
            })
        },
    )
}
