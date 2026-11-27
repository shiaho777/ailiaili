package app.ailiaili.core.data

import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.Danmaku
import app.ailiaili.core.model.SubtitleCue
import app.ailiaili.core.model.SubtitleTrack
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CachedPlaybackTest {
    @Test
    fun loadsSubtitleTracksWithoutRemoteUrlsAndIgnoresMissingSidecars() {
        val tmp = Files.createTempDirectory("ailiaili-offline-overlays").toFile()
        try {
            val file = tmp.resolve("part.mp4").apply { writeBytes(byteArrayOf(1)) }
            val entry = CacheEntry("BV1xx411c7mD", "Part", "", file.absolutePath, null, 80, 101)
            assertEquals(CachedPlayback(), readCachedPlayback(entry))

            val comment = Danmaku(4.0, "cached comment", 1, 0xABCDEF)
            val tracks = listOf(
                CachedSubtitle(
                    SubtitleTrack(1, "zh", "中文", "https://example.invalid/signed", false),
                    listOf(SubtitleCue(2.0, 5.0, "离线字幕")),
                ),
                CachedSubtitle(
                    SubtitleTrack(2, "en", "English", "https://example.invalid/signed", false),
                    listOf(SubtitleCue(2.0, 5.0, "Offline subtitle")),
                ),
            )
            writeCachedPlayback(entry, CachedPlayback(listOf(comment), tracks))
            val saved = readCachedPlayback(entry)
            assertEquals(listOf(comment), saved.danmaku)
            assertEquals(tracks.map { it.copy(track = it.track.copy(url = "")) }, saved.subtitles)
            assertFalse(tmp.resolve("playback.json").readText().contains("example.invalid"))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
