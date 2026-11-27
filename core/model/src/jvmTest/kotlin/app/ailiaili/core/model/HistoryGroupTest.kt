package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryGroupTest {
    private fun item(title: String, viewAt: Long, author: String = "UP") =
        VideoItem(bvid = "BV-$title", title = title, author = author, pubdate = viewAt)

    @Test
    fun groupsByDayKeepingOrder() {
        val items = listOf(
            item("甲", 100),
            item("乙", 100),
            item("丙", 50),
            item("丁", 0),
        )
        // formatDay(100)/formatDay(50) 在不同时区可能同日也可能跨日，
        // 用函数自身算出的 key 做断言，保证时区无关。
        val today = formatDay(100)
        val yesterday = formatDay(50)
        val groups = historyDayGroups(items, todayKey = today, yesterdayKey = yesterday)
        if (today == yesterday) {
            assertEquals(2, groups.size)
            assertEquals("今天", groups[0].first)
            assertEquals(listOf("甲", "乙", "丙"), groups[0].second.map { it.title })
        } else {
            assertEquals(3, groups.size)
            assertEquals("今天", groups[0].first)
            assertEquals("昨天", groups[1].first)
            assertEquals(listOf("丙"), groups[1].second.map { it.title })
        }
        assertEquals("更早", groups.last().first)
        assertEquals(listOf("丁"), groups.last().second.map { it.title })
    }

    @Test
    fun emptyInputGivesEmptyGroups() {
        assertTrue(historyDayGroups(emptyList(), "a", "b").isEmpty())
    }

    @Test
    fun searchMatchesTitleOrAuthorCaseInsensitive() {
        val items = listOf(item(" Kotlin 教程 ", 1, "张三"), item("音乐", 2, "LiSi"))
        assertEquals(listOf(" Kotlin 教程 "), historySearch(items, "kotlin").map { it.title })
        assertEquals(listOf("音乐"), historySearch(items, "lisi").map { it.title })
        assertEquals(items, historySearch(items, "  "))
        assertTrue(historySearch(items, "不存在").isEmpty())
    }
}