package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveMsgSendFormTest {
    @Test
    fun validFormCarriesRoomAndText() {
        val form = liveMsgSendForm(12345, " 主播好 ").getOrThrow()
        assertEquals("12345", form["roomid"])
        assertEquals("主播好", form["msg"])
        assertEquals("16777215", form["color"])
        assertEquals("1", form["mode"])
        assertTrue(form.getValue("rnd").toLong() > 0)
    }

    @Test
    fun rejectsBadRoomEmptyAndOverlong() {
        assertTrue(liveMsgSendForm(0, "hi").isFailure)
        assertTrue(liveMsgSendForm(1, "  ").isFailure)
        assertTrue(liveMsgSendForm(1, "字".repeat(31)).isFailure)
        assertTrue(liveMsgSendForm(1, "字".repeat(30)).isSuccess)
    }
}