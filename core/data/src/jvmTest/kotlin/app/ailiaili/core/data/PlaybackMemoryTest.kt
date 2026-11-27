package app.ailiaili.core.data

import app.ailiaili.core.model.VideoItem
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackMemoryTest {
    @Test
    fun migratesPositionsAndRestoresTheLatestPart() = withMemoryFile { file ->
        file.writeTextAtomic("""{"BV1:1":12000,"BV1:2":{"pos":18000,"at":2}}""")
        val memory = PlaybackMemory(file)
        assertEquals(12_000L, memory.read("BV1", 1))
        assertEquals(18_000L, memory.read("BV1", 2))
        assertNull(memory.resume.value)

        memory.write("BV1", 2, 21_321, entry)
        memory.flush()

        val reloaded = PlaybackMemory(file)
        assertEquals(entry, reloaded.resume.value)
        assertEquals(21_321L, reloaded.read("BV1", 2))
        assertEquals(12_000L, reloaded.read("BV1", 1))
    }

    @Test
    fun dismissalKeepsPositionAndHidesUpdatesUntilThePartIsOpenedAgain() = withMemoryFile { file ->
        val memory = PlaybackMemory(file)
        memory.write("BV1", 2, 21_000, entry)
        memory.dismiss("BV1", 2)
        memory.write("BV1", 2, 22_000, entry.copy(positionMs = 22_000))
        memory.flush()
        assertNull(memory.resume.value)
        assertNull(PlaybackMemory(file).resume.value)
        assertEquals(22_000L, PlaybackMemory(file).read("BV1", 2))

        memory.begin("BV1", 2)
        memory.write("BV1", 2, 23_000, entry.copy(positionMs = 23_000))
        assertEquals(23_000L, memory.resume.value?.positionMs)
        memory.write("BV1", 2, 60_000, entry.copy(positionMs = 60_000))
        assertNull(memory.resume.value)
    }

    private fun withMemoryFile(test: (AppFile) -> Unit) {
        val dir = Files.createTempDirectory("playback-memory").toFile()
        try {
            test(AppFile(dir.resolve("play-memory.json").absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    private val entry = ResumePlayback(
        video = VideoItem(bvid = "BV1", cid = 2, title = "视频", author = "UP 主", ownerMid = 10),
        partLabel = "第二段",
        episodeId = 7,
        positionMs = 21_000,
        durationMs = 60_000,
    )
}
