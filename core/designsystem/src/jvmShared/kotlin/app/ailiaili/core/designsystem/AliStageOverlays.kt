package app.ailiaili.core.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun AliStageDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.padding(horizontal = 16.dp).widthIn(max = 520.dp)
            .aliMaterial(AliMaterial.Floating, shape, elevated = true),
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = shape,
        containerColor = Color.Transparent,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

@Composable
fun AliStageMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 176.dp, max = 340.dp)
            .aliMaterial(AliMaterial.Floating, shape),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AliStageSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = shape,
        containerColor = colors.fillFor(AliMaterial.Floating),
        contentColor = colors.onSurface,
        scrimColor = Color.Black.copy(alpha = .60f),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.onSurfaceVariant.copy(alpha = .48f)) },
        content = content,
    )
}
