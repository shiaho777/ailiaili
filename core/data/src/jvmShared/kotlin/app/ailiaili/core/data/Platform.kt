package app.ailiaili.core.data

enum class HostKind { Android, Desktop }

expect fun hostKind(): HostKind

fun isDesktopHost(): Boolean = hostKind() == HostKind.Desktop

expect fun canControlBrightness(): Boolean

expect fun canFollowSystemAccent(): Boolean

/** 各系统自己的文件、窗口和分享。界面代码只调这些函数。 */
expect fun appStorageDir(): AppFile

expect fun imageCacheDirs(): List<AppFile>

expect fun openUrl(url: String)

/** 返回要给用户看的一句说明。空字符串表示系统自己已经弹出了分享界面。 */
expect fun shareText(title: String, text: String): String

expect fun saveImageBytes(bytes: ByteArray, displayName: String): String

/**
 * 选择下载保存目录。桌面端弹目录选择器返回绝对路径；
 * Android 走系统 SAF 目录选择，返回 content:// tree Uri 字符串（持久授权）。
 * 取消返回 null。
 */
expect suspend fun chooseDirectory(): String?

/**
 * 把应用缓存里的视频文件复制到用户选定的目录。
 * Android：dest 是 SAF tree Uri，逐个复制并返回成功个数；
 * 桌面端下载直接写目标目录，无需搬运，恒返回 0。
 */
expect suspend fun exportCacheFiles(files: List<String>, dest: String): Int

/** 选一张本地图片（私信发图用）。返回文件名与字节；取消返回 null。 */
expect suspend fun pickImage(): PickedImage?

data class PickedImage(val name: String, val bytes: ByteArray)

/** 系统里可选的界面字体（ttf/otf/ttc 文件路径）。Android 返回空列表，走导入。 */
expect fun systemFontFiles(): List<String>

/** 选一个字体文件导入。返回文件名与字节；取消返回 null。 */
expect suspend fun pickFontFile(): PickedImage?

/**
 * 设置导出：桌面弹保存对话框把文本写成文件；Android 写入应用存储的固定
 * 备份文件并返回路径（配合分享发出）。失败返回 null。
 */
expect suspend fun saveTextFile(suggestedName: String, text: String): String?

/**
 * 设置导入：桌面弹打开对话框读文本文件；Android 读应用存储的固定备份
 * 文件（用户先把它放到该位置）。无文件/取消返回 null。
 */
expect suspend fun readTextFile(): String?

/** Android 设置备份的固定文件名；桌面端不使用。 */
const val SETTINGS_BACKUP_NAME = "ailiaili-settings.json"

/** 应用字体目录：导入/复制的字体统一放这里，重启后按路径加载。 */
fun appFontDir(): AppFile = AppFile(appStorageDir(), "fonts").also { it.mkdirs() }

expect fun exitApp()

expect fun windowBrightness(): Float

expect fun setWindowBrightness(value: Float)

expect fun setFullscreen(on: Boolean)

/**
 * 画中画（Android 8+ 且设备支持）。桌面恒 false。
 * inPip 为 true 时播放页不销毁引擎、返回键不弹页，保证小窗继续播。
 */
expect fun pipSupported(): Boolean

expect fun inPip(): Boolean

/** 主动进入画中画；不支持或失败返回 false。 */
expect fun enterPip(): Boolean

/** 播放页登记播放状态查询（Home 键自动进 PiP 用）；传 null 注销。桌面空实现。 */
expect fun registerPipPlayback(check: (() -> Boolean)?)

/**
 * 发一条系统通知（开播提醒用）。桌面走托盘气泡钩子（Main 安装）；
 * Android 走 NotificationManager。返回是否发出。
 */
expect fun notifyUser(title: String, message: String): Boolean

expect fun traySupported(): Boolean

expect fun hideAppWindow()
