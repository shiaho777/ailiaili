package app.ailiaili.core.data

import app.ailiaili.core.network.JSONObject

/**
 * 动态页左栏「未读」角标的本地基准：每个 UP 主上次看到的最新一条动态的 bvid。
 *
 * 为什么不问接口：官方「新动态」依赖服务端的 update_baseline / update_num，
 * 我们走的动态接口没接这套。本地记一份「上次看到的最新一条」，刷新时对不上就打角标，
 * 零接口改动。
 *
 * 语义：角标表示「这个 UP 有新投稿，你还没单独点进去看过」。
 * 看一眼全部动态不算已读，点开某个 UP 才清它自己的角标。
 * 首次运行只建立基准、不打角标，避免第一次打开满屏红点。
 */
class DynamicSeenStore(private val file: AppFile) {
    private val map = mutableMapOf<Long, String>()
    private var dirty = false

    fun load() {
        map.clear()
        runCatching {
            if (!file.exists()) return@runCatching
            val o = JSONObject(file.readText())
            val keys = o.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val mid = key.toLongOrNull() ?: continue
                val bvid = o.optString(key)
                if (mid > 0 && bvid.isNotBlank()) map[mid] = bvid
            }
        }
    }

    /** 某个 UP 上次看到的最新 bvid；没有基准时返回 null。 */
    fun seen(mid: Long): String? = if (mid <= 0) null else map[mid]?.takeIf { it.isNotBlank() }

    /** 只改内存，批量更新后调用 [flush] 落盘。 */
    fun put(mid: Long, bvid: String) {
        if (mid <= 0 || bvid.isBlank()) return
        if (map[mid] == bvid) return
        map[mid] = bvid
        dirty = true
    }

    fun flush() {
        if (!dirty) return
        runCatching {
            file.parentFile?.mkdirs()
            val o = JSONObject()
            map.forEach { (mid, bvid) -> o.put(mid.toString(), bvid) }
            file.writeTextAtomic(o.toString(2))
            dirty = false
        }
    }
}
