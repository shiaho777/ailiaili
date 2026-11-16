package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkScannerTest {
    @Test
    fun findsHttpUrlAndTrimsTrailingPunctuation() {
        val text = "看这个 https://example.com/a?b=1，很有意思"
        val links = scanTextLinks(text)
        assertEquals(1, links.size)
        assertEquals("https://example.com/a?b=1", links[0].url)
        assertNull(links[0].bvid)
    }

    @Test
    fun bareBvidOpensInApp() {
        val links = scanTextLinks("强烈推荐 BV1xx411c7mD 这一期")
        assertEquals(1, links.size)
        assertEquals("BV1xx411c7mD", links[0].bvid)
    }

    @Test
    fun urlWithBvidNotDuplicated() {
        val links = scanTextLinks("https://www.bilibili.com/video/BV1xx411c7mD?spm_id_from=1")
        assertEquals(1, links.size)
        assertEquals("BV1xx411c7mD", links[0].bvid)
    }

    @Test
    fun chineseBracketNotPartOfUrl() {
        val links = scanTextLinks("（详见 https://example.com/x）")
        assertEquals("https://example.com/x", links.single().url)
    }

    @Test
    fun multipleLinksSortedByPosition() {
        val links = scanTextLinks("BV1aa411c7mD 和 https://a.b/c 以及 BV1bb411c7mD")
        assertEquals(3, links.size)
        assertTrue(links[0].start < links[1].start && links[1].start < links[2].start)
    }

    @Test
    fun plainSchemeAloneIgnored() {
        assertTrue(scanTextLinks("https:// 不是链接").isEmpty())
    }
}
