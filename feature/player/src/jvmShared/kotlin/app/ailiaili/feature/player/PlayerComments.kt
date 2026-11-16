package app.ailiaili.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.designsystem.BiliActionItem
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import androidx.compose.material3.LoadingIndicator
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.formatCount
import coil3.compose.AsyncImage

/**
 * 播放页评论区组件：单条评论（含楼中楼）、评论操作按钮、输入框。
 * 从 Player.kt 按职责拆出，逻辑零改动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentBlock(
    comment: Comment,
    replies: List<Comment>,
    rootRpid: Long,
    onSpace: (Long) -> Unit,
    onLike: (Comment) -> Unit,
    onDislike: (Comment) -> Unit,
    onReply: (Long, Comment) -> Unit,
    onExpand: (() -> Unit)?,
    onPreviewImage: (List<String>, Int, String) -> Unit,
    nested: Boolean = false,
    onOpenLink: ((String, String?) -> Unit)? = null,
    /** 提供后，「查看 N 条回复」进入独立楼中楼详情页。 */
    onOpenDetail: ((Comment) -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.padding(vertical = if (nested) 4.dp else 8.dp)) {
        if (comment.face.isNotBlank()) {
            AsyncImage(
                model = app.ailiaili.core.model.cdnThumb(comment.face, 80),
                contentDescription = comment.name,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(if (nested) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(if (nested) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                contentAlignment = Alignment.Center,
            ) {
                Text(comment.name.take(1).ifBlank { "用" }, color = accent, fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (comment.pinned) {
                    BiliBadge(
                        text = "置顶",
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
                Text(
                    comment.name.ifBlank { "用户" },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = comment.mid > 0) { onSpace(comment.mid) },
                    color = if (comment.up) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (nested) 13.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comment.level > 0) {
                    BiliBadge(
                        text = "LV${comment.level}",
                        tone = BiliBadgeTone.Neutral,
                        style = BiliBadgeStyle.Soft,
                    )
                }
                if (comment.up) {
                    BiliBadge(
                        text = "UP",
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
            }
            MetaRow(
                items = listOfNotNull(
                    comment.timeText.takeIf { it.isNotBlank() }?.let {
                        MetaSpec(Icons.Filled.Schedule, it, MetricTone.Neutral)
                    },
                    comment.location.takeIf { it.isNotBlank() }?.let {
                        MetaSpec(Icons.Filled.LocationOn, it, MetricTone.Neutral)
                    },
                ),
                maxItems = 2,
            )
            if (comment.message.isNotBlank()) {
                SelectionContainer {
                    CommentRichText(
                        comment = comment,
                        modifier = Modifier.padding(top = 6.dp),
                        color = ink,
                        accent = accent,
                        onOpenLink = onOpenLink,
                    )
                }
            }
            if (comment.pictures.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val allUrls = comment.pictures.map { it.url }
                    comment.pictures.forEachIndexed { picIndex, picture ->
                        val height = 140.dp
                        val width = if (picture.width > 0 && picture.height > 0) {
                            (height * (picture.width.toFloat() / picture.height))
                                .coerceIn(60.dp, 240.dp)
                        } else {
                            height
                        }
                        AsyncImage(
                            // 缩略图按显示尺寸 2x 取 CDN 缩图；点开预览仍加载原图。
                            model = app.ailiaili.core.model.cdnThumb(
                                picture.url,
                                (width.value * 2).toInt(),
                                (height.value * 2).toInt(),
                            ),
                            contentDescription = "评论图片",
                            modifier = Modifier
                                .size(width, height)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onPreviewImage(
                                        allUrls,
                                        picIndex,
                                        comment.name.ifBlank { "评论图片" },
                                    )
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            Row(
                Modifier.padding(top = 4.dp).offset(x = (-6).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommentAction(
                    Icons.Filled.ThumbUp,
                    formatCount(comment.like.toLong()),
                    comment.liked,
                    description = "点赞",
                ) { onLike(comment) }
                CommentAction(Icons.Filled.ThumbDown, "", comment.disliked, description = "点踩") { onDislike(comment) }
                CommentAction(Icons.AutoMirrored.Filled.Reply, "回复", false) { onReply(rootRpid, comment) }
            }
            if (!nested && (replies.isNotEmpty() || onExpand != null || (onOpenDetail != null && comment.replyCount > 0))) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    replies.forEachIndexed { index, reply ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                        key(reply.rpid) {
                            CommentBlock(
                                reply,
                                emptyList(),
                                rootRpid,
                                onSpace,
                                onLike,
                                onDislike,
                                onReply,
                                onExpand = null,
                                onPreviewImage = onPreviewImage,
                                nested = true,
                                onOpenLink = onOpenLink,
                            )
                        }
                    }
                    if (onExpand != null || onOpenDetail != null) {
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    if (onOpenDetail != null) onOpenDetail(comment) else onExpand?.invoke()
                                }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "查看 ${comment.replyCount} 条回复",
                                color = accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommentAction(
    icon: ImageVector,
    label: String,
    on: Boolean,
    description: String? = null,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "commentActionTint",
    )
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = description ?: label.ifBlank { null },
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (label.isNotEmpty()) {
            Text(label, modifier = Modifier.padding(start = 4.dp), color = tint, fontSize = 13.sp)
        }
    }
}

@Composable
internal fun Act(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    active: Color = MaterialTheme.colorScheme.primary,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    BiliActionItem(
        icon = icon,
        label = label,
        modifier = modifier,
        active = on,
        accent = active,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** 竖向操作下方只放计数；为 0 时回退成动作名。 */
internal fun actLabel(name: String, n: Long): String = formatCount(n).ifBlank { name }

@Composable
internal fun CommentComposer(
    draft: String,
    onDraft: (String) -> Unit,
    replyingTo: String?,
    expanded: Boolean,
    sending: Boolean,
    onToggleExpand: () -> Unit,
    onCancelReply: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = replyingTo != null) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(scheme.primaryContainer)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "回复 @${replyingTo.orEmpty()}",
                    modifier = Modifier.padding(start = 6.dp),
                    color = scheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onCancelReply, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "取消回复",
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraft,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { onFocus(it.isFocused) },
                placeholder = { Text(if (replyingTo == null) "发一条友善的评论" else "回复 @$replyingTo") },
                shape = MaterialTheme.shapes.medium,
                minLines = if (expanded) 5 else 1,
                maxLines = if (expanded) 12 else 4,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                        contentDescription = if (expanded) "收起输入框" else "展开输入框",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (sending) {
                        LoadingIndicator(Modifier.size(22.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (replyingTo == null) "发送" else "回复",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
