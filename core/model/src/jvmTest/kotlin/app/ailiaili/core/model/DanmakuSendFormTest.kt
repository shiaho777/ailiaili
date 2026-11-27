package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DanmakuSendFormTest {
    @Test
    fun validFormCarriesAllFields() {
        val form = danmakuSendForm(123, 456, " 前方高能 ", 90_000, 1, 0xFFFFFF).getOrThrow()
        assertEquals("1", form["type"])
        assertEquals("456", form["oid"])
        assertEquals("123", form["aid"])
        assertEquals("前方高能", form["msg"])
        assertEquals("90000", form["progress"])
        assertEquals("1", form["mode"])
        assertEquals("16777215", form["color"])
        assertEquals("25", form["fontsize"])
        assertEquals("0", form["pool"])
        assertTrue(form.getValue("rnd").toLong() > 0)
    }

    @Test
    fun rejectsEmptyAndOverlong() {
        assertTrue(danmakuSendForm(1, 2, "   ", 0, 1, 0xFFFFFF).isFailure)
        assertTrue(danmakuSendForm(1, 2, "字".repeat(101), 0, 1, 0xFFFFFF).isFailure)
        assertTrue(danmakuSendForm(1, 2, "字".repeat(100), 0, 1, 0xFFFFFF).isSuccess)
    }

    @Test
    fun rejectsBadIdsModesAndClampsColor() {
        assertTrue(danmakuSendForm(0, 2, "hi", 0, 1, 0xFFFFFF).isFailure)
        assertTrue(danmakuSendForm(1, 0, "hi", 0, 1, 0xFFFFFF).isFailure)
        assertTrue(danmakuSendForm(1, 2, "hi", 0, 7, 0xFFFFFF).isFailure)
        val form = danmakuSendForm(1, 2, "hi", -5, 5, 0x1FF0000).getOrThrow()
        assertEquals("0", form["progress"])
        assertEquals("16711680", form["color"])
    }
}