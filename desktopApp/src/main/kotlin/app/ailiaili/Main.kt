package app.ailiaili

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DesktopHost
import app.ailiaili.core.designsystem.BiliTheme
import app.ailiaili.core.designsystem.InstallBiliImages
import java.awt.Dimension
import java.awt.Frame
import java.awt.SystemTray
import java.awt.Toolkit

fun main() {
    if (!SingleInstance.tryAcquire()) return
    application {
        AppGraph.init()
        DesktopHost.exit = ::exitApplication
        val state = rememberWindowState(width = 1280.dp, height = 800.dp)
        val icon = painterResource("icon.png")
        val trayOk = remember { SystemTray.isSupported() }
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }
        var balloonShown by remember { mutableStateOf(false) }
        fun showMain() {
            visible = true
        }
        fun hideMain() {
            visible = false
            if (!balloonShown) {
                balloonShown = true
                trayState.sendNotification(
                    Notification(
                        "ailiaili 仍在后台运行",
                        "双击托盘图标可以恢复主界面，右键菜单可退出。",
                        Notification.Type.Info,
                    ),
                )
            }
        }
        DesktopHost.showWindow = { showMain() }
        DesktopHost.hideWindow = { hideMain() }
        if (trayOk) {
            Tray(
                icon = icon,
                state = trayState,
                tooltip = "ailiaili",
                onAction = { showMain() },
            ) {
                Item("显示主界面", onClick = { showMain() })
                Item("最小化到托盘", onClick = { hideMain() })
                Separator()
                Item("退出", onClick = { exitApplication() })
            }
        }
        Window(
            onCloseRequest = {
                val closeToTray = AppGraph.repo.settings.value.closeToTray
                if (closeToTray && trayOk) hideMain() else exitApplication()
            },
            state = state,
            visible = visible,
            title = "ailiaili",
            icon = icon,
        ) {
            // 竖屏副屏 / 小屏：初始窗口不能比工作区还大，否则右侧按钮会跑到屏幕外。
            // Compose 的 dp 与 AWT 像素之间差一个屏幕缩放，最小尺寸也要按同一比例换算，
            // 否则 Retina 上 720px 实际只有 360dp，布局会被压坏。
            LaunchedEffect(Unit) {
                val gc = window.graphicsConfiguration
                val scale = gc.defaultTransform.scaleX.toFloat().takeIf { it > 0f } ?: 1f
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
                val workWidth = (gc.bounds.width - insets.left - insets.right) / scale
                val workHeight = (gc.bounds.height - insets.top - insets.bottom) / scale
                window.minimumSize = Dimension(
                    (720 * scale).toInt(),
                    (480 * scale).toInt(),
                )
                val maxWidth = workWidth.dp
                val maxHeight = workHeight.dp
                if (state.size.width > maxWidth || state.size.height > maxHeight) {
                    state.size = DpSize(
                        minOf(state.size.width, maxWidth),
                        minOf(state.size.height, maxHeight),
                    )
                }
            }
            DesktopHost.fullscreen = { on ->
                state.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
            LaunchedEffect(visible) {
                if (visible) {
                    window.extendedState = Frame.NORMAL
                    window.toFront()
                    window.requestFocus()
                }
            }
            InstallBiliImages()
            val settings by AppGraph.repo.settings.collectAsState()
            BiliTheme(settings.theme, settings.accentHex, settings.accentFollowSystem) {
                AppRoot()
                UpdatePrompt(settings.autoCheckUpdate, settings.skippedVersion)
            }
        }
    }
}
