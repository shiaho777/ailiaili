package app.ailiaili.core.data

import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.network.DownloadCancellation
import app.ailiaili.core.network.DownloadStorageException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.ailiaili.core.network.JSONArray
import app.ailiaili.core.network.JSONObject

data class DownloadRequest(
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val qn: Int,
    val epId: Long = 0,
    val aid: Long = 0,
    val destination: String? = null,
    val includeDanmaku: Boolean = false,
    val includeSubtitles: Boolean = false,
) {
    val key: String get() = "$bvid/$cid"
    init {
        require(bvid.isNotBlank() && bvid.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        require(cid > 0 && qn > 0)
    }
}

enum class DownloadPhase {
    Queued, Resolving, Video, Audio, Pausing, Paused, Finalizing, Failed, Removing;

    val canPause: Boolean get() = this == Queued || this == Resolving || this == Video || this == Audio
    val canResume: Boolean get() = this == Paused || this == Failed
    val isWorking: Boolean get() = this == Resolving || this == Video || this == Audio ||
        this == Pausing || this == Finalizing
}

enum class DownloadNetwork { Available, Offline, Metered }

data class DownloadTask(
    val request: DownloadRequest,
    val phase: DownloadPhase,
    val done: Long = 0,
    val total: Long = -1,
    val error: String? = null,
    val bytesPerSecond: Long = 0,
)

class DownloadQueue internal constructor(
    private val file: AppFile,
    private val resolve: suspend (DownloadRequest) -> PlayInfo,
    private val transfer: (DownloadRequest, PlayInfo, DownloadCancellation, (DownloadPhase, Long, Long) -> Unit) -> CacheEntry?,
    private val removePartial: (DownloadRequest) -> Unit,
    private val saveExtras: suspend (DownloadRequest, PlayInfo, CacheEntry) -> Unit = { _, _, _ -> },
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val gate = Any()
    private val queue = MutableStateFlow(load())
    val tasks = queue.asStateFlow()
    private val revision = MutableStateFlow(0)
    val completedRevision = revision.asStateFlow()
    private val networkState = MutableStateFlow(DownloadNetwork.Available)
    val network = networkState.asStateFlow()
    private var active: Pair<String, DownloadCancellation>? = null
    private var waitingKey: String? = null
    private var draining = false

    fun enqueue(requests: List<DownloadRequest>): Int = synchronized(gate) {
        val existing = queue.value.mapTo(hashSetOf()) { it.request.key }
        val added = requests.filter { existing.add(it.key) }.map { DownloadTask(it, DownloadPhase.Queued) }
        if (added.isNotEmpty()) {
            publish(queue.value + added)
            startWorker()
        }
        added.size
    }

    fun pause(key: String): Unit = synchronized(gate) {
        val row = queue.value.firstOrNull { it.request.key == key } ?: return
        if (waitingKey == key) waitingKey = null
        if (row.phase.canPause) pauseRow(row)
    }

    fun pauseAll() = synchronized(gate) {
        waitingKey = null
        queue.value.filter { it.phase.canPause }.forEach(::pauseRow)
    }

    private fun pauseRow(row: DownloadTask) {
        val token = active?.takeIf { it.first == row.request.key }?.second
        replace(row.copy(phase = if (token == null) DownloadPhase.Paused else DownloadPhase.Pausing))
        token?.cancel()
    }

    fun resumeAll() = requeue(DownloadPhase.Paused)

    fun retryFailed() = requeue(DownloadPhase.Failed)

    private fun requeue(phase: DownloadPhase) = synchronized(gate) {
        queue.value = queue.value.map {
            if (it.phase == phase) it.copy(phase = DownloadPhase.Queued, error = null, bytesPerSecond = 0) else it
        }
        startWorker()
    }

    fun setNetwork(state: DownloadNetwork) = synchronized(gate) {
        if (networkState.value == state) return
        networkState.value = state
        if (state == DownloadNetwork.Available) {
            startWorker()
        } else {
            val key = active?.first
            val row = queue.value.firstOrNull { it.request.key == key && it.phase.canPause }
            if (row != null) {
                waitingKey = row.request.key
                pauseRow(row)
            }
        }
    }

    fun resume(key: String) = synchronized(gate) {
        val row = queue.value.firstOrNull { it.request.key == key && it.phase.canResume } ?: return
        replace(row.copy(phase = DownloadPhase.Queued, error = null))
        startWorker()
    }

    fun discard(key: String) {
        val row = synchronized(gate) {
            val pending = queue.value.firstOrNull { it.request.key == key && it.phase.canResume } ?: return
            replace(pending.copy(phase = DownloadPhase.Removing))
            pending
        }
        scope.launch {
            val removed = runCatching { removePartial(row.request) }.isSuccess
            synchronized(gate) {
                if (!removed || !removeFromQueue(key)) {
                    replace(row.copy(phase = DownloadPhase.Failed, error = "清理下载任务失败"))
                }
            }
        }
    }

    private fun startWorker() {
        if (draining || networkState.value != DownloadNetwork.Available) return
        draining = true
        scope.launch {
            while (true) {
                val (row, token) = synchronized(gate) {
                    val row = queue.value.firstOrNull {
                        networkState.value == DownloadNetwork.Available && it.phase == DownloadPhase.Queued
                    }
                    if (row == null) {
                        draining = false
                        return@launch
                    }
                    val token = DownloadCancellation()
                    active = row.request.key to token
                    replace(row.copy(phase = DownloadPhase.Resolving))
                    row to token
                }
                var lastUpdateAt = 0L
                var lastPhase = DownloadPhase.Resolving
                var measuredAt = 0L
                var measuredBytes = 0L
                var speed = 0L
                val result = runCatching {
                    val info = resolve(row.request)
                    token.check()
                    val entry = transfer(row.request, info, token) { phase, done, total ->
                        token.check()
                        val now = System.nanoTime()
                        if (phase != lastPhase || done < measuredBytes) {
                            measuredAt = now
                            measuredBytes = done
                            speed = 0L
                        } else if (now - measuredAt >= 600_000_000L) {
                            speed = ((done - measuredBytes) * 1_000_000_000.0 / (now - measuredAt)).toLong()
                            measuredAt = now
                            measuredBytes = done
                        }
                        if (phase != lastPhase || now - lastUpdateAt >= 150_000_000 || done == total) {
                            synchronized(gate) {
                                token.check()
                                replace(row.copy(phase = phase, done = done, total = total, bytesPerSecond = speed))
                            }
                            lastPhase = phase
                            lastUpdateAt = now
                        }
                    }
                    if (entry != null && networkState.value == DownloadNetwork.Available &&
                        (row.request.includeDanmaku || row.request.includeSubtitles)
                    ) {
                        runCatching { saveExtras(row.request, info, entry) }
                    }
                }
                synchronized(gate) {
                    active = null
                    val resumeAfterNetwork = waitingKey == row.request.key
                    waitingKey = null
                    val current = queue.value.first { it.request.key == row.request.key }
                    when {
                        result.isSuccess -> {
                            if (!removeFromQueue(row.request.key)) {
                                replace(current.copy(phase = DownloadPhase.Failed, error = "文件已保存，队列记录更新失败"))
                            }
                            revision.value++
                        }
                        token.isCancelled -> replace(current.copy(
                            phase = if (resumeAfterNetwork) DownloadPhase.Queued else DownloadPhase.Paused,
                        ))
                        result.exceptionOrNull() is DownloadStorageException ->
                            replace(current.copy(phase = DownloadPhase.Failed, error = "存储空间不足，清理空间后重试"))
                        else -> replace(current.copy(phase = DownloadPhase.Failed, error = "下载失败，可重新获取线路后重试"))
                    }
                }
            }
        }
    }

    private fun replace(task: DownloadTask) {
        queue.value = queue.value.map { if (it.request.key == task.request.key) task else it }
    }

    private fun removeFromQueue(key: String): Boolean = runCatching {
        publish(queue.value.filterNot { it.request.key == key })
    }.isSuccess

    private fun publish(rows: List<DownloadTask>) {
        val data = JSONArray()
        rows.forEach { row ->
            val request = row.request
            val item = JSONObject().put("bvid", request.bvid).put("cid", request.cid)
                .put("title", request.title).put("cover", request.cover).put("qn", request.qn)
                .put("epId", request.epId).put("aid", request.aid)
                .put("includeDanmaku", request.includeDanmaku).put("includeSubtitles", request.includeSubtitles)
            request.destination?.let { item.put("destination", it) }
            data.put(item)
        }
        file.writeTextAtomic(data.toString())
        queue.value = rows
    }

    private fun load(): List<DownloadTask> {
        val data = runCatching { JSONArray(file.readText()) }.getOrElse { return emptyList() }
        return (0 until data.length()).mapNotNull { index ->
            runCatching {
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val request = DownloadRequest(
                    item.optString("bvid"), item.optLong("cid"), item.optString("title"), item.optString("cover"),
                    item.optInt("qn"), item.optLong("epId"), item.optLong("aid"),
                    item.optString("destination").takeIf { item.has("destination") },
                    item.optBoolean("includeDanmaku"), item.optBoolean("includeSubtitles"),
                )
                DownloadTask(request, DownloadPhase.Paused)
            }.getOrNull()
        }.distinctBy { it.request.key }
    }
}
