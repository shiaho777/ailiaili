package app.ailiaili.core.data

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DesktopHost {
    var exit: (() -> Unit)? = null
    var fullscreen: ((Boolean) -> Unit)? = null
    var showWindow: (() -> Unit)? = null
    var hideWindow: (() -> Unit)? = null
}

actual fun hostKind(): HostKind = HostKind.Desktop

actual fun canControlBrightness(): Boolean = false

actual fun canFollowSystemAccent(): Boolean = false

actual fun appStorageDir(): AppFile = AppFile(File(System.getProperty("user.home"), ".ailiaili").also { it.mkdirs() }.absolutePath)

actual fun imageCacheDirs(): List<AppFile> {
    val cache = AppFile(appStorageDir(), "cache")
    return listOf(AppFile(cache, "image_cache"), AppFile(cache, "coil"))
}

actual fun openUrl(url: String) {
    Desktop.getDesktop().browse(URI(url))
}

actual fun shareText(title: String, text: String): String {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
    val page = text.lineSequence().firstOrNull { it.startsWith("http") }
    if (page != null && Desktop.isDesktopSupported()) {
        runCatching { Desktop.getDesktop().browse(URI(page.trim())) }
    }
    return "链接已复制"
}

actual suspend fun chooseDirectory(): String? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择下载保存位置"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.absolutePath
    } else {
        null
    }
}

actual suspend fun saveTextFile(suggestedName: String, text: String): String? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "导出设置"
        selectedFile = File(suggestedName)
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    val file = chooser.selectedFile ?: return@withContext null
    runCatching {
        file.writeText(text)
        file.absolutePath
    }.getOrNull()
}

actual suspend fun readTextFile(): String? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "导入设置"
        fileSelectionMode = JFileChooser.FILES_ONLY
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    val file = chooser.selectedFile ?: return@withContext null
    runCatching { file.readText() }.getOrNull()
}

actual fun systemFontFiles(): List<String> {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val roots = when {
        os.contains("mac") -> listOf(
            "/System/Library/Fonts",
            "/Library/Fonts",
            File(System.getProperty("user.home"), "Library/Fonts").absolutePath,
        )
        os.contains("win") -> listOf("C:/Windows/Fonts")
        else -> listOf(
            "/usr/share/fonts",
            "/usr/local/share/fonts",
            File(System.getProperty("user.home"), ".local/share/fonts").absolutePath,
        )
    }
    return roots.asSequence()
        .map { File(it) }
        .filter { it.isDirectory }
        .flatMap { it.walkTopDown().maxDepth(4) }
        .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .map { it.absolutePath }
        .distinct()
        .sortedBy { File(it).name.lowercase() }
        .take(400)
        .toList()
}

actual suspend fun pickFontFile(): PickedImage? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "导入字体文件"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = object : javax.swing.filechooser.FileFilter() {
            override fun accept(f: File): Boolean =
                f.isDirectory || f.extension.lowercase() in setOf("ttf", "otf")
            override fun getDescription(): String = "字体（ttf / otf）"
        }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    val file = chooser.selectedFile ?: return@withContext null
    runCatching { PickedImage(file.name, file.readBytes()) }.getOrNull()
}

actual suspend fun pickImage(): PickedImage? = withContext(Dispatchers.Main) {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择要发送的图片"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = object : javax.swing.filechooser.FileFilter() {
            override fun accept(f: File): Boolean =
                f.isDirectory || f.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")
            override fun getDescription(): String = "图片（jpg / png / gif / webp）"
        }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
    val file = chooser.selectedFile ?: return@withContext null
    runCatching { PickedImage(file.name, file.readBytes()) }.getOrNull()
}

actual fun saveImageBytes(bytes: ByteArray, displayName: String): String {
    val dir = File(System.getProperty("user.home"), "Pictures/ailiaili").also { it.mkdirs() }
    val safe = displayName.ifBlank { "avatar" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    // 按真实字节格式定扩展名：播放器截图是 PNG，不能一律标成 .jpg。
    val isPng = bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
    val file = File(dir, "$safe.${if (isPng) "png" else "jpg"}")
    file.writeBytes(bytes)
    return "已保存到 ${file.absolutePath}"
}

actual fun exitApp() {
    DesktopHost.exit?.invoke()
}

actual fun windowBrightness(): Float = 0.5f

actual fun setWindowBrightness(value: Float) = Unit

actual fun setFullscreen(on: Boolean) {
    DesktopHost.fullscreen?.invoke(on)
}

actual fun traySupported(): Boolean = java.awt.SystemTray.isSupported()

actual fun hideAppWindow() {
    DesktopHost.hideWindow?.invoke()
}
