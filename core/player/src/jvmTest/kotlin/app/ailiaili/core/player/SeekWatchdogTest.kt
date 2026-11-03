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
}
