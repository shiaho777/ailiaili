package app.ailiaili.feature.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.designsystem.AvatarPreviewDialog
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.BiliFilterChip
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.LinkifiedText
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.RegisterPageRefresh
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.DynamicEntry
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.model.formatRelativeTime
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class DynamicDetailViewModel(
    private val id: String,
    seed: DynamicEntry?,
) : ViewModel() {
    var entry by mutableStateOf(seed)
    var loading by mutableStateOf(seed == null)
    var error by mutableStateOf<String?>(null)

    var comments by mutableStateOf<List<Comment>>(emptyList())
    var commentTotal by mutableIntStateOf(0)
    var commentMode by mutableIntStateOf(3)
    var commentLoading by mutableStateOf(false)
    var commentError by mutableStateOf<String?>(null)
    var commentsMore by mutableStateOf(false)
    private var commentPage = 1
    var replyExtra by mutableStateOf<Map<Long, List<Comment>>>(emptyMap())
    var replyDone by mutableStateOf(setOf<Long>())
    private var replyPage = emptyMap<Long, Int>()

    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
    var replyRoot by mutableStateOf(0L)
    var replyTarget by mutableStateOf<Comment?>(null)
    var toast by mutableStateOf<String?>(null)

    init {
        if (seed == null) reload() else loadComments(reset = true)
    }

    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.dynamicDetail(id)
                .onSuccess { entry = it; loadComments(reset = true) }
                .onFailure { error = it.message }
            loading = false
        }
    }

    fun switchSort(mode: Int) {
        if (commentMode == mode) return
        commentMode = mode
        loadComments(reset = true)
    }

    fun loadComments(reset: Boolean) {
        val post = entry?.post ?: return
        if (post.oid <= 0) return
        if (commentLoading) return
        commentLoading = true
        commentError = null
        val page = if (reset) 1 else commentPage + 1
        viewModelScope.launch {
            AppGraph.repo.comments(post.oid, page, commentMode, post.commentType)
                .onSuccess { got ->
                    comments = if (reset) got.items else (comments + got.items).distinctBy { it.rpid }
                    commentPage = page
                    commentsMore = got.hasMore
                    if (got.total > 0) commentTotal = got.total
                    if (reset) {
                        replyExtra = emptyMap()
                        replyDone = emptySet()
                        replyPage = emptyMap()
                    }
                }
                .onFailure { commentError = it.message ?: "评论加载失败" }
            commentLoading = false
        }
    }

    fun expandReplies(root: Comment) {
        val post = entry?.post ?: return
        viewModelScope.launch {
            val next = (replyPage[root.rpid] ?: 0) + 1
            AppGraph.repo.replies(post.oid, root.rpid, next, post.commentType).onSuccess { got ->
                val base = replyExtra[root.rpid] ?: root.replies
                val merged = (base + got).distinctBy { it.rpid }
                replyExtra = replyExtra + (root.rpid to merged)
                replyPage = replyPage + (root.rpid to next)
                if (got.isEmpty() || merged.size >= root.replyCount) replyDone = replyDone + root.rpid
            }.onFailure { commentError = it.message ?: "回复加载失败" }
        }
    }

    fun react(target: Comment, like: Boolean) {
        val post = entry?.post ?: return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "请先登录"
            return
        }
        val turningOn = if (like) !target.liked else !target.disliked
        fun patch(list: List<Comment>): List<Comment> = list.map { c ->
            if (c.rpid != target.rpid) c else if (like) {
                c.copy(
                    liked = turningOn,
                    like = (c.like + if (turningOn) 1 else -1).coerceAtLeast(0),
                    disliked = if (turningOn) false else c.disliked,
                )
            } else {
                c.copy(
                    disliked = turningOn,
                    liked = if (turningOn) false else c.liked,
                    like = if (turningOn && c.liked) (c.like - 1).coerceAtLeast(0) else c.like,
                )
            }
        }
        comments = patch(comments)
        replyExtra = replyExtra.mapValues { (_, v) -> patch(v) }
        viewModelScope.launch {
            val result = if (like) {
                AppGraph.repo.commentLike(post.oid, target.rpid, turningOn, post.commentType)
            } else {
                AppGraph.repo.commentDislike(post.oid, target.rpid, turningOn, post.commentType)
            }
            result.onFailure { toast = it.message ?: "操作失败" }
        }
    }

    fun togglePostLike() {
        val current = entry ?: return
        val post = current.post ?: return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "点赞需要先登录"
            return
        }
        val next = !post.liked
        entry = current.copy(
            post = post.copy(
                liked = next,
                likeCount = (post.likeCount + if (next) 1 else -1).coerceAtLeast(0),
            ),
        )
        viewModelScope.launch {
            AppGraph.repo.dynamicLike(post.id, next).onFailure {
                entry = entry?.let { e ->
                    e.copy(post = e.post?.copy(liked = post.liked, likeCount = post.likeCount))
                }
                toast = it.message ?: "点赞失败"
            }
        }
    }

    fun send() {
        val post = entry?.post ?: return
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "请先登录后再评论"
            return
        }
        sending = true
        viewModelScope.launch {
            AppGraph.repo.postComment(
                post.oid, text,
                root = replyRoot,
                parent = replyTarget?.rpid ?: 0,
                type = post.commentType,
            ).onSuccess {
                draft = ""
                replyRoot = 0
                replyTarget = null
                toast = "已发送"
                loadComments(reset = true)
            }.onFailure { toast = it.message ?: "发送失败" }
            sending = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicDetailRoute(
    id: String,
    seed: DynamicEntry?,
    onBack: () -> Unit,
    onAuthor: (Long) -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: DynamicDetailViewModel = appViewModel(key = "dyn-$id") { DynamicDetailViewModel(id, seed) },
) {
    val haptic = LocalHapticFeedback.current
    RegisterPageRefresh(vm::reload)
    var preview by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }
    LaunchedEffect(vm.toast) {
        val message = vm.toast ?: return@LaunchedEffect
        kotlinx.coroutines.delay(1800)
        if (vm.toast == message) vm.toast = null
    }
    preview?.let { (urls, index, name) ->
        AvatarPreviewDialog(
            url = urls.getOrElse(index) { urls.first() },
            name = name,
            onDismiss = { preview = null },
            onSaved = { vm.toast = it },
            urls = urls,
        )
    }
    Scaffold(topBar = { BiliBackHeader("动态详情", onBack) }) { padding ->
        LoadBox(
            loading = vm.loading,
            error = vm.error,
            empty = vm.entry == null,
            emptyText = "动态不存在",
            onRetry = vm::reload,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val entry = vm.entry ?: return@LoadBox
            val post = entry.post
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val uiScope = androidx.compose.runtime.rememberCoroutineScope()
            LazyColumn(
                Modifier.fillMaxSize().smoothWheelScroll(listState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "author") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (entry.authorFace.isNotBlank()) {
                            AsyncImage(
                                model = app.ailiaili.core.model.cdnThumb(entry.authorFace, 96),
                                contentDescription = entry.authorName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = entry.authorMid > 0) { onAuthor(entry.authorMid) },
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.authorName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable(enabled = entry.authorMid > 0) { onAuthor(entry.authorMid) },
                            )
                            if (entry.pubTs > 0) {
                                Text(
                                    formatRelativeTime(entry.pubTs),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (entry.desc.isNotBlank()) {
                    item(key = "desc") {
                        LinkifiedText(
                            text = entry.desc,
                            onOpenLink = { url, bvid ->
                                if (bvid != null) onVideo(VideoItem(bvid = bvid)) else openUrl(url)
                            },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
                if (post != null) {
                    if (post.title.isNotBlank()) {
                        item(key = "title") {
                            Text(post.title, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (post.text.isNotBlank() && post.text != entry.desc) {
                        item(key = "text") {
                            LinkifiedText(
                                text = post.text,
                                onOpenLink = { url, bvid ->
                                    if (bvid != null) onVideo(VideoItem(bvid = bvid)) else openUrl(url)
                                },
                                fontSize = 15.sp,
                                lineHeight = 23.sp,
                            )
                        }
                    }
                    if (post.pics.isNotEmpty()) {
                        item(key = "pics") {
                            PostPicGrid(post.pics, onPreview = { index ->
                                preview = Triple(post.pics, index, entry.authorName)
                            })
                        }
                    }
                    item(key = "actions") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BiliFilterChip(
                                selected = post.liked,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    vm.togglePostLike()
                                },
                                leadingIcon = { Icon(Icons.Filled.ThumbUp, null, Modifier.size(15.dp)) },
                                label = { Text("点赞 ${formatCount(post.likeCount)}".trim()) },
                            )
                            BiliFilterChip(
                                selected = false,
                                onClick = {
                                    // 评论区就在下方：滚到 comment-head；不在屏内时
                                    // 先跳到底部附近让它进组合，再精确定位。
                                    uiScope.launch {
                                        val idx = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == "comment-head" }?.index
                                        if (idx != null) {
                                            listState.animateScrollToItem(idx)
                                        } else {
                                            listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
                                            val near = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == "comment-head" }?.index
                                            if (near != null) listState.animateScrollToItem(near)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, null, Modifier.size(15.dp)) },
                                label = { Text("评论 ${formatCount(post.commentCount)}".trim()) },
                            )
                        }
                    }
                }
                entry.video?.let { video ->
                    item(key = "video") {
                        DynamicEntryCard(
                            entry = entry.copy(desc = ""),
                            onVideo = onVideo,
                            onAuthor = { onAuthor(it.ownerMid) },
                            onPost = {},
                        )
                    }
                }
                item(key = "comment-head") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BiliSectionTitle("评论 ${if (vm.commentTotal > 0) formatCount(vm.commentTotal.toLong()) else ""}".trim())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BiliFilterChip(
                                selected = vm.commentMode == 3,
                                onClick = { vm.switchSort(3) },
                                label = { Text("热门") },
                            )
                            BiliFilterChip(
                                selected = vm.commentMode == 2,
                                onClick = { vm.switchSort(2) },
                                label = { Text("时间") },
                            )
                        }
                        OutlinedTextField(
                            value = vm.draft,
                            onValueChange = { vm.draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(if (vm.replyTarget != null) "回复 @${vm.replyTarget?.name}" else "写下你的评论…")
                            },
                            minLines = 2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (vm.replyTarget != null) {
                                TextButton(onClick = { vm.replyTarget = null; vm.replyRoot = 0 }) { Text("取消回复") }
                            }
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(
                                onClick = vm::send,
                                enabled = !vm.sending && vm.draft.isNotBlank(),
                            ) { Text(if (vm.sending) "发送中…" else "发送") }
                        }
                        vm.commentError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
                items(vm.comments, key = { it.rpid }) { c ->
                    PostCommentBlock(
                        comment = c,
                        replies = vm.replyExtra[c.rpid] ?: c.replies,
                        canExpand = c.replyCount > (vm.replyExtra[c.rpid] ?: c.replies).size && c.rpid !in vm.replyDone,
                        onExpand = { vm.expandReplies(c) },
                        onLike = { vm.react(it, like = true) },
                        onDislike = { vm.react(it, like = false) },
                        onReply = { target ->
                            vm.replyRoot = c.rpid
                            vm.replyTarget = target
                        },
                        onAuthor = onAuthor,
                        onVideo = onVideo,
                        onPreview = { urls, index -> preview = Triple(urls, index, c.name) },
                    )
                }
                if (vm.commentsMore) {
                    item(key = "more") {
                        FilledTonalButton(
                            onClick = { vm.loadComments(reset = false) },
                            enabled = !vm.commentLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (vm.commentLoading) "加载中…" else "加载更多评论") }
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    vm.toast?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.toast = null },
            confirmButton = {},
            text = { Text(message) },
        )
    }
}

@Composable
private fun PostCommentBlock(
    comment: Comment,
    replies: List<Comment>,
    canExpand: Boolean,
    onExpand: () -> Unit,
    onLike: (Comment) -> Unit,
    onDislike: (Comment) -> Unit,
    onReply: (Comment) -> Unit,
    onAuthor: (Long) -> Unit,
    onVideo: (VideoItem) -> Unit,
    onPreview: (List<String>, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CommentLine(comment, onAuthor, onVideo, onPreview, compact = false)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable { onLike(comment) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = "点赞",
                    modifier = Modifier.size(15.dp),
                    tint = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatCount(comment.like.toLong()).ifBlank { "赞" },
                    fontSize = 12.sp,
                    color = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ThumbDown,
                contentDescription = "点踩",
                modifier = Modifier.size(15.dp).clickable { onDislike(comment) },
                tint = if (comment.disliked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.clickable { onReply(comment) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (replies.isNotEmpty() || canExpand) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                replies.forEachIndexed { index, reply ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    CommentLine(reply, onAuthor, onVideo, onPreview, compact = true)
                }
                if (canExpand) {
                    TextButton(onClick = onExpand) { Text("展开更多回复", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun CommentLine(
    comment: Comment,
    onAuthor: (Long) -> Unit,
    onVideo: (VideoItem) -> Unit,
    onPreview: (List<String>, Int) -> Unit,
    compact: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (comment.face.isNotBlank()) {
            AsyncImage(
                model = app.ailiaili.core.model.cdnThumb(comment.face, 72),
                contentDescription = comment.name,
                modifier = Modifier
                    .size(if (compact) 22.dp else 30.dp)
                    .clip(CircleShape)
                    .clickable(enabled = comment.mid > 0) { onAuthor(comment.mid) },
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    comment.name,
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(enabled = comment.mid > 0) { onAuthor(comment.mid) },
                )
                if (comment.timeText.isNotBlank()) {
                    Text(comment.timeText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (comment.message.isNotBlank()) {
                LinkifiedText(
                    text = comment.message,
                    onOpenLink = { url, bvid ->
                        if (bvid != null) onVideo(VideoItem(bvid = bvid)) else openUrl(url)
                    },
                    fontSize = if (compact) 13.sp else 14.sp,
                    lineHeight = if (compact) 19.sp else 21.sp,
                )
            }
            if (comment.pictures.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val urls = comment.pictures.map { it.url }
                    urls.forEachIndexed { index, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "评论图片",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { onPreview(urls, index) },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}