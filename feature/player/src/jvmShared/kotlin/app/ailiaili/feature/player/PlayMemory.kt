package app.ailiaili.feature.player

import app.ailiaili.core.data.AppFile
import app.ailiaili.core.data.appStorageDir
import app.ailiaili.core.network.JSONObject

/** 记住每个分 P 看到的位置。关掉再打开，从上次停下的地方继续。 */
internal object PlayMemory {
    private const val FileName = "play-memory.json"
    private const val Max = 40
    private const val FlushIntervalMs = 5_000L

    private var cache: JSONObject? = null
    private var dirty = false
    private var lastFlushAt = 0L

    fun read(bvid: String, cid: Long): Long {
        if (bvid.isBlank() || cid <= 0) return 0
        return load().optLong(key(bvid, cid), 0L)
    }

    /**
     * 更新内存中的进度；真正落盘按 5 秒节流。
     *
     * 以前播放器每 400ms 调一次 write，每次都读文件 + 解析 JSON + 重写，
     * SponsorBlock 开着时磁盘 I/O 会一直压在 UI 线程上。现在内存即时更新，
     * 落盘最多 5 秒一次，退出时再 flush。
     */
    fun write(bvid: String, cid: Long, positionMs: Long) {
        if (bvid.isBlank() || cid <= 0 || positionMs < 5_000) return
        val root = load()
        root.put(key(bvid, cid), positionMs)
        val keys = root.keys().asSequence().toList()
        if (keys.size > Max) keys.take(keys.size - Max).forEach { root.remove(it) }
        dirty = true
        val now = System.currentTimeMillis()
        if (now - lastFlushAt >= FlushIntervalMs) flush(now)
    }

    /** 退出播放器 / 进程退出前调用，保证最后一次进度落盘。 */
    fun flush(now: Long = System.currentTimeMillis()) {
        if (!dirty) return
        val root = cache ?: return
        runCatching { file().writeTextAtomic(root.toString()) }
        dirty = false
        lastFlushAt = now
    }

    private fun key(bvid: String, cid: Long) = "$bvid:$cid"

    private fun file(): AppFile = AppFile(appStorageDir(), FileName)

    private fun load(): JSONObject {
        cache?.let { return it }
        val stored = file()
        val parsed = if (!stored.exists()) {
            JSONObject()
        } else {
            runCatching { JSONObject(stored.readText()) }.getOrDefault(JSONObject())
        }
        cache = parsed
        return parsed
    }
}
