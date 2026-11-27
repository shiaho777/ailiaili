package app.ailiaili.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.ResumePlayback
import app.ailiaili.core.designsystem.AliColors
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.biliPressScale
import app.ailiaili.core.model.cdnThumb
import app.ailiaili.core.model.formatClock
import coil3.compose.AsyncImage

@Composable
internal fun HomeResumeCard(playback: ResumePlayback, onResume: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val clock = formatClock(playback.positionMs)
    val status = listOf("继续观看", clock, playback.partLabel).filter(String::isNotBlank).joinToString(" · ")
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < (510 * fontScale).dp
        Row(
            Modifier.fillMaxWidth().aliMaterial(AliMaterial.Panel, shape, tinted = focused).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).biliPressScale(interaction, .99f).clip(shape)
                    .background(if (hovered) colors.onSurface.copy(alpha = .045f) else Color.Transparent)
                    .clickable(interaction, indication = null, role = Role.Button, onClickLabel = "从 $clock 继续观看", onClick = onResume)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(if (compact) 78.dp else 88.dp, if (compact) 44.dp else 50.dp)
                        .clip(MaterialTheme.shapes.small).background(colors.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playback.video.cover.isNotBlank()) {
                        AsyncImage(
                            cdnThumb(playback.video.cover, 240), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                        )
                    }
                    if (compact) {
                        Box(
                            Modifier.size(26.dp).background(Color.Black.copy(alpha = .65f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), tint = AliColors.StageInk)
                        }
                    } else if (playback.video.cover.isBlank()) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        status, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        playback.video.title, color = colors.onSurface, style = MaterialTheme.typography.titleSmall,
                        maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { (playback.positionMs.toDouble() / playback.durationMs).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = colors.primary,
                        trackColor = colors.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
                if (!compact) {
                    Box(
                        Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(19.dp), tint = colors.onPrimaryContainer)
                    }
                }
            }
            AliStageIconButton(Icons.Filled.Close, "移出继续观看", onDismiss)
        }
    }
}
