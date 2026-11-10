package app.ailiaili.core.network

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import app.ailiaili.core.model.USER_AGENT
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

/** 一条直播间弹幕。 */
data class LiveDanmaku(
    val text: String,
    val user: String = "",
    val uid: Long = 0,
    val color: Int = 0xFFFFFF,
    val mode: Int = 1,
    val admin: Boolean = false,
    val guard: Int = 0,
    val medalName: String = "",
    val medalLevel: Int = 0,
)

data class LiveDanmakuInfo(
    val host: String,
    val port: Int,
    val token: String,
)

/**
 * B 站直播弹幕 WebSocket。
 *
 * 协议：16 字节头（总长/头长/版本/操作码/序号）+ JSON body。
 * 请求 `protover=2`，服务端用 zlib 压缩 op=5 的通知包；这里用 JDK 自带的
 * Inflater 解压，避免为了 brotli 再引一个原生库。
 */
internal class LiveDanmakuConnection(
    private val host: String,
    private val port: Int,
    private val token: String,
    private val roomId: Long,
    private val uid: Long,
    private val cookie: String,
) {
    fun messages(): Flow<LiveDanmaku> = callbackFlow {
        val scope = this
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("wss://$host:$port/sub")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://live.bilibili.com")
            .header("Referer", "https://live.bilibili.com/$roomId")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .build()
        var heartbeat: Job? = null
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val auth = JSONObject()
                    .put("uid", uid)
                    .put("roomid", roomId)
                    .put("protover", 2)
                    .put("platform", "web")
                    .put("type", 2)
                    .put("key", token)
                    .toString()
                webSocket.send(ByteString.of(*packet(7, auth.toByteArray())))
                heartbeat = scope.launch {
                    while (isActive) {
                        delay(30_000)
                        webSocket.send(ByteString.of(*packet(2, "[object Object]".toByteArray())))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val found = ArrayList<LiveDanmaku>(4)
                runCatching { parseFrames(bytes.toByteArray(), found) }
                found.forEach { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose {
            heartbeat?.cancel()
            runCatching { socket.cancel() }
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun parseFrames(data: ByteArray, out: MutableList<LiveDanmaku>) {
        var offset = 0
        while (offset + 16 <= data.size) {
            val head = ByteBuffer.wrap(data, offset, 16).order(ByteOrder.BIG_ENDIAN)
            val total = head.int
            val headerLen = head.short.toInt()
            val version = head.short.toInt()
            head.int // op
            head.int // sequence
            if (total < headerLen || offset + total > data.size) break
            val body = data.copyOfRange(offset + headerLen, offset + total)
            when (version) {
                2 -> {
                    val inflated = runCatching { inflate(body) }.getOrNull()
                    if (inflated != null) parseFrames(inflated, out)
                }
                0, 1 -> parseJson(body, out)
            }
            offset += total
        }
    }

    private fun parseJson(body: ByteArray, out: MutableList<LiveDanmaku>) {
        val root = runCatching { JSONObject(body.decodeToString()) }.getOrNull() ?: return
        val cmd = root.optString("cmd")
        if (!cmd.startsWith("DANMU_MSG")) return
        val info = root.optJSONArray("info") ?: return
        val text = info.optString(1).trim()
        if (text.isEmpty()) return
        val user = info.optJSONArray(2)
        val meta = info.optJSONArray(0)
        val medal = info.optJSONArray(3)
        out += LiveDanmaku(
            text = text,
            user = user?.optString(1).orEmpty(),
            uid = user?.optString(0)?.toLongOrNull() ?: 0L,
            color = meta?.optInt(3) ?: 0xFFFFFF,
            mode = meta?.optInt(1) ?: 1,
            admin = user?.optInt(2) == 1,
            guard = info.optInt(7).takeIf { it in 1..3 } ?: 0,
            medalName = medal?.optString(1).orEmpty(),
            medalLevel = medal?.optInt(0) ?: 0,
        )
    }

    private fun packet(op: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(16 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(16 + payload.size)
        buffer.putShort(16)
        buffer.putShort(1)
        buffer.putInt(op)
        buffer.putInt(1)
        buffer.put(payload)
        return buffer.array()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
                continue
            }
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}
