package app.ailiaili.core.data

import kotlinx.coroutines.runBlocking
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
