package app.ailiaili.core.data

import app.ailiaili.core.model.PlayInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadQueueTest {
    @Test
    fun retryingFailuresLeavesPausedTasksAloneAndWaitsForNetwork() = runBlocking {
        val tmp = Files.createTempDirectory("ailiaili-retry-queue").toFile()
        try {
            val failed = DownloadRequest("BV1xx411c7mD", 101, "First part", "", 80)
            val paused = failed.copy(cid = 202)
            val attempts = AtomicInteger()
            val info = PlayInfo("unused", null, emptyList(), 0, emptyList(), 80)
            val queue = DownloadQueue(
                AppFile(tmp.resolve("downloads.json").absolutePath),
                resolve = {
                    check(it == failed)
                    if (attempts.incrementAndGet() == 1) error("probe failed")
                    info
                },
                transfer = { _, _, _, _ -> null },
                removePartial = {},
            )
            queue.setNetwork(DownloadNetwork.Offline)
            queue.enqueue(listOf(failed, paused))
            queue.pause(paused.key)
            queue.setNetwork(DownloadNetwork.Available)
            withTimeout(5_000) { queue.tasks.first { it.first().phase == DownloadPhase.Failed } }

            queue.setNetwork(DownloadNetwork.Offline)
            queue.retryFailed()
            assertEquals(listOf(DownloadPhase.Queued, DownloadPhase.Paused), queue.tasks.value.map { it.phase })
            assertEquals(null, queue.tasks.value.first().error)
            assertEquals(1, attempts.get())

            queue.setNetwork(DownloadNetwork.Available)
            withTimeout(5_000) { queue.completedRevision.first { it == 1 } }
            assertEquals(listOf(paused), queue.tasks.value.map { it.request })
            assertEquals(2, attempts.get())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun changingToAMeteredNetworkSuspendsAndResumesTheSameTask() = runBlocking {
        val tmp = Files.createTempDirectory("ailiaili-network-queue").toFile()
        try {
            val item = DownloadRequest("BV1xx411c7mD", 101, "Part one", "", 80)
            val attempts = AtomicInteger()
            val info = PlayInfo("unused", null, emptyList(), 0, emptyList(), 80)
            val queue = DownloadQueue(
                AppFile(tmp.resolve("downloads.json").absolutePath),
                resolve = { info },
                transfer = { _, _, token, progress ->
                    progress(DownloadPhase.Video, 3, 10)
                    if (attempts.incrementAndGet() == 1) {
                        while (true) {
                            token.check()
                            Thread.sleep(10)
                        }
                    }
                    null
                },
                removePartial = {},
            )
            queue.enqueue(listOf(item))
            withTimeout(5_000) { queue.tasks.first { it.singleOrNull()?.phase == DownloadPhase.Video } }
            queue.setNetwork(DownloadNetwork.Metered)
            withTimeout(5_000) { queue.tasks.first { it.singleOrNull()?.phase == DownloadPhase.Queued } }
            assertEquals(1, attempts.get())

            queue.pauseAll()
            assertEquals(DownloadPhase.Paused, queue.tasks.value.single().phase)
            queue.resumeAll()
            assertEquals(DownloadPhase.Queued, queue.tasks.value.single().phase)
            queue.setNetwork(DownloadNetwork.Available)
            withTimeout(5_000) { queue.completedRevision.first { it == 1 } }
            assertEquals(2, attempts.get())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun pausesRestoresAndResumesIndependentParts() = runBlocking {
        val tmp = Files.createTempDirectory("ailiaili-queue").toFile()
        try {
            val file = AppFile(tmp.resolve("downloads.json").absolutePath)
            val first = DownloadRequest("BV1xx411c7mD", 101, "Part one", "", 80)
            val second = first.copy(cid = 202, title = "Part two")
            val attempts = AtomicInteger()
            val info = PlayInfo("unused", null, emptyList(), 0, emptyList(), 80)
            val queue = DownloadQueue(
                file,
                resolve = { info },
                transfer = { request, _, token, progress ->
                    progress(DownloadPhase.Video, 3, 10)
                    if (request == first && attempts.incrementAndGet() == 1) {
                        while (true) {
                            token.check()
                            Thread.sleep(10)
                        }
                    }
                    null
                },
                removePartial = {},
            )

            assertEquals(2, queue.enqueue(listOf(first, first, second)))
            withTimeout(5_000) { queue.tasks.first { it.firstOrNull()?.phase == DownloadPhase.Video } }
            queue.pause(first.key)
            withTimeout(5_000) { queue.completedRevision.first { it == 1 } }
            assertEquals(DownloadPhase.Paused, queue.tasks.value.single().phase)

            val restored = DownloadQueue(file, resolve = { info }, transfer = { _, _, _, _ -> null }, removePartial = {})
            assertEquals(listOf(first), restored.tasks.value.map { it.request })
            assertEquals(DownloadPhase.Paused, restored.tasks.value.single().phase)

            queue.resume(first.key)
            withTimeout(5_000) { queue.completedRevision.first { it == 2 } }
            assertEquals(emptyList(), queue.tasks.value)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
