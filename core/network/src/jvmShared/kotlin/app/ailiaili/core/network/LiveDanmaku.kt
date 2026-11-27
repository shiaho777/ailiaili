package app.ailiaili.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

data class LiveDanmakuEndpoint(val host: String, val port: Int)

data class LiveDanmakuInfo(val endpoints: List<LiveDanmakuEndpoint>, val token: String)

/**
 * B 站直播弹幕 WebSocket。
 *
 * 协议：16 字节头（总长/头长/版本/操作码/序号）+ JSON body。
 * 请求 `protover=2`，服务端用 zlib 压缩 op=5 的通知包；这里用 JDK 自带的
 * Inflater 解压，避免为了 brotli 再引一个原生库。
 */
internal class LiveDanmakuConnection(
    private val endpoints: List<LiveDanmakuEndpoint>,
    private val token: String,
    private val roomId: Long,
    private val uid: Long,
    private val cookie: String,
) {
    fun messages(): Flow<LiveDanmaku> = channelFlow {
        val selected = CompletableDeferred<Int>()
        val winner = AtomicInteger(-1)
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .apply {
                app.ailiaili.core.model.parseProxyAddress(app.ailiaili.core.model.effectiveProxy())?.let { addr ->
                    proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(addr.host, addr.port)))
                }
            }
            .build()
        try {
            val attempts = endpoints.take(4).mapIndexed { index, endpoint ->
                launch {
                    delay(index * 600L)
                    try {
                        endpointMessages(client, endpoint) {
                            winner.compareAndSet(-1, index).also { accepted ->
                                if (accepted) selected.complete(index)
                            }
                        }.collect { send(it) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (winner.get() == index) throw failure
                    }
                }
            }
            val cancelAlternatives = launch {
                val index = selected.await()
                attempts.forEachIndexed { other, job -> if (index != other) job.cancel() }
            }
            try {
                attempts.joinAll()
                if (winner.get() < 0) throw IOException("直播弹幕服务器连接失败")
            } finally {
                cancelAlternatives.cancel()
            }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun endpointMessages(
        client: OkHttpClient,
        endpoint: LiveDanmakuEndpoint,
        select: () -> Boolean,
    ): Flow<LiveDanmaku> = callbackFlow {
        val request = Request.Builder()
            .url(HttpUrl.Builder().scheme("https").host(endpoint.host).port(endpoint.port).addPathSegment("sub").build())
            .header("User-Agent", app.ailiaili.core.model.effectiveUserAgent())
            .header("Origin", "https://live.bilibili.com")
            .header("Referer", "https://live.bilibili.com/$roomId")
            .apply { if (cookie.isNotBlank() && endpoint.host.endsWith(".bilibili.com", true)) header("Cookie", cookie) }
            .build()
        val authenticated = CompletableDeferred<Boolean>()
        val lastMessageAt = AtomicLong(System.currentTimeMillis())
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            private var accepted = false

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val auth = JSONObject()
                    .put("uid", uid)
                    .put("roomid", roomId)
                    .put("protover", 2)
                    .put("platform", "web")
                    .put("type", 2)
                    .put("key", token)
                    .toString()
                if (!webSocket.send(ByteString.of(*packet(7, auth.toByteArray())))) close()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastMessageAt.set(System.currentTimeMillis())
                val found = ArrayList<LiveDanmaku>(4)
                runCatching {
                    parseFrames(bytes.toByteArray(), found) { code ->
                        if (!authenticated.isCompleted) {
                            accepted = code == 0 && select()
                            authenticated.complete(accepted)
                        }
                    }
                }
                if (accepted) found.forEach { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        val heartbeat = launch {
            when (withTimeoutOrNull(6_000) { authenticated.await() }) {
                null -> close(IOException("直播弹幕认证超时"))
                false -> close()
                true -> {
                    val frame = ByteString.of(*packet(2, "[object Object]".toByteArray()))
                    while (isActive) {
                        if (!socket.send(frame) || System.currentTimeMillis() - lastMessageAt.get() > 150_000) {
                            close(IOException("直播弹幕连接已断开，正在重连"))
                            break
                        }
                        delay(30_000)
                    }
                }
            }
        }
        awaitClose {
            heartbeat.cancel()
            socket.cancel()
        }
    }

    private fun parseFrames(data: ByteArray, out: MutableList<LiveDanmaku>, auth: (Int) -> Unit) {
        var offset = 0
        while (offset + 16 <= data.size) {
            val head = ByteBuffer.wrap(data, offset, 16).order(ByteOrder.BIG_ENDIAN)
            val total = head.int
            val headerLen = head.short.toInt()
            val version = head.short.toInt()
            val op = head.int
            head.int // sequence
            // total/headerLen 必须有 16 字节协议头的下界：total=0 的畸形帧会让
            // offset 永不前进（读线程死循环），headerLen>total 会构造非法区间。
            if (total < 16 || headerLen < 16 || total < headerLen || offset + total > data.size) break
            val body = data.copyOfRange(offset + headerLen, offset + total)
            when (version) {
                2 -> {
                    val inflated = runCatching { inflate(body) }.getOrNull()
                    if (inflated != null) parseFrames(inflated, out, auth)
                }
                0, 1 -> when (op) {
                    5 -> parseJson(body, out)
                    8 -> auth(runCatching { JSONObject(body.decodeToString()).optInt("code", -1) }.getOrDefault(-1))
                }
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
