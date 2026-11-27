package app.ailiaili.core.data

import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.SubtitleCue
import app.ailiaili.core.model.SubtitleTrack
import app.ailiaili.core.network.JSONArray
import app.ailiaili.core.network.JSONObject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

data class CachedSubtitle(val track: SubtitleTrack, val cues: List<SubtitleCue>)

data class CachedPlayback(
    val danmaku: List<Danmaku> = emptyList(),
    val subtitles: List<CachedSubtitle> = emptyList(),
)

fun readCachedPlayback(entry: CacheEntry): CachedPlayback = runCatching {
    val file = cachedPlaybackFile(entry) ?: return CachedPlayback()
    val json = JSONObject(file.readText())
    val comments = json.optJSONArray("danmaku").objects().mapNotNull { row ->
        val time = row.optDouble("t").takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
        val text = row.optString("s").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Danmaku(time, text, row.optInt("m", 1), row.optInt("c", 0xFFFFFF))
    }.sortedBy { it.timeSec }
    val subtitles = json.optJSONArray("subtitles").objects().mapNotNull { row ->
        val lan = row.optString("lan").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val cues = row.optJSONArray("cues").objects().mapNotNull { cue ->
            val start = cue.optDouble("from")
            val end = cue.optDouble("to")
            val text = cue.optString("text")
            if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start || text.isBlank()) null
            else SubtitleCue(start, end, text)
        }.sortedBy { it.from }
        if (cues.isEmpty()) null else CachedSubtitle(
            SubtitleTrack(row.optLong("id"), lan, row.optString("label", lan), "", row.optBoolean("ai")), cues,
        )
    }.distinctBy { it.track.lan }
    CachedPlayback(comments, subtitles)
}.getOrDefault(CachedPlayback())

internal fun writeCachedPlayback(entry: CacheEntry, assets: CachedPlayback) {
    if (assets.danmaku.isEmpty() && assets.subtitles.isEmpty()) return
    if (!AppFile(entry.videoPath).isFile()) return
    val file = cachedPlaybackFile(entry) ?: return
    val comments = JSONArray()
    assets.danmaku.forEach {
        comments.put(JSONObject().put("t", it.timeSec).put("s", it.text).put("m", it.mode).put("c", it.color))
    }
    val subtitles = JSONArray()
    assets.subtitles.forEach { subtitle ->
        val cues = JSONArray()
        subtitle.cues.forEach {
            cues.put(JSONObject().put("from", it.from).put("to", it.to).put("text", it.text))
        }
        val track = subtitle.track
        subtitles.put(JSONObject().put("id", track.id).put("lan", track.lan).put("label", track.label)
            .put("ai", track.ai).put("cues", cues))
    }
    file.writeTextAtomic(JSONObject().put("danmaku", comments).put("subtitles", subtitles).toString())
}

internal suspend fun cachePlaybackExtras(
    repo: BiliRepository,
    request: DownloadRequest,
    info: PlayInfo,
    entry: CacheEntry,
    networkAvailable: () -> Boolean,
) = coroutineScope {
    val danmaku = async {
        val saved = mutableListOf<Danmaku>()
        if (request.includeDanmaku) withTimeoutOrNull(25_000) {
            val segments = ((info.durationMs.coerceAtLeast(1L) - 1L) / 360_000L + 1L).coerceAtMost(200L)
            for (segment in 1..segments.toInt()) {
                if (!networkAvailable()) break
                saved += repo.danmakuSegment(request.cid, segment).getOrDefault(emptyList())
            }
        }
        saved.distinct()
    }
    val subtitles = async {
        val saved = mutableListOf<CachedSubtitle>()
        if (request.includeSubtitles && networkAvailable()) withTimeoutOrNull(25_000) {
            val tracks = repo.subtitles(request.bvid, request.cid).getOrDefault(emptyList()).distinctBy { it.lan }
            for (track in tracks) {
                if (!networkAvailable()) break
                val cues = repo.subtitleCues(track.url).getOrDefault(emptyList())
                if (cues.isNotEmpty()) saved += CachedSubtitle(track.copy(url = ""), cues)
            }
        }
        saved
    }
    writeCachedPlayback(entry, CachedPlayback(danmaku.await(), subtitles.await()))
}

private fun cachedPlaybackFile(entry: CacheEntry): AppFile? =
    AppFile(entry.videoPath).parentFile?.let { AppFile(it, "playback.json") }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull(::optJSONObject)
