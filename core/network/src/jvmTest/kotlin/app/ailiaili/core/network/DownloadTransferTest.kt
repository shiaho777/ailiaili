package app.ailiaili.core.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DownloadTransferTest {
    @Test
    fun renewedSignatureKeepsThePrefixWhenTheResourceAndValidatorMatch() {
        val tmp = Files.createTempDirectory("ailiaili-signed-url").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<Pair<String?, String?>>()
        server.createContext("/video") { exchange ->
            try {
                requests += exchange.requestHeaders.getFirst("Range") to exchange.requestHeaders.getFirst("If-Range")
                exchange.responseHeaders.add("ETag", "\"same-media\"")
                exchange.responseHeaders.add("Content-Range", "bytes 3-5/6")
                exchange.sendResponseHeaders(206, 3)
                exchange.responseBody.use { it.write("def".toByteArray()) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/video"
            tmp.resolve("video.m4s.part").writeText("abc")
            tmp.resolve("video.m4s.part.meta").writeText("$url?sign=old\n6\n\"same-media\"")
            val destination = tmp.resolve("video.m4s")
            HttpEngine(3_000, 3_000).downloadTo("$url?sign=new", emptyMap(), destination.absolutePath) { _, _ -> }
            assertEquals(listOf<Pair<String?, String?>>("bytes=3-" to "\"same-media\""), requests.toList())
            assertEquals("abcdef", destination.readText())
        } finally {
            server.stop(0)
            tmp.deleteRecursively()
        }
    }

    @Test
    fun rejectsMismatchedRangesAndSafelyRestarts() {
        val data = ByteArray(128 * 1024) { (it * 7).toByte() }
        val requests = CopyOnWriteArrayList<String>()
        val tmp = Files.createTempDirectory("ailiaili-transfer").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/video") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range").orEmpty()
            requests += range
            try {
                if (range.isNotEmpty()) exchange.responseHeaders.add("Content-Range", "bytes 1-100/999")
                exchange.responseHeaders.add("ETag", "\"fixture\"")
                exchange.sendResponseHeaders(if (range.isEmpty()) 200 else 206, data.size.toLong())
                exchange.responseBody.use { it.write(data) }
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/video"
            val destination = tmp.resolve("video.m4s")
            val engine = HttpEngine(3_000, 3_000)
            val control = DownloadCancellation()
            assertFailsWith<CancellationException> {
                engine.downloadTo(url, emptyMap(), destination.absolutePath, control) { done, _ ->
                    if (done > 0) {
                        control.cancel()
                        control.check()
                    }
                }
            }
            val partial = tmp.resolve("video.m4s.part").length()
            engine.downloadTo(url, emptyMap(), destination.absolutePath) { _, _ -> }
            assertEquals(listOf("", "bytes=$partial-", ""), requests)
            assertContentEquals(data, destination.readBytes())
        } finally {
            server.stop(0)
            tmp.deleteRecursively()
        }
    }
}
