package app.ailiaili.core.network

class HttpResponse(
    val code: Int,
    val body: ByteArray,
    private val headerMap: Map<String, List<String>>,
) {
    val isSuccessful: Boolean get() = code in 200..299
    val bodyText: String get() = body.decodeToString()

    fun header(name: String): String? =
        headerMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun headers(name: String): List<String> =
        headerMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
}

expect class HttpEngine(connectTimeoutMs: Long, readTimeoutMs: Long) {
    fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        form: Map<String, String>?,
        range: String?,
        maxBytes: Int?,
    ): HttpResponse

    /** 发送 application/json 请求体。直播状态批量查询等接口只认 JSON，不认 form。 */
    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String>,
    ): HttpResponse

    /**
     * 流式下载到文件，不把整个响应读进内存。
     * 先写 `<filePath>.part`，成功后再原子改名；失败会清掉临时文件。
     */
    fun downloadTo(
        url: String,
        headers: Map<String, String>,
        filePath: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    )

    /** 打开一个流式响应。调用方负责 close；关闭流会同时释放底层响应。 */
    fun openStream(url: String, headers: Map<String, String>): java.io.InputStream
}

fun HttpEngine.get(
    url: String,
    headers: Map<String, String> = emptyMap(),
    range: String? = null,
    maxBytes: Int? = null,
): HttpResponse = execute("GET", url, headers, null, range, maxBytes)

fun HttpEngine.post(
    url: String,
    form: Map<String, String>,
    headers: Map<String, String> = emptyMap(),
): HttpResponse = execute("POST", url, headers, form, null, null)

object SharedHttp {
    val default by lazy { HttpEngine(15_000, 60_000) }
    val api by lazy { HttpEngine(12_000, 20_000) }
    val probe by lazy { HttpEngine(3_000, 3_000) }
}

internal expect fun md5Hex(text: String): String
