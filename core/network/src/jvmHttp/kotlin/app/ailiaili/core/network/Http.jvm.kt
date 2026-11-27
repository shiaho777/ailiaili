package app.ailiaili.core.network

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        cancellation: DownloadCancellation?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val target = File(filePath)
        target.parentFile?.mkdirs()
        val gate = downloadGates.getOrPut(target.absolutePath) { Any() }
        synchronized(gate) {
            cancellation?.check()
            val temp = File("$filePath.part")
            val meta = File("$filePath.part.meta")
            val saved = runCatching { meta.readLines() }.getOrDefault(emptyList())
            val savedTotal = saved.getOrNull(1)?.toLongOrNull() ?: 0L
            val validator = saved.getOrNull(2).orEmpty()
            val sameSource = saved.firstOrNull() == url
            val strongValidator = validator.length > 2 && validator.startsWith('"') && validator.endsWith('"')
            val source = saved.firstOrNull()?.toHttpUrlOrNull()?.newBuilder()?.query(null)?.fragment(null)?.build()
            val current = url.toHttpUrlOrNull()?.newBuilder()?.query(null)?.fragment(null)?.build()
            val renewedSource = strongValidator && source != null && source == current
            val offset = temp.length().takeIf {
                (sameSource || renewedSource && it < savedTotal) && (savedTotal <= 0 || it <= savedTotal)
            } ?: 0L
            fun transfer(start: Long): Boolean {
                val builder = Request.Builder().url(url).get()
                headers.forEach { (key, value) -> builder.header(key, value) }
                builder.header("Accept-Encoding", "identity")
                if (start > 0) {
                    builder.header("Range", "bytes=$start-")
                    if (validator.isNotEmpty()) builder.header("If-Range", validator)
                }
                val call = downloadClient().newCall(builder.build())
                val consume = {
                    call.execute().use { response ->
                        if (response.code == 416 && start > 0) {
                            false
                        } else {
                            check(response.isSuccessful) { "下载失败 HTTP ${response.code}" }
                            val body = checkNotNull(response.body) { "下载响应为空" }
                            val range = response.header("Content-Range")?.let(downloadRange::matchEntire)
                            val suppliedValidator = response.header("ETag")?.takeUnless { it.startsWith("W/") }
                                ?: response.header("Last-Modified").orEmpty()
                            val partial = response.code == 206
                            val end = range?.groupValues?.get(2)?.toLongOrNull()
                            val total = if (partial) range?.groupValues?.get(3)?.toLongOrNull() ?: -1L else body.contentLength()
                            val validRange = !partial || range?.groupValues?.get(1)?.toLongOrNull() == start &&
                                end != null && end >= start && end == total - 1 &&
                                (savedTotal <= 0 || start == 0L || total == savedTotal) &&
                                (start == 0L || validator == suppliedValidator ||
                                    sameSource && (validator.isEmpty() || suppliedValidator.isEmpty()))
                            if (!validRange && start > 0) false else {
                                check(validRange) { "下载响应的字节范围无效" }
                                val base = if (partial) start else 0L
                                if (base == 0L) {
                                    check(!temp.exists() || temp.delete()) { "无法重置临时文件" }
                                }
                                val free = target.parentFile?.usableSpace ?: 0L
                                if (free > 0L && total - base > free) throw DownloadStorageException()
                                meta.writeText("$url\n$total\n${suppliedValidator.ifEmpty { if (base > 0) validator else "" }}")
                                var written = base
                                body.byteStream().use { input ->
                                    java.io.FileOutputStream(temp, base > 0).use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        onProgress(written, total)
                                        while (true) {
                                            cancellation?.check()
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            output.write(buffer, 0, count)
                                            written += count
                                            onProgress(written, total)
                                        }
                                    }
                                }
                                check(written > 0 && (total <= 0 || written == total)) { "下载文件长度不完整" }
                                true
                            }
                        }
                    }
                }
                return if (cancellation == null) consume() else cancellation.withRequest(call::cancel, consume)
            }

            if (offset <= 0L || offset != savedTotal) {
                if (!transfer(offset)) check(transfer(0)) { "下载无法重新开始" }
            }
            cancellation?.check()
            try {
                java.nio.file.Files.move(
                    temp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            meta.delete()
        }
    }

    private val downloadRange = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

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
