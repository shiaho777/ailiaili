package app.ailiaili.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliPaginationFooter
import app.ailiaili.core.designsystem.QuickActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.designsystem.AvatarPreviewDialog
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.RegisterPageRefresh
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.Comment
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.launch

/**
 * 评论详情页（楼中楼）：完整回复分页加载，热门/时间排序在页内切换不弹回，
 * 页内直接回复。对齐上游 v1.6.1 的交互修复：切排序停留在原楼中楼里。
 */
class CommentDetailViewModel(
    private val aid: Long,
    private val rootRpid: Long,
    seed: Comment?,
) : ViewModel() {
    var root by mutableStateOf(seed)
    var replies by mutableStateOf<List<Comment>>(seed?.replies.orEmpty())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(true)
    /** 3 热门 / 2 时间。楼中楼接口按 like 排序时上层自排。 */
    var sortMode by mutableIntStateOf(3)
    private var page = 0
    private var loadGen = 0

    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
    var replyTarget by mutableStateOf<Comment?>(null)
    var toast by mutableStateOf<String?>(null)
    var composerExpanded by mutableStateOf(false)
    var composing by mutableStateOf(false)

    init {
        loadPage(reset = true)
        if (root == null) {
            // 从导航栈恢复（无种子）时反查根评论：主列表按页找，最多翻 5 页兜底。
            viewModelScope.launch {
                for (p in 1..5) {
                    val page = AppGraph.repo.comments(aid, p, 3).getOrNull() ?: break
                    val hit = page.items.firstOrNull { it.rpid == rootRpid }
                    if (hit != null) {
                        root = hit
                        break
                    }
                    if (!page.hasMore) break
                }
            }
        }
    }

    fun switchSort(mode: Int) {
        if (sortMode == mode) return
        sortMode = mode
        // 已有回复立即按新排序重排，不用等网络；分页拉取继续补齐。
        replies = sortReplies(replies)
        loadPage(reset = true)
    }

    private fun sortReplies(list: List<Comment>): List<Comment> = when (sortMode) {
        3 -> list.sortedByDescending { it.like }
        else -> list.sortedByDescending { it.rpid }
    }

    fun loadPage(reset: Boolean) {
        if (aid <= 0 || rootRpid <= 0) return
        if (loading) return
        if (!reset && !hasMore) return
        val token = if (reset) ++loadGen else loadGen
        loading = true
        error = null
        val next = if (reset) 1 else page + 1
        viewModelScope.launch {
            AppGraph.repo.replies(aid, rootRpid, next)
                .onSuccess { got ->
                    if (token != loadGen) return@onSuccess
                    page = next
                    val merged = if (reset) got else (replies + got).distinctBy { it.rpid }
                    replies = sortReplies(merged)
                    hasMore = got.isNotEmpty()
                    if (root == null && got.isNotEmpty()) {
                        // 没有种子时从回复里反推不了根评论，保持空根由页面隐藏根块。
                    }
                }
                .onFailure { if (token == loadGen) error = it.message ?: "回复加载失败" }
            if (token == loadGen) loading = false
        }
    }

    private fun patch(target: Comment, like: Boolean) {
        val turningOn = if (like) !target.liked else !target.disliked
        fun one(c: Comment): Comment {
            if (c.rpid != target.rpid) return c
            return if (like) {
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
        root = root?.let(::one)
        replies = replies.map(::one)
        viewModelScope.launch {
            val result = if (like) {
                AppGraph.repo.commentLike(aid, target.rpid, turningOn)
            } else {
                AppGraph.repo.commentDislike(aid, target.rpid, turningOn)
            }
            result.onFailure { toast = it.message ?: "操作失败" }
        }
    }

    fun like(target: Comment) = patch(target, like = true)
    fun dislike(target: Comment) = patch(target, like = false)

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "请先登录后再回复"
            return
        }
        sending = true
        viewModelScope.launch {
            AppGraph.repo.postComment(
                aid, text,
                root = rootRpid,
                parent = replyTarget?.rpid ?: rootRpid,
            ).onSuccess {
                draft = ""
                replyTarget = null
                toast = "已回复"
                loadPage(reset = true)
            }.onFailure { toast = it.message ?: "回复失败" }
            sending = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentDetailRoute(
    aid: Long,
    rootRpid: Long,
    seed: Comment?,
    onBack: () -> Unit,
    onSpace: (Long) -> Unit,
    onVideo: (VideoItem) -> Unit,
    vm: CommentDetailViewModel = appViewModel(key = "cd-$aid-$rootRpid") {
        CommentDetailViewModel(aid, rootRpid, seed)
    },
) {
    RegisterPageRefresh { vm.loadPage(reset = true) }
    var preview by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }
    LaunchedEffect(vm.toast) {
        val message = vm.toast ?: return@LaunchedEffect
        QuickActions.showToast(message)
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
    val openLink: (String, String?) -> Unit = { url, bvid ->
        if (bvid != null) onVideo(VideoItem(bvid = bvid)) else openUrl(url)
    }
    Scaffold(topBar = {
        AliStagePageHeader("评论详情", onBack = onBack) {
            AliStageIconButton(
                Icons.Filled.Refresh, "刷新回复", { vm.loadPage(reset = true) }, enabled = !vm.loading,
            )
        }
    }) { padding ->
        AliStageBody(Modifier.padding(padding).imePadding(), maxWidth = 820.dp) {
            val detailState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth()
                    .smoothWheelScroll(detailState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                state = detailState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.root?.let { rootComment ->
                    item(key = "root") {
                        AliStagePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                            CommentBlock(
                                comment = rootComment,
                                replies = emptyList(),
                                rootRpid = rootRpid,
                                onSpace = onSpace,
                                onLike = vm::like,
                                onDislike = vm::dislike,
                                onReply = { _, target -> vm.replyTarget = target },
                                onExpand = null,
                                onPreviewImage = { urls, index, name -> preview = Triple(urls, index, name) },
                                onOpenLink = openLink,
                            )
                        }
                    }
                }
                stickyHeader(key = "sort") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AliStageTabs(
                                labels = listOf("热门", "最新"),
                                selected = if (vm.sortMode == 3) 0 else 1,
                                onSelect = { vm.switchSort(if (it == 0) 3 else 2) },
                                modifier = Modifier.widthIn(max = 220.dp).weight(1f, fill = false),
                            )
                            Text(
                                "${vm.replies.size} 条回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                items(vm.replies, key = { it.rpid }) { reply ->
                    AliStagePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        CommentBlock(
                            comment = reply,
                            replies = emptyList(),
                            rootRpid = rootRpid,
                            onSpace = onSpace,
                            onLike = vm::like,
                            onDislike = vm::dislike,
                            onReply = { _, target -> vm.replyTarget = target },
                            onExpand = null,
                            onPreviewImage = { urls, index, name -> preview = Triple(urls, index, name) },
                            nested = true,
                            onOpenLink = openLink,
                        )
                    }
                }
                when {
                    vm.loading || vm.hasMore || vm.error != null -> item(key = "more") {
                        BiliPaginationFooter(
                            loading = vm.loading,
                            error = vm.error,
                            onLoadMore = { vm.loadPage(reset = vm.replies.isEmpty()) },
                        )
                    }
                    vm.replies.isEmpty() -> item(key = "empty") {
                        BiliEmptyState("暂无回复", icon = Icons.AutoMirrored.Filled.Comment)
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(8.dp)) }
            }
            CommentComposer(
                draft = vm.draft,
                onDraft = { vm.draft = it },
                replyingTo = vm.replyTarget?.name,
                expanded = vm.composerExpanded,
                sending = vm.sending,
                onToggleExpand = { vm.composerExpanded = !vm.composerExpanded },
                onCancelReply = { vm.replyTarget = null },
                onFocus = { vm.composing = it },
                onSend = vm::send,
            )
        }
    }
}
