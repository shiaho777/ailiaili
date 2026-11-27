package app.ailiaili.core.data

import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.network.DownloadCancellation
import app.ailiaili.core.network.HttpEngine
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfflineCacheTest {
    @Test
    fun exportsUseDifferentNamesForEachPartAndTrack() {
        val entry = CacheEntry("BV1xx411c7mD", "标题 / 含特殊字符", "", "/video.m4s", "/audio.m4s", 80, 101)
        val names = (downloadExports(entry) + downloadExports(entry.copy(cid = 202))).map { it.name }
        assertEquals(4, names.distinct().size)
        assertTrue(names.none { '/' in it })
        assertTrue(names.any { it.endsWith("-101-audio.m4s") })
        assertTrue(names.any { it.endsWith("-202-video.m4s") })
    }

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
        assertTrue(entries.single().storedBytes >= 3)
        assertTrue(entries.single().storedAtMs > 0)

        cache.delete("BV1xx411c7mD")
        assertFalse(folder.exists())
    }

    @Test
    fun deletingACacheEntryStaysInsideTheCacheRoots() {
        val base = Files.createTempDirectory("cache-delete-root").toFile()
        try {
            val cache = OfflineCache(AppFile(File(base, "cache").apply { mkdir() }.absolutePath))
            val other = File(base, "other").apply { mkdir() }

            assertFailsWith<IllegalArgumentException> { cache.delete("../other") }
            assertTrue(other.isDirectory)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun differentPartsSurviveAFailedReplacementAndDirectoryChange() {
        val base = Files.createTempDirectory("ailiaili-cache-parts").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val payload = byteArrayOf(1, 7, 4, 2)
        server.createContext("/media") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/failure") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val root = AppFile(base.resolve("default").absolutePath)
            val custom = base.resolve("custom").absolutePath
            val cache = OfflineCache(root)
            val media = "http://127.0.0.1:${server.address.port}"
            val client = HttpEngine(3_000, 3_000)
            val info = PlayInfo("$media/media", null, emptyList(), 0, emptyList(), 80)
            val a = DownloadRequest("BV1xx411c7mD", 101, "Part one", "", 80, destination = custom)
            val b = a.copy(cid = 202, title = "Part two")
            val first = cache.download(a, info, client, DownloadCancellation()) { _, _, _ -> }
            cache.download(b, info, client, DownloadCancellation()) { _, _, _ -> }

            assertFailsWith<IllegalStateException> {
                cache.download(a.copy(qn = 64), info.copy(videoUrl = "$media/failure"), client, DownloadCancellation()) { _, _, _ -> }
            }
            assertTrue(File(first.videoPath).readBytes().contentEquals(payload))
            val reopened = OfflineCache(root)
            assertEquals(setOf(101L, 202L), reopened.list().map { it.cid }.toSet())

            reopened.delete(a.bvid, a.cid)
            assertEquals(listOf(202L), reopened.list().map { it.cid })
        } finally {
            server.stop(0)
            base.deleteRecursively()
        }
    }
}
