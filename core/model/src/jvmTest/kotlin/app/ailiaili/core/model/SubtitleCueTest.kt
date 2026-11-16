package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleCueTest {
    private val cues = listOf(
        SubtitleCue(0.0, 2.0, "第一句"),
        SubtitleCue(2.5, 4.0, "第二句"),
        SubtitleCue(10.0, 12.0, "第三句"),
    )

    @Test
    fun findsCueAtPosition() {
        assertEquals("第一句", subtitleCueAt(cues, 1.0)?.text)
        assertEquals("第二句", subtitleCueAt(cues, 2.5)?.text)
        assertEquals("第三句", subtitleCueAt(cues, 11.9)?.text)
    }

    @Test
    fun gapsAndEdgesReturnNull() {
        assertNull(subtitleCueAt(cues, 2.2))
        assertNull(subtitleCueAt(cues, 4.0))
        assertNull(subtitleCueAt(cues, 99.0))
        assertNull(subtitleCueAt(emptyList(), 1.0))
    }

    @Test
    fun positiveDelayShiftsCueLater() {
        // 字幕延后 0.5s：t=2.6 时应显示 2.1 位置的内容（第一句已结束→空隙）。
        assertNull(subtitleCueAt(cues, 2.6, delaySec = 0.5))
        assertEquals("第二句", subtitleCueAt(cues, 3.2, delaySec = 0.5)?.text)
    }

    @Test
    fun negativeDelayShowsCueEarlier() {
        assertEquals("第二句", subtitleCueAt(cues, 2.2, delaySec = -0.5)?.text)
    }
}
