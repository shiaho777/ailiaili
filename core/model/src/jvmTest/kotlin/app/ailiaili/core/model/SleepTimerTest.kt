package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerTest {
    @Test
    fun timedOptionFiresAtDeadlineRegardlessOfPlayState() {
        assertFalse(sleepTimerDue(30, armed = true, deadlineMs = 10_000, nowMs = 9_999, ended = false, live = false))
        assertTrue(sleepTimerDue(30, armed = true, deadlineMs = 10_000, nowMs = 10_000, ended = false, live = true))
    }

    @Test
    fun endOfVideoOnlyFiresForVodWhenEnded() {
        assertFalse(sleepTimerDue(0, armed = true, deadlineMs = 0, nowMs = 1, ended = false, live = false))
        assertFalse(sleepTimerDue(0, armed = true, deadlineMs = 0, nowMs = 1, ended = true, live = true))
        assertTrue(sleepTimerDue(0, armed = true, deadlineMs = 0, nowMs = 1, ended = true, live = false))
    }

    @Test
    fun disabledOrUnarmedNeverFires() {
        assertFalse(sleepTimerDue(-1, armed = true, deadlineMs = 1, nowMs = 999_999, ended = true, live = false))
        assertFalse(sleepTimerDue(30, armed = false, deadlineMs = 1, nowMs = 999_999, ended = true, live = false))
    }

    @Test
    fun clockFormatsAndClamps() {
        assertEquals("00:00", sleepTimerClock(-5))
        assertEquals("00:59", sleepTimerClock(59_400))
        assertEquals("90:00", sleepTimerClock(90 * 60_000L))
    }

    @Test
    fun labelsCoverAllOptions() {
        assertEquals("播完当前", sleepTimerLabel(0))
        assertEquals("定时关闭", sleepTimerLabel(-1))
        SLEEP_TIMER_OPTIONS.filter { it > 0 }.forEach { assertEquals("$it 分钟", sleepTimerLabel(it)) }
    }
}