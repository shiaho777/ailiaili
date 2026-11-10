package app.ailiaili.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.BiliEmptyState
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
                    onSuccess()
                    return@LaunchedEffect
                }
                QrStatus.Expired -> {
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
        topBar = { BiliBackHeader("扫码登录", onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(
                "登录哔哩哔哩",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "扫码后可以同步历史、收藏、关注和硬币",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            val qr = bitmap
            if (qr != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(220.dp),
                    )
                }
            } else if (failed) {
                BiliEmptyState(
                    title = "二维码暂时不可用",
                    message = message,
                    icon = Icons.Filled.QrCodeScanner,
                )
            } else {
                Box(
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                message,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { refresh += 1 }) { Text(if (failed) "重新获取" else "刷新二维码") }
        }
    }
}

internal expect fun qrBitmap(content: String): ImageBitmap
