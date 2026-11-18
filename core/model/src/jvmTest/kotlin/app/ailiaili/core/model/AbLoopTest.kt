package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AbLoopTest {
    @Test
    fun rewindsAtOrPastB() {
        assertTrue(abLoopShouldRewind(10_000, 20_000, 20_000))
        assertTrue(abLoopShouldRewind(10_000, 20_000, 20_400))
        assertFalse(abLoopShouldRewind(10_000, 20_000, 19_999))
    }

    @Test
    fun unsetPointsNeverFire() {
        assertFalse(abLoopShouldRewind(-1, 20_000, 30_000))
        assertFalse(abLoopShouldRewind(10_000, -1, 30_000))
        assertFalse(abLoopShouldRewind(-1, -1, 0))
    }

    @Test
    fun requiresAtLeastOneSecondSpan() {
        assertFalse(abLoopShouldRewind(10_000, 10_500, 11_000))
        assertFalse(abLoopShouldRewind(10_000, 11_000, 12_000))
        assertTrue(abLoopShouldRewind(10_000, 11_001, 12_000))
        // B 在 A 之前（用户乱序设置）不触发。
        assertFalse(abLoopShouldRewind(20_000, 10_000, 30_000))
    }
}