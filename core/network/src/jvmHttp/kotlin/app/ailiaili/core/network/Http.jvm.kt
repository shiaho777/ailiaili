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
    private val client = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(readTimeoutMs + connectTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    /** 大文件下载不能受 API 的 callTimeout 限制。 */
    private val downloadClient by lazy {
        client.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
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
        client.newCall(builder.build()).execute().use { resp ->
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
        client.newCall(builder.build()).execute().use { resp ->
            return readResponse(resp, null)
        }
    }

    actual fun downloadTo(
        url: String,
        headers: Map<String, String>,
        filePath: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        val target = File(filePath)
        val temp = File("$filePath.part")
        target.parentFile?.mkdirs()
        try {
            downloadClient.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) error("下载失败 ${resp.code}")
                val body = resp.body ?: error("下载失败：响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
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
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    actual fun openStream(url: String, headers: Map<String, String>): InputStream {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) -> builder.header(key, value) }
        val resp = client.newCall(builder.build()).execute()
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
