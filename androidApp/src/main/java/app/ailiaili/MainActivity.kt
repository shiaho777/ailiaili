package app.ailiaili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.PickedImage
import app.ailiaili.core.designsystem.BiliTheme
import app.ailiaili.core.designsystem.InstallBiliImages
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidHost.activity = this
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
        enableEdgeToEdge()
        setContent {
            InstallBiliImages()
            val settings by AppGraph.repo.settings.collectAsState()
            BiliTheme(settings.theme, settings.accentHex, settings.accentFollowSystem, settings.fontPath) {
                AppRoot()
                UpdatePrompt(settings.autoCheckUpdate, settings.skippedVersion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidHost.activity = this
    }

    override fun onDestroy() {
        // 静态持有必须随销毁清空：否则进程存活期间泄漏整个 Activity，
        // openUrl/亮度调节还可能作用于已销毁的窗口。只清自己安装的引用。
        if (AndroidHost.activity === this) AndroidHost.activity = null
        AndroidHost.imagePicker = null
        AndroidHost.fontPicker = null
        pendingPick?.cancel()
        pendingPick = null
        pendingFont?.cancel()
        pendingFont = null
        super.onDestroy()
    }
}
