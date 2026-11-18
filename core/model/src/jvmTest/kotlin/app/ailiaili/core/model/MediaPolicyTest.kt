package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPolicyTest {
    @Test
    fun allowsOfficialCdnHosts() {
        assertTrue(isAllowedMediaUrl("https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/x.mp4"))
        assertTrue(isAllowedMediaUrl("https://cn-jssz-cu-01-05.bilivideo.com/x.m4s?e=1"))
        assertTrue(isAllowedMediaUrl("https://xy120x120.biliapi.net/x.flv"))
        assertTrue(isAllowedMediaUrl("https://i0.hdslb.com/bfs/live/x.jpg"))
        assertTrue(isAllowedMediaUrl("https://bilivideo.com/x.mp4"))
    }

    @Test
    fun rejectsThirdPartyAndTricks() {
        assertFalse(isAllowedMediaUrl("https://evil.com/x.mp4"))
        assertFalse(isAllowedMediaUrl("https://bilivideo.com.evil.com/x.mp4"))
        assertFalse(isAllowedMediaUrl("https://evilbilivideo.com/x.mp4"))
        // IP 直连（v4/v6）
        assertFalse(isAllowedMediaUrl("https://1.2.3.4/x.mp4"))
        assertFalse(isAllowedMediaUrl("https://[2001:db8::1]/x.mp4"))
        // userinfo / fragment
        assertFalse(isAllowedMediaUrl("https://u:p@upos.bilivideo.com/x.mp4"))
        assertFalse(isAllowedMediaUrl("https://upos.bilivideo.com/x.mp4#frag"))
        // 非 HTTPS / 本地 / 非 HTTP 协议
        assertFalse(isAllowedMediaUrl("http://upos.bilivideo.com/x.mp4"))
        assertFalse(isAllowedMediaUrl("file:///etc/passwd"))
        assertFalse(isAllowedMediaUrl("rtmp://live.bilivideo.com/x"))
        assertFalse(isAllowedMediaUrl(""))
        assertFalse(isAllowedMediaUrl("not a url"))
    }

    @Test
    fun filtersListKeepingOrder() {
        val urls = listOf(
            "https://upos.bilivideo.com/a.m4s",
            "https://evil.com/b.m4s",
            "https://cn-jssz.bilivideo.cn/c.m4s",
        )
        assertEquals(
            listOf("https://upos.bilivideo.com/a.m4s", "https://cn-jssz.bilivideo.cn/c.m4s"),
            filterAllowedMediaUrls(urls),
        )
        assertEquals(emptyList(), filterAllowedMediaUrls(listOf("https://evil.com/x")))
    }
}
