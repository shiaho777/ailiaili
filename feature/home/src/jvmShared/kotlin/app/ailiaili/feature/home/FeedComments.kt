package app.ailiaili.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.ailiaili.core.designsystem.AliStageSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.cdnThumb
import app.ailiaili.core.model.formatCount
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

private enum class CommentOrder(val label: String, val mode: Int) {
    Popular("热门", 3),
    Latest("最新", 2),
}

@Composable
internal fun FeedComments(item: VideoItem, onClose: () -> Unit, onCommentDetail: (Long, Long) -> Unit) {
    var order by remember(item.stableKey) { mutableStateOf(CommentOrder.Popular) }
    var total by remember(item.stableKey) { mutableIntStateOf(0) }
    var comments by remember(item.stableKey) { mutableStateOf<List<Comment>>(emptyList()) }
    var aid by remember(item.stableKey) { mutableLongStateOf(item.aid) }
    var requestedPage by remember(item.stableKey) { mutableIntStateOf(1) }
    var retry by remember(item.stableKey) { mutableIntStateOf(0) }
    var loading by remember(item.stableKey) { mutableStateOf(true) }
    var more by remember(item.stableKey) { mutableStateOf(false) }
    var error by remember(item.stableKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.stableKey, order, requestedPage, retry) {
        loading = true
        error = null
        try {
            val repository = AppGraph.repo
            if (aid <= 0) aid = repository.video(item.bvid).getOrThrow().aid
            check(aid > 0)
            val page = repository.comments(aid, requestedPage, order.mode).getOrThrow()
            ensureActive()
            comments = (comments + page.items).distinctBy { it.rpid }
            more = page.hasMore
            if (page.total > 0) total = page.total
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message?.takeIf { it.isNotBlank() } ?: "请稍后再试"
        } finally {
            if (isActive) loading = false
        }
    }
    AliStageSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().heightIn(max = 660.dp).fillMaxHeight(.68f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (total > 0) "${formatCount(total.toLong())} 条评论" else "评论",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                AliStageIconButton(Icons.Filled.Close, "关闭评论", onClose)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommentOrder.entries.forEach { next ->
                    BiliFilterChip(selected = next == order, onClick = {
                        if (next != order) {
                            comments = emptyList()
                            more = false
                            error = null
                            loading = true
                            requestedPage = 1
                            order = next
                        }
                    }, label = { Text(next.label) })
                }
            }
            HorizontalDivider()
            if (comments.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(28.dp))
                        error != null -> FeedNotice("评论加载失败", error, "重试") { retry++ }
                        else -> Text("还没有评论", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(comments, key = { it.rpid }) { comment ->
                        FeedCommentRow(comment) { onCommentDetail(aid, comment.rpid) }
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            when {
                                loading -> CircularProgressIndicator(Modifier.size(24.dp))
                                error != null -> TextButton({ retry++ }) { Text("重试加载") }
                                more -> TextButton({ requestedPage++ }) { Text("更多评论") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCommentRow(comment: Comment, onDetail: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "查看评论详情", onClick = onDetail)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            if (comment.face.isBlank()) {
                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            } else {
                AsyncImage(
                    cdnThumb(comment.face, 96), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(comment.name, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(
                comment.message, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium,
                maxLines = 8, overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                comment.timeText.takeIf { it.isNotBlank() },
                "${comment.replyCount} 条回复".takeIf { comment.replyCount > 0 },
            )
            if (details.isNotEmpty()) {
                Text(details.joinToString(" · "), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 60.dp))
}
