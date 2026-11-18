package app.ailiaili.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TtlCacheTest {
    @Test
    fun cachesSuccessfulValuesUntilExpiry() = runBlocking {
        var now = 1_000L
        var loads = 0
        val cache = TtlCache<String, String>(ttlMs = 100, clock = { now })
        suspend fun load() = cache.get("k") {
            loads++
            Result.success("v$loads")
        }

        assertEquals("v1", load().getOrThrow())
        assertEquals("v1", load().getOrThrow())
        assertEquals(1, loads)

        now += 101
        assertEquals("v2", load().getOrThrow())
        assertEquals(2, loads)
    }

    @Test
    fun doesNotCacheFailures() = runBlocking {
        var loads = 0
        val cache = TtlCache<String, String>(ttlMs = 10_000)
        suspend fun load() = cache.get("k") {
            loads++
            if (loads == 1) Result.failure(IllegalStateException("boom")) else Result.success("ok")
        }

        assertTrue(load().isFailure)
        assertEquals("ok", load().getOrThrow())
        assertEquals(2, loads)
    }

    @Test
    fun concurrentMissesMergeIntoOneLoad() = runBlocking {
        val cache = TtlCache<String, String>(ttlMs = 10_000)
        val calls = AtomicInteger(0)
        val (a, b) = coroutineScope {
            val first = async {
                cache.get("k") {
                    calls.incrementAndGet()
                    delay(50)
                    Result.success("v")
                }
            }
            val second = async {
                delay(10)
                cache.get("k") {
                    calls.incrementAndGet()
                    Result.success("other")
                }
            }
            first.await() to second.await()
        }
        assertEquals("v", a.getOrThrow())
        assertEquals("v", b.getOrThrow())
        assertEquals(1, calls.get())
    }

    @Test
    fun ownerCancellationDoesNotPoisonKey() = runBlocking {
        val cache = TtlCache<String, String>(ttlMs = 10_000)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        // 独立作用域：不能挂在 runBlocking 下，否则取消前要先等子协程。
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val job = scope.launch {
            cache.get("k") {
                started.complete(Unit)
                delay(10_000)
                Result.success("late")
            }
        }
        started.await()
        job.cancel()
        job.join()
        scope.cancel()
        // 取消清理必须执行：残留异常态 deferred 会让该 key 永远各自 load。
        assertEquals("ok", cache.get("k") { Result.success("ok") }.getOrThrow())
    }

    @Test
    fun evictsOldestWhenFull() = runBlocking {
        var loads = 0
        val cache = TtlCache<Int, Int>(ttlMs = 10_000, maxEntries = 2)
        repeat(3) { i -> cache.get(i) { loads++; Result.success(i) } }
        assertEquals(3, loads)

        cache.get(0) { loads++; Result.success(0) }
        assertEquals(4, loads)

        cache.get(2) { loads++; Result.success(2) }
        assertEquals(4, loads)
    }
}
