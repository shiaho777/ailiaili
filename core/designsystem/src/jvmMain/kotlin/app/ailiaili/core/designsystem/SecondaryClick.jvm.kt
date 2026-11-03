package app.ailiaili.core.designsystem

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.secondaryClick(onClick: () -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            onClick()
            event.changes.forEach { it.consume() }
        }
    }
