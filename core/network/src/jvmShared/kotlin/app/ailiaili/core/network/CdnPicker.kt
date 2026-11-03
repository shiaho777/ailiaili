package app.ailiaili.core.network

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.CdnLine
import app.ailiaili.core.model.CdnMode
import app.ailiaili.core.model.USER_AGENT
import app.ailiaili.core.model.epochMs
import app.ailiaili.core.model.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 线路选择不挡起播。
 *
 * 有 10 分钟内的测速结果就直接换到那家的 host。没有就先用服务端给的地址开播，
 * 测速放在后台，下一支视频再用赢家。音轨复用视频那次的 host，不再单独测。
 * 换 host 不用 mcdn 模板，那类地址的签名和 host 绑在一起。
 */
object CdnPicker {
    private const val WINDOW = 256 * 1024
    private const val TTL_MS = 10 * 60 * 1000L
    private const val STALL_WINDOW_MS = 60_000L
    private const val STALL_COUNT_TO_BOOST = 2
    private const val STALL_BOOST_MS = 1_000
    private const val STALL_RECORD_CAP = 8

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val gate = Mutex()
    private val probeGate = Mutex()

    private var cachedHost: String? = null
    private var cachedAt = 0L

    /** 最近一轮测速冠军的吞吐（字节/秒），给 BufferPolicy 自动档用。 */
    @Volatile
    var lastMeasuredBytesPerSecond: Long = 0L
        private set

    private val stallLock = Any()
    private val stallTimes = mutableMapOf<String, MutableList<Long>>()

    fun invalidate() {
        scope.launch {
            gate.withLock {
                cachedHost = null
                cachedAt = 0L
                lastMeasuredBytesPerSecond = 0L
            }
            synchronized(stallLock) { stallTimes.clear() }
        }
    }

    /**
     * 上报一次播放中途卡顿。按 host 归因：同一家 CDN 的抖动才累加。
     * 只影响后续媒体，绝不在当前播放中途换源。
     */
    fun reportStall(url: String) {
        val host = hostOf(url)
        if (host.isBlank()) return
        val now = epochMs()
        synchronized(stallLock) {
            val list = stallTimes.getOrPut(host) { mutableListOf() }
            list += now
            list.removeAll { now - it > STALL_WINDOW_MS }
            if (list.size > STALL_RECORD_CAP) {
                list.subList(0, list.size - STALL_RECORD_CAP).clear()
            }
        }
    }

    /** 这条媒体要不要加深预读（毫秒）。60 秒内同 host 卡两次才触发。 */
    fun stallBoostMs(url: String): Int {
        val host = hostOf(url)
        if (host.isBlank()) return 0
        val now = epochMs()
        synchronized(stallLock) {
            val list = stallTimes[host] ?: return 0
            val recent = list.count { now - it <= STALL_WINDOW_MS }
            return if (recent >= STALL_COUNT_TO_BOOST) STALL_BOOST_MS else 0
        }
    }

    fun pickVideo(candidates: List<String>, settings: AppSettings): String {
        val pool = poolOf(candidates, settings)
        when (settings.cdnMode) {
            CdnMode.Server -> {
                CdnStatus.report.value = "跟随服务端：${hostOf(pool.first())}"
                return pool.first()
            }
            CdnMode.Manual -> {
                val line = CdnLine.byId(settings.cdnManualId)
                val swapped = line?.let { swap(pool, it.host) }
                CdnStatus.report.value = if (swapped != null) "指定线路：${line.name}" else "指定线路不可用，先用服务端线路"
                return swapped ?: pool.first()
            }
            CdnMode.Auto -> {
                val cached = freshHost()
                val swapped = cached?.let { swap(pool, it) }
                if (swapped != null) {
                    CdnStatus.report.value = "沿用 ${labelOf(cached)} 的测速结果"
                    return swapped
                }
                if (probeGate.tryLock()) {
                    scope.launch {
                        try {
                            rank(pool)
                        } finally {
                            probeGate.unlock()
                        }
                    }
                }
                CdnStatus.report.value = "先用服务端线路，正在后台测速"
                return pool.first()
            }
        }
    }

    fun pickAudio(candidates: List<String>, videoUrl: String, settings: AppSettings): String {
        val pool = poolOf(candidates, settings)
        if (settings.cdnMode == CdnMode.Server) {
            val videoHost = hostOf(videoUrl)
            return pool.firstOrNull { hostOf(it) == videoHost } ?: pool.first()
        }
        val videoHost = hostOf(videoUrl)
        if (videoHost.isNotBlank() && !videoHost.contains("mcdn.", ignoreCase = true)) {
            swap(pool, videoHost)?.let { return it }
        }
        return pool.firstOrNull { hostOf(it) == videoHost } ?: pool.first()
    }

