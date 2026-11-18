package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class WbiTest {
    @Test
    fun mixinKeyMatchesKnownNavPair() {
        assertEquals(
            "ea1db124af3c7062474693fa704f4ff8",
            mixinKey("7cd084941338484aae1ad9425b84077c", "4932caff0ff746eab6f01bf08b70ac45"),
        )
    }

    @Test
    fun unsignedEncodingKeepsSearchPunctuation() {
        // 未签名请求（裸搜索端点等）不得套用 WBI 的字符剥离：
        // 搜索词里的 !'()* 必须原样编码送达，否则查询词被静默改写。
        assertEquals("Re%3A%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B%21", urlEncode("Re:从零开始!"))
        // * 是 RFC 3986 非保留字符，标准编码原样保留；!'() 必须百分号转义而非删除。
        assertEquals("a%28b%29%27c*d", urlEncode("a(b)'c*d"))
        assertEquals("a+b", urlEncode("a b"))
    }

    @Test
    fun wbiEncodingStillStripsPerSpec() {
        // 签名串的编码规范保持不变：URL 编码后去掉 !'()*。
        assertEquals("Re%3A%E4%BB%8E%E9%9B%B6%E5%BC%80%E5%A7%8B", wbiEnc("Re:从零开始!"))
        assertEquals("abcd", wbiEnc("a(b)'c*d"))
        assertEquals("a%20b", wbiEnc("a b"))
    }
}
