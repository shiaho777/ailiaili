package app.ailiaili.core.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeekWatchdogTest {
    @Test
    fun settledWithinTolerance() {
        assertTrue(isSeekSettled(60_000, 60_000))
        assertTrue(isSeekSettled(58_500, 60_000))
        assertTrue(isSeekSettled(61_500, 60_000))
    }

    @Test
    fun overshootCountsAsSettled() {
        assertTrue(isSeekSettled(61_000, 60_000))
    }

    @Test
    fun bouncedBackToStartIsNotSettled() {
        assertFalse(isSeekSettled(2_000, 3_600_000))
        assertFalse(isSeekSettled(0, 60_000))
    }

    @Test
    fun negativeTargetIsSettled() {
        assertTrue(isSeekSettled(0, -1))
    }
    @Test
    fun oldPositionAfterBackwardSeekIsNotConfirmation() {
        assertFalse(isSeekSettled(60_000, 12_000))
        assertTrue(isSeekSettled(12_600, 12_000))
    }

    @Test
    fun earlyEndRequiresRecovery() {
        assertFalse(isCrediblePlaybackEnd(18_000, 120_000))
        assertTrue(isCrediblePlaybackEnd(117_000, 120_000))
        assertTrue(isCrediblePlaybackEnd(1_000, 0))
    }

}
