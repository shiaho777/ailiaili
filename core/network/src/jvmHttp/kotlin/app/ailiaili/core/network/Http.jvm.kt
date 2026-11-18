package app.ailiaili.core.network

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

actual class HttpEngine actual constructor(
    connectTimeoutMs: Long,
    readTimeoutMs: Long,
) {
    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(readTimeoutMs + connectTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        // 三个引擎（api/default/probe）共享连接池与调度器：
        // 同一 host 的 TLS 握手结果直接复用，图片加载不再和 API 抢各自的池子。
        .connectionPool(sharedPool)
        .dispatcher(sharedDispatcher)
        .build()

    /**
     * HTTP 代理热切换：设置变化后下一次请求即用新代理，无需重启。
     * 解析失败（用户填了非法地址）按直连兜底，不让整个网络层瘫痪。
     * newBuilder 复用连接池与调度器；代理不同的连接在池里按地址区分。
     */
    @Volatile private var proxyKey: String = app.ailiaili.core.model.effectiveProxy()
    @Volatile private var activeClient: OkHttpClient = buildProxied(baseClient, proxyKey)
    @Volatile private var activeDownload: OkHttpClient =
        activeClient.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()

    private fun buildProxied(base: OkHttpClient, raw: String): OkHttpClient {
        val addr = runCatching { app.ailiaili.core.model.parseProxyAddress(raw) }.getOrNull()
            ?: return base
        return base.newBuilder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(addr.host, addr.port)))
            .build()
    }

    private fun client(): OkHttpClient {
        val key = app.ailiaili.core.model.effectiveProxy()
        if (key != proxyKey) {
            synchronized(this) {
                if (key != proxyKey) {
                    proxyKey = key
                    activeClient = buildProxied(baseClient, key)
                    activeDownload = activeClient.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
                }
            }
        }
        return activeClient
    }

    private companion object {
        val sharedPool = okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES)
        val sharedDispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 48
            maxRequestsPerHost = 12
        }
    }

    /** 大文件下载不能受 API 的 callTimeout 限制。 */
    private fun downloadClient(): OkHttpClient {
        client()
        return activeDownload
    }



    actual fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>?,
        range: String?,
        maxBytes: Int?,
    ): HttpResponse {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (range != null) builder.header("Range", range)
        if (form != null) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            builder.post(body)
        } else {
            builder.method(method, null)
        }
        client().newCall(builder.build()).execute().use { resp ->
            return readResponse(resp, maxBytes)
        }
    }

    actual fun postJson(
        url: String,
        json: String,
        headers: Map<String, String>,
    ): HttpResponse {
        val builder = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
        headers.forEach { (key, value) -> builder.header(key, value) }
        client().newCall(builder.build()).execute().use { resp ->
            return readResponse(resp, null)
        }
    }

    actual fun postMultipart(
        url: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        fileBytes: ByteArray,
        fileMime: String,
        headers: Map<String, String>,
    ): HttpResponse {
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .apply {
                fields.forEach { (k, v) -> addFormDataPart(k, v) }
                addFormDataPart(
                    fileField,
                    fileName,
                    fileBytes.toRequestBody(fileMime.toMediaType()),
                )
            }
            .build()
        val builder = Request.Builder().url(url).post(body)
        headers.forEach { (key, value) -> builder.header(key, value) }
        client().newCall(builder.build()).execute().use { resp ->
            return readResponse(resp, null)
        }
    }

    actual fun downloadTo(
        url: String,
        headers: Map<String, String>,
        filePath: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val target = File(filePath)
        target.parentFile?.mkdirs()
        // 同一目标的并发下载必须串行，避免字节交叉后 rename 成「完整」视频。
        val gate = downloadGates.getOrPut(target.absolutePath) { Any() }
        synchronized(gate) {
            // 断点续传用稳定临时名（同目标已有互斥闸）：失败保留 .part，
            // 下次 Range 从断点继续；URL 变了（换流/过期签名）则整段重下。
            val temp = File("$filePath.part")
            val meta = File("$filePath.part.meta")
            // meta 两行：URL + 已知总长（0 = 未知）。URL 变了（换流/过期签名）
            // 则断点不可信，整段重下。
            val metaLines = if (meta.isFile) runCatching { meta.readLines() }.getOrDefault(emptyList()) else emptyList()
            val metaUrl = metaLines.getOrNull(0).orEmpty()
            val metaTotal = metaLines.getOrNull(1)?.toLongOrNull() ?: 0L
            var resumeFrom = if (temp.isFile && metaUrl == url) temp.length() else 0L
            if (resumeFrom > 0 && metaTotal > 0 && resumeFrom >= metaTotal) {
                // 上次其实下完了只是没改名：直接收尾。
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                meta.delete()
                onProgress(resumeFrom, metaTotal)
                return
            }
            val builder = Request.Builder().url(url).get()
            headers.forEach { (key, value) -> builder.header(key, value) }
            if (resumeFrom > 0) builder.header("Range", "bytes=$resumeFrom-")
            var knownTotal = 0L
            try {
                downloadClient().newCall(builder.build()).execute().use { resp ->
                    when {
                        resp.code == 416 -> {
                            // 断点超出文件长度：本地残留不可信，整段重下。
                            temp.delete()
                            resumeFrom = 0
                            val retry = Request.Builder().url(url).get()
                            headers.forEach { (key, value) -> retry.header(key, value) }
                            downloadClient().newCall(retry.build()).execute().use { again ->
                                if (!again.isSuccessful) error("下载失败 ${again.code}")
                                knownTotal = again.body?.contentLength() ?: 0L
                                writeBody(again, temp, 0, onProgress)
                            }
                        }
                        resp.code == 206 -> {
                            // 真续传：追加写。总长 = 已下 + 剩余（Content-Range 更准）。
                            val rangeTotal = resp.header("Content-Range")
                                ?.substringAfterLast('/')?.toLongOrNull()
                            knownTotal = rangeTotal ?: (resumeFrom + (resp.body?.contentLength() ?: 0L))
                            appendBody(resp, temp, resumeFrom, knownTotal, onProgress)
                        }
                        resp.isSuccessful -> {
                            // 服务端无视 Range 回了 200：从头整段重下。
                            resumeFrom = 0
                            knownTotal = resp.body?.contentLength() ?: 0L
                            writeBody(resp, temp, 0, onProgress)
                        }
                        else -> error("下载失败 ${resp.code}")
                    }
                }
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                meta.delete()
            } catch (t: Throwable) {
                // 保留 .part 供下次续传；meta 记下 URL 与总长，
                // 下次可判断「已下完只差改名」或从断点继续。
                runCatching { meta.writeText("$url\n${knownTotal.coerceAtLeast(0)}") }
                throw t
            }
        }
    }

    private fun writeBody(
        resp: okhttp3.Response,
        temp: File,
        base: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val body = resp.body ?: error("下载失败：响应为空")
        val total = body.contentLength()
        body.byteStream().use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = base
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    }

    private fun appendBody(
        resp: okhttp3.Response,
        temp: File,
        base: Long,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val body = resp.body ?: error("下载失败：响应为空")
        body.byteStream().use { input ->
            java.io.FileOutputStream(temp, /* append = */ true).use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = base
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    }

    /** 同目标下载互斥。条目数最多等于历史下载目标数，内存可忽略；
     *  不能在下载结束时移除：还在排队的第二个下载会拿到新锁失去互斥。 */
    private val downloadGates = java.util.concurrent.ConcurrentHashMap<String, Any>()

    actual fun openStream(url: String, headers: Map<String, String>): InputStream {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        val resp = client().newCall(builder.build()).execute()
        if (!resp.isSuccessful) {
            resp.close()
            error("请求失败 ${resp.code}")
        }
        val body = resp.body ?: run {
            resp.close()
            error("响应为空")
        }
        return object : FilterInputStream(body.byteStream()) {
            override fun close() {
                runCatching { super.close() }
                resp.close()
            }
        }
    }

    private fun readResponse(resp: okhttp3.Response, maxBytes: Int?): HttpResponse {
        val bytes = if (maxBytes != null) {
            val stream = resp.body?.byteStream() ?: return HttpResponse(resp.code, ByteArray(0), headersOf(resp))
            val buf = ByteArray(maxBytes)
            var n = 0
            while (n < maxBytes) {
                val read = stream.read(buf, n, maxBytes - n)
                if (read <= 0) break
                n += read
            }
            buf.copyOf(n)
        } else {
            resp.body?.bytes() ?: ByteArray(0)
        }
        return HttpResponse(resp.code, bytes, headersOf(resp))
    }

    private fun headersOf(resp: okhttp3.Response): Map<String, List<String>> = buildMap {
        resp.headers.names().forEach { name -> put(name, resp.headers.values(name)) }
    }
}

actual fun md5Hex(text: String): String {
    val digest = java.security.MessageDigest.getInstance("MD5").digest(text.encodeToByteArray())
    return digest.joinToString("") { b ->
        val v = b.toInt() and 0xff
        v.toString(16).padStart(2, '0')
    }
}
