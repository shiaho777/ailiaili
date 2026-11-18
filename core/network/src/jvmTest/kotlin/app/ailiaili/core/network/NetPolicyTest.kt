package app.ailiaili.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetPolicyTest {
    @Test
    fun transientStatusOnly() {
        assertTrue(isTransientHttpStatus(500))
        assertTrue(isTransientHttpStatus(502))
        assertTrue(isTransientHttpStatus(429))
        assertTrue(isTransientHttpStatus(408))
        assertFalse(isTransientHttpStatus(200))
        assertFalse(isTransientHttpStatus(304))
        assertFalse(isTransientHttpStatus(403))
        assertFalse(isTransientHttpStatus(404))
        assertFalse(isTransientHttpStatus(412))
    }

    @Test
    fun backoffGrowsWithJitterBounds() {
        assertTrue(retryBackoffMs(0) < retryBackoffMs(1))
        assertTrue(retryBackoffMs(1) < retryBackoffMs(2))
        assertTrue(retryBackoffMs(0, 0.2) <= 360)
        assertTrue(retryBackoffMs(0, -0.2) >= 240)
    }

    @Test
    fun singleFlightMergesConcurrentCalls() = runBlocking {
        val flight = SingleFlight<String, Int>()
        val calls = AtomicInteger(0)
        val (a, b) = coroutineScope {
            val first = async {
                flight.run("k") {
                    calls.incrementAndGet()
                    delay(50)
                    42
                }
            }
            val second = async {
                delay(10)
                flight.run("k") {
                    calls.incrementAndGet()
                    99
                }
            }
            first.await() to second.await()
        }
        assertEquals(42, a)
        assertEquals(42, b)
        assertEquals(1, calls.get())
    }

    @Test
    fun singleFlightAllowsRetryAfterFailure() = runBlocking {
        val flight = SingleFlight<String, Int>()
        val result = runCatching { flight.run("k") { error("boom") } }
        assertTrue(result.isFailure)
        // 失败后不留残留：下一次调用正常执行。
        assertEquals(7, flight.run("k") { 7 })
    }

    @Test
    fun singleFlightOwnerCancellationDoesNotPoisonKey() = runBlocking {
        val flight = SingleFlight<String, Int>()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        // 独立作用域：不能挂在 runBlocking 下，否则取消前要先等子协程。
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val job = scope.launch {
            flight.run("k") {
                started.complete(Unit)
                delay(10_000)
                1
            }
        }
        started.await()
        job.cancel()
        job.join()
        scope.cancel()
        // 取消路径的清理必须执行，否则该 key 的单飞永久失效。
        assertEquals(7, flight.run("k") { 7 })
    }

    @Test
    fun shortTtlCacheExpiresAndEvicts() = runBlocking {
        var now = 0L
        val cache = ShortTtlCache<String, String>(maxEntries = 2, clock = { now })
        cache.put("a", "1", ttlMs = 100)
        assertEquals("1", cache.get("a"))
        now = 101
        assertNull(cache.get("a"))
        cache.put("b", "2", ttlMs = 1000)
        cache.put("c", "3", ttlMs = 1000)
        cache.put("d", "4", ttlMs = 1000)
        // 容量 2：最老的 b 被淘汰。
        assertNull(cache.get("b"))
        assertEquals("3", cache.get("c"))
        assertEquals("4", cache.get("d"))
    }
}
