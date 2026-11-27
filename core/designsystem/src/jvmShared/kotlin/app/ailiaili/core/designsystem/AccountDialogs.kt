package app.ailiaili.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.FavFolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun CreateFavoriteFolderDialog(onDismiss: () -> Unit, onCreated: (FavFolder?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var privateFolder by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = { if (!saving) onDismiss() }
    val submit: () -> Unit = {
        if (!saving && name.trim().length in 1..20) {
            saving = true
            error = null
            scope.launch {
                AppGraph.repo.createFavFolder(name, privateFolder)
                    .onSuccess(onCreated)
                    .onFailure { error = it.message ?: "新建收藏夹失败" }
                saving = false
            }
        }
    }

    AliStageDialog(
        onDismissRequest = dismiss,
        title = { Text("新建收藏夹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    supportingText = { Text(error ?: "${name.trim().length}/20") },
                    isError = error != null || name.trim().length > 20,
                    enabled = !saving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !saving) { privateFolder = !privateFolder },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = privateFolder, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                    Text("设为私密")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = !saving && name.trim().length in 1..20) {
                Text(if (saving) "创建中…" else "创建")
            }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("取消") } },
    )
}

fun TextFieldValue.insertAtSelection(text: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    return TextFieldValue(this.text.replaceRange(start, end, text), TextRange(start + text.length))
}

@Composable
fun CommentEmoteButton(onInsert: (String) -> Unit, modifier: Modifier = Modifier) {
    val session by AppGraph.repo.session.collectAsState()
    val uid = session.dedeUserId
    var open by remember(uid) { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(Icons.Filled.EmojiEmotions, "插入表情", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!open) return

    var emotes by remember(uid) { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember(uid) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(uid, attempt) {
        error = null
        if (!session.loggedIn) {
            error = "登录后可以发表评论表情"
        } else {
            AppGraph.repo.commentEmotes().onSuccess { emotes = it }
                .onFailure { error = it.message ?: "表情加载失败" }
        }
    }
    AliStageDialog(
        onDismissRequest = { open = false },
        title = { Text("评论表情") },
        text = {
            when {
                error != null -> Column {
                    Text(error.orEmpty())
                    if (session.loggedIn) TextButton(onClick = { attempt++ }) { Text("重试") }
                }
                emotes == null -> BiliLoadingState()
                emotes.isNullOrEmpty() -> Text("没有可用的表情")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(44.dp),
                    modifier = Modifier.height(280.dp).fillMaxWidth(),
                ) {
                    items(emotes.orEmpty(), key = { it.first }) { (shortCode, url) ->
                        Box(
                            Modifier.size(44.dp).clickable {
                                onInsert(shortCode)
                                open = false
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(url, shortCode, Modifier.size(32.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
    )
}