    private fun freshHost(): String? {
        val host = cachedHost ?: return null
        if (epochMs() - cachedAt > TTL_MS) return null
        return host
    }

    private suspend fun rank(pool: List<String>) {
        val ranked = coroutineScope {
            buildCandidates(pool).map { url ->
                async { url to probeOf(url) }
            }.map { it.await() }
        }.mapNotNull { (url, sample) ->
            val (ok, ms, bytes) = sample
            if (!ok || bytes <= 0 || ms <= 0) null else Triple(url, bytes * 1000 / ms, ms)
        }.sortedByDescending { it.second }
        gate.withLock {
            if (ranked.isEmpty()) {
                CdnStatus.report.value = "测速失败，继续用服务端线路"
                return
            }
            val winner = ranked.first()
            cachedHost = hostOf(winner.first)
            cachedAt = epochMs()
            lastMeasuredBytesPerSecond = winner.second
            val rest = ranked.drop(1).take(3).joinToString("，") {
                "${labelOf(hostOf(it.first))} ${it.second / 1024}KB/s"
            }
            val head = "最快 ${labelOf(cachedHost)}（${winner.second / 1024}KB/s）"
            CdnStatus.report.value = if (rest.isBlank()) head else "$head；其次 $rest"
        }
    }

    private fun buildCandidates(pool: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        pool.forEach { seen += it }
        val template = pool.firstOrNull { swappable(it) }
        if (template != null) {
            CdnLine.all.forEach { line -> seen += replaceHost(template, line.host) }
        }
        return seen.toList()
    }

    private fun probeOf(url: String): Triple<Boolean, Long, Long> {
        val started = epochMs()
        return runCatching {
            val resp = SharedHttp.probe.get(
                url,
                mapOf(
                    "Referer" to "https://www.bilibili.com/",
                    "User-Agent" to USER_AGENT,
                ),
                range = "bytes=0-${WINDOW - 1}",
                maxBytes = WINDOW,
            )
            if (resp.code != 200 && resp.code != 206) return Triple(false, 0L, 0L)
            val total = resp.body.size.toLong()
            val ms = (epochMs() - started).coerceAtLeast(1)
            Triple(total > 0, ms, total)
        }.getOrDefault(Triple(false, 0L, 0L))
    }

    private fun poolOf(candidates: List<String>, settings: AppSettings): List<String> {
        val raw = candidates.filter { it.isNotBlank() }.distinct()
        if (raw.isEmpty()) return raw
        if (!settings.blockPcdn) return raw
        return raw.filter { !isPcdn(it) }.ifEmpty { raw }
    }

    private fun swap(pool: List<String>, host: String): String? {
        val template = pool.firstOrNull { swappable(it) } ?: return null
        return replaceHost(template, host)
    }

    private fun swappable(url: String): Boolean {
        val host = hostOf(url)
        return host.isNotBlank() && !host.contains("mcdn.", ignoreCase = true)
    }

    private fun replaceHost(url: String, host: String): String {
        val scheme = if ("://" in url) url.substringBefore("://") else "https"
        val after = url.substringAfter("://", url)
        val restStart = after.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) after.length else it }
        val hostPart = after.substring(0, restStart)
        val rest = after.substring(restStart)
        val prefix = if ('@' in hostPart) hostPart.substringBeforeLast('@') + "@" else ""
        return "$scheme://$prefix$host$rest"
    }

    private fun hostOf(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val after = url.substringAfter("://", url)
        val hostPort = after.substringBefore('/').substringBefore('?').substringBefore('#')
        return hostPort.substringAfter('@').substringBefore(':')
    }

    private fun labelOf(host: String?): String {
        if (host.isNullOrBlank()) return "未知线路"
        return CdnLine.all.firstOrNull { it.host.equals(host, ignoreCase = true) }?.name ?: host
    }

    private fun isPcdn(url: String): Boolean {
        val after = url.substringAfter("://", url)
        val host = after.substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':')
        if (host.contains("mcdn.", ignoreCase = true) || host.contains("pcdn.", ignoreCase = true)) return true
        val query = url.substringAfter('?', "").substringBefore('#')
        return query.split('&').any { part ->
            val name = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            name.equals("os", ignoreCase = true) && value.startsWith("mcdn", ignoreCase = true)
        }
    }
}
