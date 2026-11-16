package app.ailiaili.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidBufferSpecTest {
    @Test
    fun weakNetworkBuffersDeeper() {
        val weak = BufferPolicy.androidDurations(3_000, live = false)
        val fast = BufferPolicy.androidDurations(500, live = false)
        assertTrue(weak.minBufferMs > fast.minBufferMs)
        assertTrue(weak.maxBufferMs > fast.maxBufferMs)
        assertTrue(weak.rebufferMs > fast.rebufferMs)
    }

    @Test
    fun fastNetworkStartsSooner() {
        val fast = BufferPolicy.androidDurations(500, live = false)
        assertEquals(500, fast.playbackMs)
        assertTrue(fast.maxBufferMs <= 15_000)
    }

    @Test
    fun liveStaysLowLatency() {
        val live = BufferPolicy.androidDurations(3_000, live = true)
        assertTrue(live.maxBufferMs <= 20_000)
        assertTrue(live.playbackMs <= 2_000)
        val vod = BufferPolicy.androidDurations(3_000, live = false)
        assertTrue(live.maxBufferMs < vod.maxBufferMs)
    }

    @Test
    fun parametersStayOrdered() {
        listOf(300, 1_000, 2_000, 3_000).forEach { ms ->
            listOf(true, false).forEach { live ->
                val spec = BufferPolicy.androidDurations(ms, live)
                assertTrue(spec.playbackMs <= spec.rebufferMs, "playback<=rebuffer for $ms live=$live")
                assertTrue(spec.rebufferMs <= spec.minBufferMs, "rebuffer<=min for $ms live=$live")
                assertTrue(spec.minBufferMs <= spec.maxBufferMs, "min<=max for $ms live=$live")
            }
        }
    }

    @Test
    fun clampsOutOfRangeInput() {
        val spec = BufferPolicy.androidDurations(999_999, live = false)
        val clamped = BufferPolicy.androidDurations(3_000, live = false)
        assertEquals(clamped, spec)
    }
}
