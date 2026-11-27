package app.ailiaili.feature.player

import app.ailiaili.core.model.Quality
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerQualityTest {
    @Test
    fun switchingToLowQualityRetainsKnownHigherQualities() {
        val previous = listOf(Quality(80, "1080P"), Quality(64, "720P"))
        val current = listOf(Quality(64, "720P"), Quality(32, "480P"), Quality(16, "360P"))
        assertEquals(listOf(80, 64, 32, 16), mergeQualityCatalog(previous, current).map { it.qn })
    }
}
