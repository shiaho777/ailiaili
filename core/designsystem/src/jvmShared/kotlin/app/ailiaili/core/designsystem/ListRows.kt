package app.ailiaili.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * 列表页（历史、稍后再看、收藏夹、缓存）共用的一行：左边 16:10 封面 + 时长角标，
 * 右边两行标题 + 元信息。编辑态把 [leading] 换成勾选框，选中时整行淡强调色。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListRow(
    title: String,
    cover: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String = "",
    selected: Boolean = false,
    coverWidth: Dp = 136.dp,
    /** 没有封面时是否保留占位。纯文字列表（如番剧选集）可以关掉。 */
    coverPlaceholder: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    author: String = "",
    ownerMid: Long = 0,
    leading: (@Composable () -> Unit)? = null,
    meta: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    var menuOpen by remember(title, author) { mutableStateOf(false) }
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        label = "videoListRowBg",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .biliPressScale(interaction, pressedScale = 0.985f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .secondaryClick { menuOpen = true }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        if (cover.isNotBlank() || coverPlaceholder) {
            Box(
                Modifier
                    .width(coverWidth)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (cover.isNotBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (badge.isNotBlank()) {
                    BiliBadge(
                        text = badge,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                        tone = BiliBadgeTone.Dark,
                        style = BiliBadgeStyle.Solid,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title.ifBlank { "未命名" },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            meta?.invoke()
        }
        trailing?.invoke(this)
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (author.isNotBlank() || ownerMid > 0) {
                DropdownMenuItem(
                    text = { Text("屏蔽此 UP 主") },
                    onClick = {
                        menuOpen = false
                        ContentFilter.blockUp(ownerMid, author)
                    },
                )
            }
            if (title.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("屏蔽此标题") },
                    onClick = {
                        menuOpen = false
                        ContentFilter.blockTitle(title)
                    },
                )
            }
        }
    }
}
