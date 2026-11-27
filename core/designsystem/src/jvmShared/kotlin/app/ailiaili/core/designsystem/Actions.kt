package app.ailiaili.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BiliPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ),
        content = content,
    )
}

/**
 * 播放器/详情页的操作按钮：点赞、投币、收藏、稍后、分享、缓存。
 * 选中态用语义色浅底，未选中用 surfaceContainerHigh；统一圆角、按压反馈和最小高度。
 */
@Composable
fun BiliActionPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val container = if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (active) accent else MaterialTheme.colorScheme.onSurface
    val border = if (active) {
        BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .biliPressScale(interaction),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 4.dp),
                color = content,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 详情页一排等宽的竖向操作：图标在上、计数在下。
 * 激活时图标染成语义色并轻弹一下；首次组合不弹，避免进页面时整排抖动。
 *
 * [onLongClick] 提供时启用两段式长按：系统长按识别（约 0.5 秒）确认意图后，
 * 图标外圈开始扫进度环，扫满才触发；中途松手立即取消，不会误触发。
 */
@Composable
fun BiliActionItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "biliActionTint",
    )
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active && !seen) {
            pop.snapTo(0.72f)
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
        seen = active
    }
    val ring = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val latestLongClick by rememberUpdatedState(onLongClick)
    if (onLongClick != null) {
        // 松手/取消立即熄灭进度环：长按后半程反悔不会触发。
        LaunchedEffect(interaction) {
            interaction.interactions.collect { event ->
                if (event is PressInteraction.Release || event is PressInteraction.Cancel) {
                    holding = false
                }
            }
        }
        LaunchedEffect(holding) {
            if (!holding) {
                ring.snapTo(0f)
                return@LaunchedEffect
            }
            ring.snapTo(0f)
            ring.animateTo(1f, tween(durationMillis = 650, easing = LinearEasing))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            latestLongClick?.invoke()
            holding = false
        }
    }
    Column(
        modifier = modifier
            .biliPressScale(interaction, pressedScale = 0.92f)
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = { holding = true },
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(38.dp)
                .aliMaterial(AliMaterial.Floating, CircleShape, tinted = active),
            contentAlignment = Alignment.Center,
        ) {
            if (onLongClick != null && ring.value > 0f) {
                val stroke = 2.5.dp
                Canvas(Modifier.matchParentSize()) {
                    val inset = stroke.toPx() / 2f
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring.value,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
        }
        Text(
            text = label,
            color = if (active) tint else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
