package app.ailiaili.core.player

import kotlin.test.Test
import kotlin.test.assertEquals

class BufferPolicyTest {
    @Test
    fun autoWithoutMeasurementFallsBackToOldDefault() {
        assertEquals(
            1_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 0),
        )
    }

    @Test
    fun autoScalesWithMeasuredBandwidth() {
        // 25.6 Mbps 富余 -> 500ms
        assertEquals(
            500,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 3_200_000),
        )
        // 8 Mbps 左右 -> 1000ms
        assertEquals(
            1_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 1_000_000),
        )
        // 4 Mbps 紧张 -> 2000ms
        assertEquals(
            2_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 500_000),
        )
        // 1.5 Mbps 弱网 -> 3000ms
        assertEquals(
            3_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 150_000),
        )
    }

    @Test
    fun shortVideoCapsAutoBuffer() {
        assertEquals(
            1_000,
            BufferPolicy.resolve(BufferMediaKind.VodSingle, true, 1_000, true, 100_000),
        )
    }

    @Test
    fun manualModeClampsAndKeepsLiveFloor() {
        assertEquals(
            300,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 100, false, 0),
        )
        assertEquals(
            3_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 9_999, false, 0),
        )
        assertEquals(
            1_000,
            BufferPolicy.resolve(BufferMediaKind.Live, false, 300, false, 0),
        )
    }

    @Test
    fun stallBoostAppliesToNextMedia() {
        assertEquals(
            2_000,
            BufferPolicy.resolve(BufferMediaKind.VodDash, false, 1_000, true, 1_000_000, 1_000),
        )
        assertEquals(
            3_000,
            BufferPolicy.resolve(BufferMediaKind.Live, false, 1_000, true, 0, 1_000),
        )
    }
}
