package app.ailiaili.core.data

import android.app.Activity
import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File

object AndroidHost {
    lateinit var context: Application
    var activity: Activity? = null
    /** Activity 在 onCreate 注册的系统选图回调；由 MainActivity 安装。 */
    var imagePicker: (suspend () -> PickedImage?)? = null
    /** 系统文件选择器选字体文件；由 MainActivity 安装。 */
    var fontPicker: (suspend () -> PickedImage?)? = null
    /** SAF 目录选择（下载导出目录）；由 MainActivity 安装，返回 tree Uri 字符串。 */
    var directoryPicker: (suspend () -> String?)? = null
    /** 画中画模式标记；由 MainActivity 的模式回调维护。 */
    @Volatile
    var pipActive: Boolean = false
    /** 播放页登记「当前是否正在播放」；Home 键自动进 PiP 前查询。 */
    var pipShouldEnter: (() -> Boolean)? = null

    fun install(app: Application) {
        context = app
    }
}

actual fun pipSupported(): Boolean =
    Build.VERSION.SDK_INT >= 26 &&
        AndroidHost.context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

actual fun inPip(): Boolean = AndroidHost.pipActive

actual fun enterPip(): Boolean {
    if (!pipSupported()) return false
    val activity = AndroidHost.activity ?: return false
    return runCatching {
        activity.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
    }.getOrDefault(false)
}

actual fun registerPipPlayback(check: (() -> Boolean)?) {
    AndroidHost.pipShouldEnter = check
}

actual fun hostKind(): HostKind = HostKind.Android

actual fun canControlBrightness(): Boolean = true

actual fun canFollowSystemAccent(): Boolean = Build.VERSION.SDK_INT >= 31

actual fun appStorageDir(): AppFile = AppFile(File(AndroidHost.context.filesDir, "ailiaili").also { it.mkdirs() }.absolutePath)

actual fun imageCacheDirs(): List<AppFile> {
    val cache = AndroidHost.context.cacheDir
    return listOf(
        AppFile(File(cache, "image_cache").absolutePath),
        AppFile(File(cache, "coil").absolutePath),
        AppFile(File(cache, "coil3_disk_cache").absolutePath),
    )
}

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val host = AndroidHost.activity
    if (host != null) host.startActivity(intent) else AndroidHost.context.startActivity(intent)
}

actual fun shareText(title: String, text: String): String {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val host = AndroidHost.activity
    if (host != null) host.startActivity(chooser) else AndroidHost.context.startActivity(chooser)
    return ""
}

actual suspend fun chooseDirectory(): String? = AndroidHost.directoryPicker?.invoke()

actual suspend fun exportCacheFiles(files: List<String>, dest: String): Int {
    if (files.isEmpty() || !dest.startsWith("content://")) return 0
    return kotlinx.coroutines.withContext(app.ailiaili.core.model.ioDispatcher) {
        runCatching {
            val resolver = AndroidHost.context.contentResolver
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                AndroidHost.context,
                Uri.parse(dest),
            ) ?: return@runCatching 0
            var copied = 0
            for (path in files) {
                val src = File(path)
                if (!src.isFile || src.length() <= 0) continue
                // 同名旧导出先删再建，避免系统自动改名成 "video (1).mp4"。
                tree.findFile(src.name)?.delete()
                val target = tree.createFile("video/mp4", src.name) ?: continue
                val ok = runCatching {
                    resolver.openOutputStream(target.uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out, 1 shl 20) }
                    } != null
                }.getOrDefault(false)
                if (ok) copied++ else target.delete()
            }
            copied
        }.getOrDefault(0)
    }
}

actual suspend fun pickImage(): PickedImage? = AndroidHost.imagePicker?.invoke()

actual fun systemFontFiles(): List<String> = emptyList()

actual suspend fun pickFontFile(): PickedImage? = AndroidHost.fontPicker?.invoke()

actual suspend fun saveTextFile(suggestedName: String, text: String): String? {
    // 安卓没有通用保存对话框：写到应用存储的固定备份文件，再拉起分享，
    // 用户可发到网盘/聊天/文件应用保存。
    val file = File(appStorageDir().absolutePath, SETTINGS_BACKUP_NAME)
    val ok = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }.isSuccess
    if (!ok) return null
    val host = AndroidHost.activity ?: return file.absolutePath
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            host,
            "${host.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        host.startActivity(Intent.createChooser(send, "导出设置").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return file.absolutePath
}

actual suspend fun readTextFile(): String? {
    val file = File(appStorageDir().absolutePath, SETTINGS_BACKUP_NAME)
    if (!file.isFile) return null
    return runCatching { file.readText() }.getOrNull()
}

actual fun saveImageBytes(bytes: ByteArray, displayName: String): String {
    val resolver = AndroidHost.context.contentResolver
    // 按真实字节格式定扩展名/MIME：播放器截图是 PNG，不能一律标成 .jpg。
    val isPng = bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
    val ext = if (isPng) "png" else "jpg"
    val mime = if (isPng) "image/png" else "image/jpeg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.$ext")
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ailiaili")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return "没有写入相册的权限"
    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return "没有写入相册的权限"
    if (Build.VERSION.SDK_INT >= 29) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    return "图片已保存到相册"
}

actual fun exitApp() {
    AndroidHost.activity?.finish()
}

actual fun windowBrightness(): Float {
    val activity = AndroidHost.activity ?: return 0.5f
    val current = activity.window.attributes.screenBrightness
    if (current >= 0f) return current
    val system = runCatching {
        Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrNull()
    return system?.coerceIn(0.02f, 1f) ?: 0.5f
}

actual fun setWindowBrightness(value: Float) {
    val window = AndroidHost.activity?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = value
    window.attributes = attrs
}

actual fun setFullscreen(on: Boolean) {
    AndroidHost.activity?.requestedOrientation = if (on) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

actual fun notifyUser(title: String, message: String): Boolean = runCatching {
    val context = AndroidHost.context
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
        as android.app.NotificationManager
    val channelId = "ailiaili-live"
    if (Build.VERSION.SDK_INT >= 26) {
        val channel = android.app.NotificationChannel(
            channelId,
            "开播提醒",
            android.app.NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
    // Android 13+ 未授权时 notify 静默无效；权限申请在 MainActivity 已做。
    val intent = android.content.Intent(context, Class.forName("app.ailiaili.MainActivity"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val pending = android.app.PendingIntent.getActivity(
        context,
        0,
        intent,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = android.app.Notification.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    true
}.getOrDefault(false)

actual fun traySupported(): Boolean = false

actual fun hideAppWindow() = Unit
