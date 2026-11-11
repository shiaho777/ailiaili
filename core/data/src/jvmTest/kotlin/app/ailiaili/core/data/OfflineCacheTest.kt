package app.ailiaili.core.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineCacheTest {
    @Test
    fun scansAndDeletesFromExtraRoot() {
        val base = Files.createTempDirectory("cache-root").toFile()
        val extra = Files.createTempDirectory("cache-extra").toFile()
        val cache = OfflineCache(AppFile(base.absolutePath))
        cache.setExtraRoot(extra.absolutePath)

        val folder = File(extra, "BV1xx411c7mD").apply { mkdirs() }
        File(folder, "video.m4s").writeBytes(byteArrayOf(1, 2, 3))
        File(folder, "meta.json").writeText(
            """{"bvid":"BV1xx411c7mD","title":"标题","cover":"","qn":80,"video":"video.m4s"}""",
        )

        val entries = cache.list()
        assertEquals(1, entries.size)
        assertEquals("BV1xx411c7mD", entries.single().bvid)
        assertTrue(File(entries.single().videoPath).exists())

        cache.delete("BV1xx411c7mD")
        assertFalse(folder.exists())
    }
}
