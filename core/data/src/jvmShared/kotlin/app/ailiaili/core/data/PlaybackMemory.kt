package app.ailiaili.core.data

import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.network.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ResumePlayback(
    val video: VideoItem,
    val partLabel: String,
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
)

class PlaybackMemory(private val file: AppFile) {
    private val stored = runCatching {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    }.getOrElse { JSONObject() }
    private val _resume = MutableStateFlow(stored.optJSONObject(ResumeKey)?.toResume())
    val resume = _resume.asStateFlow()
    private var dirty = false
    private var lastFlushAt = 0L
    private var dismissedMedia: String? = null

    @Synchronized
    fun read(bvid: String, cid: Long): Long = when (val value = stored.opt(key(bvid, cid))) {
        is JSONObject -> value.optLong("pos", 0L)
        is Number -> value.toLong()
        else -> 0L
    }

    @Synchronized
    fun write(bvid: String, cid: Long, positionMs: Long, recent: ResumePlayback? = null) {
        if (bvid.isBlank() || cid <= 0 || positionMs < 5_000) return
        val now = System.currentTimeMillis()
        val key = key(bvid, cid)
        if (read(bvid, cid) != positionMs) {
            stored.put(key, JSONObject().put("pos", positionMs).put("at", now))
            val keys = stored.keys().asSequence().filter { it != ResumeKey }.toList()
            if (keys.size > 40) {
                keys.sortedBy { stored.optJSONObject(it)?.optLong("at", 0L) ?: 0L }
                    .take(keys.size - 40)
                    .forEach(stored::remove)
            }
            dirty = true
        }
        if (recent != null) updateResume(recent)
        if (now - lastFlushAt >= 5_000) flush(now)
    }

    @Synchronized
    fun begin(bvid: String, cid: Long) {
        if (dismissedMedia == key(bvid, cid)) dismissedMedia = null
    }

    @Synchronized
    fun dismiss(bvid: String, cid: Long) {
        if (clearResume(bvid, cid)) {
            dismissedMedia = key(bvid, cid)
            flush()
        }
    }

    private fun updateResume(recent: ResumePlayback) {
        if (dismissedMedia == key(recent.video.bvid, recent.video.cid)) return
        if (recent.positionMs !in 5_000 until (recent.durationMs - 5_000)) {
            clearResume(recent.video.bvid, recent.video.cid)
            return
        }
        if (recent == _resume.value) return
        val video = recent.video
        stored.put(
            ResumeKey,
            JSONObject()
                .put("bvid", video.bvid)
                .put("cid", video.cid)
                .put("title", video.title)
                .put("cover", video.cover)
                .put("author", video.author)
                .put("mid", video.ownerMid)
                .put("part", recent.partLabel)
                .put("episode", recent.episodeId)
                .put("position", recent.positionMs)
                .put("duration", recent.durationMs),
        )
        _resume.value = recent
        dirty = true
    }

    private fun clearResume(bvid: String, cid: Long): Boolean {
        val video = _resume.value?.video ?: return false
        if (video.bvid != bvid || video.cid != cid) return false
        stored.remove(ResumeKey)
        _resume.value = null
        dirty = true
        return true
    }

    @Synchronized
    fun flush(now: Long = System.currentTimeMillis()) {
        if (dirty && runCatching { file.writeTextAtomic(stored.toString()) }.isSuccess) {
            dirty = false
            lastFlushAt = now
        }
    }

    private fun JSONObject.toResume(): ResumePlayback? {
        val bvid = optString("bvid")
        val cid = optLong("cid", 0L)
        val title = optString("title")
        val position = optLong("position", 0L)
        val duration = optLong("duration", 0L)
        if (bvid.isBlank() || cid <= 0 || title.isBlank() || position !in 5_000 until (duration - 5_000)) return null
        return ResumePlayback(
            video = VideoItem(
                bvid = bvid,
                cid = cid,
                title = title,
                cover = optString("cover"),
                author = optString("author"),
                ownerMid = optLong("mid", 0L),
            ),
            partLabel = optString("part"),
            episodeId = optLong("episode", 0L),
            positionMs = position,
            durationMs = duration,
        )
    }

    private fun key(bvid: String, cid: Long) = "$bvid:$cid"

    private companion object {
        const val ResumeKey = "_continue"
    }
}
