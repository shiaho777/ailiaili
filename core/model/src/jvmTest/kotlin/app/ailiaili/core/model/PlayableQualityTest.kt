package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayableQualityTest {
    @Test
    fun keepsPlayableQnUntouched() {
        assertEquals(80, clampPlayableQn(80, listOf(120, 80, 64, 32)))
    }

    @Test
    fun clampsUnplayablePreferenceToHighestLowerTier() {
        // 用户偏好 8K/杜比视界/HDR，但设备只能播到 4K：降到 4K 而不是报错。
        assertEquals(120, clampPlayableQn(127, listOf(120, 116, 80, 64)))
        assertEquals(120, clampPlayableQn(126, listOf(120, 116, 80, 64)))
        assertEquals(64, clampPlayableQn(125, listOf(64, 32)))
    }

    @Test
    fun fallsBackToLowestWhenNothingBelow() {
        assertEquals(80, clampPlayableQn(64, listOf(120, 80)))
    }

    @Test
    fun emptyAvailabilityKeepsPreference() {
        assertEquals(127, clampPlayableQn(127, emptyList()))
    }

    @Test
    fun filtersUnplayableTiersFromMenu() {
        val all = listOf(127, 126, 125, 120, 80).map { Quality(it, qnLabel(it)) }
        val filtered = filterPlayableQualities(all)
        assertEquals(listOf(120, 80), filtered.map { it.qn })
    }

    @Test
    fun keepsOriginalWhenEverythingUnplayable() {
        val all = listOf(127, 126).map { Quality(it, qnLabel(it)) }
        assertEquals(all, filterPlayableQualities(all))
    }
}
