package app.ailiaili.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliErrorState
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliLoadingState
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.MsgItem
import app.ailiaili.core.model.MsgSession
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class InboxViewModel : ViewModel() {
    var items by mutableStateOf<List<MsgSession>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    init { reload() }
    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.messages().onSuccess { items = it; loading = false }.onFailure { error = it.message; loading = false }
        }
    }
}

class ChatViewModel(private val talkerId: Long) : ViewModel() {
    var items by mutableStateOf<List<MsgItem>>(emptyList())
    var draft by mutableStateOf("")
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
    var emotes by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var emoteOpen by mutableStateOf(false)
    var uploading by mutableStateOf(false)
    init { reload() }
    fun reload() {
        loading = true
        viewModelScope.launch {
            AppGraph.repo.msgHistory(talkerId)
                .onSuccess { items = it; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.sendMsg(talkerId, text).onSuccess {
                draft = ""
                reload()
            }.onFailure { error = it.message }
            sending = false
        }
    }

    fun toggleEmotes() {
        emoteOpen = !emoteOpen
        if (emoteOpen && emotes.isEmpty()) {
            viewModelScope.launch {
                AppGraph.repo.msgEmotes().onSuccess { emotes = it }
            }
        }
    }

    fun insertEmote(code: String) {
        draft += code
    }

    fun sendImage() {
        if (uploading) return
        viewModelScope.launch {
            val picked = runCatching { app.ailiaili.core.data.pickImage() }.getOrNull() ?: return@launch
            uploading = true
            error = null
            AppGraph.repo.sendMsgImage(talkerId, picked.bytes, picked.name)
                .onSuccess { reload() }
                .onFailure { error = it.message ?: "图片发送失败" }
            uploading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesRoute(
    onBack: () -> Unit,
    onOpen: (MsgSession) -> Unit,
    onLogin: () -> Unit,
    vm: InboxViewModel = appViewModel { InboxViewModel() },
) {
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    val session by AppGraph.repo.session.collectAsState()
    var seenLogin by remember { mutableStateOf(session.loggedIn) }
    LaunchedEffect(session.loggedIn) {
        if (session.loggedIn && !seenLogin) vm.reload()
        seenLogin = session.loggedIn
    }
    Scaffold(
        topBar = {
            BiliBackHeader("消息", onBack) {
                if (session.loggedIn) {
                    BiliHeaderAction(Icons.Filled.Refresh, "刷新", vm::reload, enabled = !vm.loading)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!session.loggedIn) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BiliEmptyState(
                        title = "登录后查看私信",
                        message = "和 UP 主、好友的对话都会出现在这里",
                        icon = Icons.Filled.Person,
                        action = {
                            Button(onClick = onLogin, modifier = Modifier.padding(top = 6.dp)) {
                                Text("登录哔哩哔哩")
                            }
                        },
                    )
                }
                return@Column
            }
            val windowWidth = app.ailiaili.core.designsystem.LocalWindowForm.current.width
            val wide = windowWidth.value >= 840f
            var opened by remember { mutableStateOf<MsgSession?>(null) }
            LoadBox(vm.loading, vm.error, vm.items.isEmpty(), "没有私信", vm::reload) {
                if (wide) {
                    // 微信式双栏：左侧会话列表，右侧直接聊天，不再整页跳转。
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.width(300.dp).fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(vm.items, key = { it.talkerId }) { session ->
                                SessionRow(
                                    session,
                                    { opened = session },
                                    Modifier.animateItem(),
                                    selected = opened?.talkerId == session.talkerId,
                                )
                            }
                        }
                        androidx.compose.material3.VerticalDivider()
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            val picked = opened
                            if (picked == null) {
                                BiliEmptyState(
                                    title = "选择一个会话",
                                    message = "左侧点开就能聊",
                                    icon = Icons.Filled.Email,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else {
                                androidx.compose.runtime.key(picked.talkerId) {
                                    ChatRoute(picked.talkerId, picked.name, onBack = { opened = null })
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(vm.items, key = { it.talkerId }) { session ->
                            SessionRow(session, { onOpen(session) }, Modifier.animateItem())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: MsgSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val unread = session.unread > 0
    val name = session.name.ifBlank { "UID ${session.talkerId}" }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(session.face, name, 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.timeText.isNotBlank()) {
                    Text(
                        session.timeText,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    session.preview.ifBlank { "[消息]" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (unread) {
                    BiliBadge(
                        text = if (session.unread > 99) "99+" else "${session.unread}",
                        modifier = Modifier.padding(start = 8.dp),
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Solid,
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(face: String, name: String, size: Dp) {
    if (face.isNotBlank()) {
        AsyncImage(
            model = app.ailiaili.core.model.cdnThumb(face, (size.value * 2).toInt()),
            contentDescription = name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(talkerId: Long, name: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "chat-$talkerId") { ChatViewModel(talkerId) }
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    val listState = rememberLazyListState()
    var preview by remember { mutableStateOf<String?>(null) }
    val emoteMap = remember(vm.emotes) { vm.emotes.toMap() }
    LaunchedEffect(vm.items.size) {
        if (vm.items.isNotEmpty()) listState.animateScrollToItem(vm.items.lastIndex)
    }
    // 收到过带表情的消息但还没打开过面板：静默拉一次表情表用于内联渲染。
    LaunchedEffect(vm.items.size) {
        if (vm.emotes.isEmpty() && vm.items.any { it.text.contains('[') }) {
            AppGraph.repo.msgEmotes().onSuccess { vm.emotes = it }
        }
    }
    preview?.let { url ->
        app.ailiaili.core.designsystem.AvatarPreviewDialog(
            url = url,
            name = name.ifBlank { "图片消息" },
            onDismiss = { preview = null },
            onSaved = { preview = null },
        )
    }
    Scaffold(
        topBar = {
            BiliBackHeader(name.ifBlank { "私信" }, onBack)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f)) {
                if (vm.loading && vm.items.isEmpty()) {
                    BiliLoadingState(
                        modifier = Modifier.align(Alignment.Center),
                        label = "正在加载聊天记录…",
                    )
                } else if (vm.items.isEmpty() && vm.error != null) {
                    BiliErrorState(
                        message = vm.error.orEmpty(),
                        onRetry = vm::reload,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (vm.items.isEmpty()) {
                    BiliEmptyState(
                        title = "还没有聊天记录",
                        message = "打个招呼吧",
                        icon = Icons.Filled.Email,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(vm.items, key = { _, msg -> msg.seqno }) { index, msg ->
                            val prev = vm.items.getOrNull(index - 1)
                            Column(Modifier.fillMaxWidth()) {
                                if (msg.timeText.isNotBlank() && msg.timeText != prev?.timeText) {
                                    TimeSeparator(msg.timeText)
                                }
                                MessageBubble(
                                    msg,
                                    groupedWithPrev = prev?.mine == msg.mine,
                                    emoteMap = emoteMap,
                                    onPreview = { preview = it },
                                )
                            }
                        }
                    }
                }
            }
            vm.error?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        message,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = vm::reload) { Text("重试") }
                }
            }
            ChatInputBar(
                draft = vm.draft,
                onDraft = { vm.draft = it },
                sending = vm.sending,
                onSend = vm::send,
                emotes = vm.emotes,
                emoteOpen = vm.emoteOpen,
                onToggleEmotes = vm::toggleEmotes,
                onPickEmote = vm::insertEmote,
                uploading = vm.uploading,
                onSendImage = vm::sendImage,
            )
        }
    }
}

@Composable
private fun TimeSeparator(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    msg: MsgItem,
    groupedWithPrev: Boolean,
    emoteMap: Map<String, String> = emptyMap(),
    onPreview: (String) -> Unit = {},
) {
    val big = 18.dp
    val small = 4.dp
    val shape = if (msg.mine) {
        RoundedCornerShape(topStart = big, topEnd = if (groupedWithPrev) small else big, bottomEnd = small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (groupedWithPrev) small else big, topEnd = big, bottomEnd = big, bottomStart = small)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = if (groupedWithPrev) 0.dp else 4.dp),
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
    ) {
        if (msg.imageUrl.isNotBlank() && (msg.text.isBlank() || msg.text == "[图片]")) {
            AsyncImage(
                model = app.ailiaili.core.model.cdnThumb(msg.imageUrl, 440, 560),
                contentDescription = "图片消息",
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .heightIn(max = 280.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onPreview(msg.imageUrl) },
                contentScale = ContentScale.Fit,
            )
        } else {
            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                color = if (msg.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (msg.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = shape,
            ) {
                EmoteText(
                    text = msg.text.ifBlank { "[消息]" },
                    emoteMap = emoteMap,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** 私信正文：把 [表情短代码] 换成内联表情图，其余保持纯文本。 */
@Composable
private fun EmoteText(text: String, emoteMap: Map<String, String>, modifier: Modifier = Modifier) {
    if (emoteMap.isEmpty() || !text.contains('[')) {
        Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val keys = remember(emoteMap) { emoteMap.keys.sortedByDescending { it.length } }
    val (annotated, inline) = remember(text, keys) {
        val inlineMap = mutableMapOf<String, androidx.compose.foundation.text.InlineTextContent>()
        val built = androidx.compose.ui.text.buildAnnotatedString {
            var i = 0
            var start = 0
            var n = 0
            while (i < text.length) {
                val key = keys.firstOrNull { text.startsWith(it, i) }
                if (key == null) {
                    i++
                    continue
                }
                if (i > start) append(text.substring(start, i))
                val id = "e${n++}"
                appendInlineContent(id, key)
                inlineMap[id] = androidx.compose.foundation.text.InlineTextContent(
                    androidx.compose.ui.text.Placeholder(
                        20.sp, 20.sp,
                        androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    AsyncImage(model = emoteMap.getValue(key), contentDescription = key, modifier = Modifier.size(20.dp))
                }
                i += key.length
                start = i
            }
            if (start < text.length) append(text.substring(start))
        }
        built to inlineMap
    }
    androidx.compose.foundation.text.BasicText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.merge(
            androidx.compose.ui.text.TextStyle(color = androidx.compose.material3.LocalContentColor.current),
        ),
        inlineContent = inline,
    )
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraft: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    emotes: List<Pair<String, String>>,
    emoteOpen: Boolean,
    onToggleEmotes: () -> Unit,
    onPickEmote: (String) -> Unit,
    uploading: Boolean,
    onSendImage: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onToggleEmotes) {
                    Icon(
                        Icons.Filled.EmojiEmotions,
                        contentDescription = "表情",
                        tint = if (emoteOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSendImage, enabled = !uploading) {
                    if (uploading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = "发送图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发消息…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = draft.isNotBlank() && !sending,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
            if (emoteOpen) {
                if (emotes.isEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(52.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        gridItems(emotes, key = { it.first }) { (code, url) ->
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPickEmote(code) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = code,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
