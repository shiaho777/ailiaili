package app.ailiaili.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.formatCount
import coil3.compose.AsyncImage

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: () -> VM,
): VM {
    return viewModel(key = key) { create() }
}

@Composable
fun LoadBox(
    loading: Boolean,
    error: String?,
    empty: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when {
            loading && empty -> BiliLoadingState(
                modifier = Modifier.align(Alignment.Center),
                label = "加载中…",
            )
            error != null && empty -> BiliErrorState(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
            empty && !loading -> BiliEmptyState(
                title = emptyText,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> content()
        }
        if (loading && !empty) {
            CircularProgressIndicator(Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
fun VideoGrid(
    items: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: (() -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    if (onLoadMore != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last ->
                    if (items.isNotEmpty() && last >= items.size - 4) onLoadMore()
                }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(LocalWindowForm.current.gridMin),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.stableKey + it.title }) { item ->
            VideoCard(item, onClick, onAuthor, Modifier.animateItem())
        }
    }
}

@Composable
fun VideoCard(
    item: VideoItem,
    onClick: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewCover by remember(item.cover) { mutableStateOf(false) }
    var menuOpen by remember(item.stableKey) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .biliPressScale(interaction, pressedScale = 0.98f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = { onClick(item) },
                    onLongClick = { if (item.cover.isNotBlank()) previewCover = true },
                )
                .secondaryClick {
                    menuOpen = true
                },
        ) {
            VideoCover(item)
            Column(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.reason.isNotBlank()) {
                    BiliBadge(
                        text = item.reason,
                        tone = BiliBadgeTone.Warning,
                        style = BiliBadgeStyle.Soft,
                    )
                }
                if (item.author.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(enabled = item.ownerMid > 0) { onAuthor(item) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (item.ownerFace.isNotBlank()) {
                            AsyncImage(
                                model = item.ownerFace,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.authorFollowed) {
                            BiliBadge(
                                text = "已关注",
                                tone = BiliBadgeTone.Accent,
                                style = BiliBadgeStyle.Solid,
                            )
                        }
                        Text(
                            item.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (item.cover.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("预览封面") },
                        onClick = {
                            menuOpen = false
                            previewCover = true
                        },
                    )
                }
                if (item.author.isNotBlank() || item.ownerMid > 0) {
                    DropdownMenuItem(
                        text = { Text("屏蔽此 UP 主") },
                        onClick = {
                            menuOpen = false
                            ContentFilter.blockUp(item.ownerMid, item.author)
                        },
                    )
                }
                if (item.title.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("屏蔽此标题") },
                        onClick = {
                            menuOpen = false
                            ContentFilter.blockTitle(item.title)
                        },
                    )
                }
            }
        }
    }
    if (previewCover) {
        AvatarPreviewDialog(
            url = item.cover,
            name = item.title,
            onDismiss = { previewCover = false },
            onSaved = {},
        )
    }
}

private val CoverScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.72f),
)

@Composable
private fun VideoCover(item: VideoItem) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(
            model = item.cover,
            contentDescription = item.title,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(CoverScrim),
        )
        if (item.isLive) {
            BiliBadge(
                text = "直播中",
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                tone = BiliBadgeTone.Accent,
                style = BiliBadgeStyle.Solid,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (item.isLive) {
                formatCount(item.viewCount).takeIf { it.isNotBlank() }?.let {
                    CoverStat(Icons.Filled.Person, "$it 人气")
                }
            } else if (item.statsReady) {
                formatCount(item.viewCount).takeIf { it.isNotBlank() }?.let {
                    CoverStat(Icons.Filled.PlayArrow, it)
                }
                formatCount(item.danmakuCount).takeIf { it.isNotBlank() }?.let {
                    CoverStat(Icons.AutoMirrored.Filled.Comment, it)
                }
            }
            Spacer(Modifier.weight(1f))
            val duration = item.durationText
            if (!item.isLive && duration.isNotBlank() && duration != "--:--") {
                Text(
                    duration,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CoverStat(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = Color.White,
        )
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}


