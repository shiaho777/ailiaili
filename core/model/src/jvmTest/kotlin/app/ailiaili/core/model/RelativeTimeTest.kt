package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {
    private val now = 1_800_000_000L

    @Test
    fun formatsRecentDurations() {
        assertEquals("刚刚", formatRelativeTime(now - 10, now))
        assertEquals("5 分钟前", formatRelativeTime(now - 300, now))
        assertEquals("2 小时前", formatRelativeTime(now - 7_200, now))
        assertEquals("3 天前", formatRelativeTime(now - 3 * 86_400, now))
    }

    @Test
    fun blankForMissingTimestamp() {
        assertEquals("", formatRelativeTime(0, now))
    }
}
