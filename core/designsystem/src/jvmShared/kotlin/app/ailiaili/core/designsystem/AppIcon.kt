package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import app.ailiaili.core.designsystem.resources.Res
import app.ailiaili.core.designsystem.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
