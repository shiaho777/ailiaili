package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackPreferenceTest {
    @Test
    fun sevenTwentyAndBelowPreferMuxedStream() {
        assertTrue(shouldPreferDurl(16))
        assertTrue(shouldPreferDurl(32))
        assertTrue(shouldPreferDurl(64))
    }

    @Test
    fun higherQualitiesKeepDash() {
        assertFalse(shouldPreferDurl(80))
        assertFalse(shouldPreferDurl(112))
        assertFalse(shouldPreferDurl(116))
        assertFalse(shouldPreferDurl(120))
        assertFalse(shouldPreferDurl(127))
    }

    @Test
    fun autoQualityIsNotTreatedAsLowestQuality() {
        // 0 是“自动/最高可用”的旧语义，不能当成 360P 而误走 durl。
        assertFalse(shouldPreferDurl(0))
    }
}
