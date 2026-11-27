package app.ailiaili.core.designsystem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 首页那种只负责跳转的搜索入口：胶囊底、左侧放大镜、占位文字。 */
@Composable
fun BiliSearchEntry(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = BiliToolbarControlHeight)
            .biliPressScale(interaction)
            .aliMaterial(AliMaterial.Inset, MaterialTheme.shapes.medium)
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = foreground,
        )
        Text(
            placeholder,
            modifier = Modifier.weight(1f),
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 搜索页里真正可输入的搜索框：和 [BiliSearchEntry] 同一个胶囊外形，带清空和提交。 */
@Composable
fun BiliSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    accessibilityLabel: String = "搜索关键词或链接",
    submitLabel: String = "搜索",
    onPaste: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    val canSubmit = value.isNotBlank()
    var focused by remember { mutableStateOf(false) }
    if (autoFocus) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
    val submit = {
        if (canSubmit) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onSearch()
        }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = accessibilityLabel },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        decorationBox = { inner ->
            Row(
                Modifier
                    .heightIn(min = BiliToolbarControlHeight)
                    .aliMaterial(AliMaterial.Inset, MaterialTheme.shapes.medium, tinted = focused)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清空",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (value.isEmpty() && onPaste != null) {
                    AliStageIconButton(Icons.Filled.ContentPaste, "粘贴", onClick = {
                        focus.requestFocus()
                        onPaste()
                    })
                } else {
                    Box(
                        Modifier
                            .height(36.dp)
                            .aliMaterial(if (canSubmit) AliMaterial.Accent else AliMaterial.Floating, CircleShape)
                            .clickable(enabled = canSubmit, role = Role.Button) { submit() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            submitLabel,
                            color = if (canSubmit) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    )
}
