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
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.Toolkit

fun main() {
    if (!SingleInstance.tryAcquire()) return
    application {
        AppGraph.init()
        DesktopHost.exit = ::exitApplication
        // 创建窗口前先按主屏工作区夹一次，避免小屏 / 竖屏副屏上先出现一个超出屏幕的窗口。
        val workArea = remember { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }
        val state = rememberWindowState(
            width = minOf(1280, workArea.width).dp,
            height = minOf(800, workArea.height).dp,
        )
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
            // AWT 的窗口尺寸和 screen bounds 都是逻辑点（HiDPI 下由系统自行缩放），
            // 所以这里直接用点值，不要再乘屏幕缩放。
            LaunchedEffect(Unit) {
                val gc = window.graphicsConfiguration
                val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
                val workWidthUnits = gc.bounds.width - insets.left - insets.right
                val workHeightUnits = gc.bounds.height - insets.top - insets.bottom
                val workWidth = workWidthUnits.dp
                val workHeight = workHeightUnits.dp
                window.minimumSize = Dimension(
                    minOf(720, workWidthUnits),
                    minOf(480, workHeightUnits),
                )
                if (state.size.width > workWidth || state.size.height > workHeight) {
                    state.size = DpSize(
                        minOf(state.size.width, workWidth),
                        minOf(state.size.height, workHeight),
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
