package app.ailiaili.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import app.ailiaili.core.designsystem.BiliPrimaryButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.aliAmbient
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.appIconPainter
import app.ailiaili.core.model.QrStatus
import kotlinx.coroutines.delay

@Composable
fun LoginRoute(onBack: () -> Unit, onSuccess: () -> Unit) {
    var message by remember { mutableStateOf("正在获取二维码…") }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        message = "正在获取二维码…"
        bitmap = null
        failed = false
        val ticket = AppGraph.repo.qrGenerate().getOrElse {
            message = it.message ?: "二维码获取失败"
            failed = true
            return@LaunchedEffect
        }
        bitmap = qrBitmap(ticket.url)
        message = "用哔哩哔哩 App 扫码登录"
        while (true) {
            delay(2000)
            when (AppGraph.repo.qrPoll(ticket.key).getOrNull()) {
                QrStatus.Scanned -> message = "已扫码，请在手机上确认"
                QrStatus.Success -> {
                    message = "登录成功"
                    // 归档到多账号列表（昵称/头像由我的页加载后补齐）。
                    AppGraph.repo.rememberCurrentAccount()
                    onSuccess()
                    return@LaunchedEffect
                }
                QrStatus.Expired -> {
                    bitmap = null
                    message = "二维码已过期，请刷新"
                    failed = true
                    return@LaunchedEffect
                }
                QrStatus.Failed -> message = "登录状态查询失败"
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            AliStagePageHeader("扫码登录", onBack = onBack) {
                AliStageIconButton(
                    Icons.Filled.Refresh, "刷新二维码", { refresh++ },
                    enabled = bitmap != null || failed,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).aliAmbient().verticalScroll(rememberScrollState()).padding(14.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AliStagePanel(Modifier.widthIn(max = 448.dp).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(appIconPainter(), null, Modifier.size(48.dp))
                    Text(
                        "登录哔哩哔哩",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val qr = bitmap
                    val shape = MaterialTheme.shapes.large
                    Box(
                        Modifier.size(228.dp).clip(shape)
                            .background(if (qr == null) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            qr != null -> Image(qr, "登录二维码", Modifier.fillMaxSize())
                            failed -> Icon(
                                Icons.Filled.QrCodeScanner, null, Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                    Text(
                        message,
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (failed) BiliPrimaryButton(onClick = { refresh++ }) { Text("重新获取") }
                }
            }
        }
    }
}

internal expect fun qrBitmap(content: String): ImageBitmap
