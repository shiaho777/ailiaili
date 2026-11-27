package app.ailiaili.feature.dynamic

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.AvatarPreviewDialog
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.ContentFilter
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.designsystem.biliPressScale
import app.ailiaili.core.model.DynamicEntry
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.model.formatRelativeTime
import coil3.compose.AsyncImage

/**
 * 综合动态卡片：作者行 + 附言 + 内容体（视频封面 / 图文九宫格）。
 * 视频与图文共用同一容器语言，观感是一套产品而不是两种页面拼起来的。
 */
@Composable
internal fun DynamicEntryCard(
    entry: DynamicEntry,
    onVideo: (VideoItem) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onPost: (DynamicEntry) -> Unit,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    var preview by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }
    Surface(
        modifier = modifier.fillMaxWidth().biliPressScale(interaction, pressedScale = 0.98f)
            .aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = {
                        val video = entry.video
                        if (video != null) onVideo(video) else onPost(entry)
                    },
                    onLongClick = {
                        val video = entry.video
                        val pics = entry.post?.pics.orEmpty()
                        if (video != null && video.cover.isNotBlank()) {
                            preview = Triple(listOf(video.cover), 0, video.title)
                        } else if (pics.isNotEmpty()) {
                            preview = Triple(pics, 0, entry.authorName)
                        }
                    },
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showAuthor) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.authorFace.isNotBlank()) {
                        AsyncImage(
                            model = app.ailiaili.core.model.cdnThumb(entry.authorFace, 80),
                            contentDescription = entry.authorName,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(enabled = entry.authorMid > 0) {
                                    onAuthor(VideoItem(ownerMid = entry.authorMid, author = entry.authorName))
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.authorName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.pubTs > 0) {
                            Text(
                                formatRelativeTime(entry.pubTs),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!entry.isVideo) {
                        BiliBadge("图文", tone = BiliBadgeTone.Neutral, style = BiliBadgeStyle.Outline)
                    }
                }
            }
            if (entry.desc.isNotBlank()) {
                Text(
                    entry.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val video = entry.video
            if (video != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (entry.forwarded) 16f / 9f else 16f / 10f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    AsyncImage(
                        model = app.ailiaili.core.model.cdnThumb(video.cover, 640),
                        contentDescription = video.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = if (entry.forwarded) ContentScale.Fit else ContentScale.Crop,
                    )
                    if (video.durationText.isNotBlank()) {
                        BiliBadge(
                            text = video.durationText,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                            tone = BiliBadgeTone.Dark,
                            style = BiliBadgeStyle.Solid,
                        )
                    }
                }
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                MetaRow(
                    items = listOfNotNull(
                        formatCount(video.viewCount).takeIf { it.isNotBlank() }?.let {
                            MetaSpec(Icons.Filled.PlayArrow, it, MetricTone.Play)
                        },
                        formatCount(video.danmakuCount).takeIf { it.isNotBlank() }?.let {
                            MetaSpec(Icons.AutoMirrored.Filled.Comment, it, MetricTone.Danmaku)
                        },
                    ),
                    maxItems = 2,
                )
            }
            val post = entry.post
            if (post != null) {
                if (post.title.isNotBlank()) {
                    Text(
                        post.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.text.isNotBlank() && post.text != entry.desc) {
                    Text(
                        post.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (post.pics.isNotEmpty()) {
                    PostPicGrid(
                        pics = post.pics,
                        onPreview = { index -> preview = Triple(post.pics, index, entry.authorName) },
                    )
                }
                MetaRow(
                    items = listOfNotNull(
                        formatCount(post.likeCount).takeIf { it.isNotBlank() }?.let {
                            MetaSpec(Icons.Filled.ThumbUp, it, MetricTone.Like)
                        },
                        formatCount(post.commentCount).takeIf { it.isNotBlank() }?.let {
                            MetaSpec(Icons.AutoMirrored.Filled.Comment, it, MetricTone.Danmaku)
                        },
                    ),
                    maxItems = 2,
                )
            }
        }
    }
    preview?.let { (urls, index, name) ->
        AvatarPreviewDialog(
            url = urls.getOrElse(index) { urls.first() },
            name = name,
            onDismiss = { preview = null },
            onSaved = { QuickActions.dismissToast(it) },
            urls = urls,
        )
    }
}

/** 图文九宫格：1 张大图、2/4 张两列、其余三列，统一 1:1 裁切。 */
@Composable
internal fun PostPicGrid(
    pics: List<String>,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = when (pics.size) {
        1 -> 1
        2, 4 -> 2
        else -> 3
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pics.chunked(columns).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEachIndexed { col, url ->
                    val index = row * columns + col
                    AsyncImage(
                        // 格子缩略图 ≤400px 足够；点开的预览对话框仍加载原图。
                        model = app.ailiaili.core.model.cdnThumb(url, if (pics.size == 1) 800 else 400),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(if (pics.size == 1) 16f / 10f else 1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onPreview(index) },
                        contentScale = ContentScale.Crop,
                    )
                }
                // 最后一行不满时补占位，保持格子大小一致。
                repeat(columns - chunk.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 动态流的屏蔽过滤：视频按既有规则；图文按作者名与正文关键词。 */
internal fun entryAllowed(entry: DynamicEntry): Boolean {
    val video = entry.video
    if (video != null) return ContentFilter.allows(video)
    val fake = VideoItem(
        title = (entry.post?.title.orEmpty() + " " + entry.post?.text.orEmpty()).take(200),
        author = entry.authorName,
        ownerMid = entry.authorMid,
    )
    return ContentFilter.allows(fake)
}
