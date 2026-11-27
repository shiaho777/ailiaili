package app.ailiaili

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DownloadNetwork
import app.ailiaili.core.data.DownloadPhase
import app.ailiaili.core.data.PickedImage
import app.ailiaili.core.designsystem.BiliTheme
import app.ailiaili.core.designsystem.InstallBiliImages
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var showDownloads by mutableStateOf(false)
    private var pendingPick: CancellableContinuation<PickedImage?>? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val continuation = pendingPick
        pendingPick = null
        if (continuation == null) return@registerForActivityResult
        if (uri == null) {
            continuation.resume(null)
            return@registerForActivityResult
        }
        val picked = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = runCatching {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            }.getOrNull() ?: "image.jpg"
            PickedImage(name, bytes)
        }.getOrNull()
        continuation.resume(picked)
    }

    private var pendingFont: CancellableContinuation<PickedImage?>? = null

    private val pickFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val continuation = pendingFont
        pendingFont = null
        if (continuation == null) return@registerForActivityResult
        if (uri == null) {
            continuation.resume(null)
            return@registerForActivityResult
        }
        val picked = runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = runCatching {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            }.getOrNull() ?: "font.ttf"
            PickedImage(name, bytes)
        }.getOrNull()
        continuation.resume(picked)
    }

    private var pendingDir: CancellableContinuation<String?>? = null

    private val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val continuation = pendingDir
        pendingDir = null
        if (continuation == null) return@registerForActivityResult
        if (uri == null) {
            continuation.resume(null)
            return@registerForActivityResult
        }
        // 持久授权：重启后仍能往这个目录写导出文件。
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        continuation.resume(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDownloads = intent.action == DownloadService.SHOW_DOWNLOADS
        if (showDownloads) intent.action = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(AppGraph.downloads.tasks, AppGraph.downloads.network, AppGraph.repo.settings) { tasks, network, settings ->
                    settings.backgroundDownloads && tasks.any {
                        it.phase.isWorking || it.phase == DownloadPhase.Queued && network == DownloadNetwork.Available
                    }
                }.distinctUntilChanged().collect { active ->
                    if (active) DownloadService.start(this@MainActivity)
                }
            }
        }
        AndroidHost.activity = this
        AndroidHost.directoryPicker = {
            suspendCancellableCoroutine { continuation ->
                pendingDir = continuation
                continuation.invokeOnCancellation { pendingDir = null }
                runCatching { pickDirectory.launch(null) }
                    .onFailure {
                        pendingDir = null
                        continuation.resume(null)
                    }
            }
        }
        AndroidHost.fontPicker = {
            suspendCancellableCoroutine { continuation ->
                pendingFont = continuation
                continuation.invokeOnCancellation { pendingFont = null }
                runCatching {
                    pickFont.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
                }.onFailure {
                    pendingFont = null
                    continuation.resume(null)
                }
            }
        }
        AndroidHost.imagePicker = {
            suspendCancellableCoroutine { continuation ->
                pendingPick = continuation
                continuation.invokeOnCancellation { pendingPick = null }
                runCatching {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.onFailure {
                    pendingPick = null
                    continuation.resume(null)
                }
            }
        }
        // Android 13+ 媒体通知需要运行时授权；拒绝也不影响播放本身。
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        enableEdgeToEdge()
        setContent {
            InstallBiliImages()
            val settings by AppGraph.repo.settings.collectAsState()
            BiliTheme(settings.theme, settings.accentHex, settings.accentFollowSystem, settings.fontPath, settings.reduceMotion) {
                AppRoot(openDownloads = showDownloads, onDownloadsOpened = { showDownloads = false })
                UpdatePrompt(settings.autoCheckUpdate, settings.skippedVersion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidHost.activity = this
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == DownloadService.SHOW_DOWNLOADS) showDownloads = true
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 播放中按 Home 自动进小窗。是否正在播由播放页登记的钩子回答；
        // 没在播就不进 PiP，避免把普通页面缩成小窗。
        if (AndroidHost.pipActive) return
        if (AndroidHost.pipShouldEnter?.invoke() == true) {
            runCatching {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        AndroidHost.pipActive = isInPictureInPictureMode
    }

    override fun onDestroy() {
        // 静态持有必须随销毁清空：否则进程存活期间泄漏整个 Activity，
        // openUrl/亮度调节还可能作用于已销毁的窗口。只清自己安装的引用。
        if (AndroidHost.activity === this) AndroidHost.activity = null
        AndroidHost.imagePicker = null
        AndroidHost.fontPicker = null
        AndroidHost.directoryPicker = null
        pendingPick?.cancel()
        pendingPick = null
        pendingFont?.cancel()
        pendingFont = null
        pendingDir?.cancel()
        pendingDir = null
        super.onDestroy()
    }
}
