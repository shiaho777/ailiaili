package app.ailiaili.core.player

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * 桌面全局媒体键（播放/暂停、上一曲、下一曲、停止）。
 *
 * 平台边界（诚实标注）：
 * - Windows：RegisterHotKey 注册虚拟媒体键，专用线程跑消息泵。零新依赖
 *   （JNA 已在 vlcj 依赖树里）。个别应用抢占媒体键时注册可能失败，静默降级。
 * - macOS / Linux：不做。macOS 需 objc 桥接 MPRemoteCommandCenter，Linux 需
 *   D-Bus MPRIS，均超出本轮范围；窗口内快捷键（空格/方向键）仍然可用。
 *
 * 生命周期：进程级单例，随 AppGraph.init 启动；注销在进程退出时由系统回收。
 * 回调目标由播放器登记（当前引擎），离开播放页注销，媒体键不再误触旧引擎。
 */
object DesktopMediaKeys {
    fun interface Handler {
        /** action: 0=播放暂停切换 1=下一条 2=上一条 3=停止 */
        fun onMediaKey(action: Int)
    }

    @Volatile
    var handler: Handler? = null

    @Volatile
    private var started = false

    private const val ID_PLAY_PAUSE = 0xB001
    private const val ID_NEXT = 0xB002
    private const val ID_PREV = 0xB003
    private const val ID_STOP = 0xB004

    fun start() {
        if (started) return
        val os = System.getProperty("os.name").orEmpty()
        if (!os.startsWith("Windows", ignoreCase = true)) return
        started = true
        val thread = Thread({ runMessagePump() }, "ailiaili-media-keys")
        thread.isDaemon = true
        thread.start()
    }

    private fun runMessagePump() {
        runCatching {
            val user32 = User32.INSTANCE
            val module = Kernel32.INSTANCE.GetModuleHandle(null)
            // MOD_NOREPEAT(0x4000)：按住不连发。注册失败（被其他应用占用）就跳过该键。
            // WinUser 未映射媒体键常量，直接用 Windows 虚拟键码：
            // 0xB3=播放暂停 0xB0=下一曲 0xB1=上一曲 0xB2=停止。
            val keys = listOf(
                ID_PLAY_PAUSE to 0xB3,
                ID_NEXT to 0xB0,
                ID_PREV to 0xB1,
                ID_STOP to 0xB2,
            )
            val registered = mutableListOf<Int>()
            for ((id, vk) in keys) {
                if (user32.RegisterHotKey(null, id, 0x4000, vk)) registered += id
            }
            if (registered.isEmpty()) return@runCatching
            val msg = WinUser.MSG()
            while (true) {
                val result = user32.GetMessage(msg, null, 0, 0)
                if (result <= 0) break
                if (msg.message == WinUser.WM_HOTKEY) {
                    val action = when (msg.wParam.toInt()) {
                        ID_PLAY_PAUSE -> 0
                        ID_NEXT -> 1
                        ID_PREV -> 2
                        ID_STOP -> 3
                        else -> -1
                    }
                    if (action >= 0) {
                        runCatching { handler?.onMediaKey(action) }
                    }
                }
                user32.TranslateMessage(msg)
                user32.DispatchMessage(msg)
            }
            registered.forEach { user32.UnregisterHotKey(null, it) }
        }
    }
}