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

/** 选择下载保存目录。桌面端弹目录选择器；Android 暂不支持，返回 null。 */
expect suspend fun chooseDirectory(): String?

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

expect fun traySupported(): Boolean

expect fun hideAppWindow()
