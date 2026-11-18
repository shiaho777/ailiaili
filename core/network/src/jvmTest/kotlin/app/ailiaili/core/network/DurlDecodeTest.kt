package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DurlDecodeTest {
    @Test
    fun decodesSingleSegmentMuxedStream() {
        val json = JSONObject(
            """
            {
              "quality": 64,
              "timelength": 120000,
              "accept_quality": [64, 32, 16],
              "durl": [
                { "url": "https://upos-sz-mirrorcos.bilivideo.com/video.mp4", "order": 1 }
              ]
            }
            """.trimIndent(),
        )
        val info = decodeDurl(json)
        assertEquals("https://upos-sz-mirrorcos.bilivideo.com/video.mp4", info?.videoUrl)
        assertEquals(null, info?.audioUrl)
        assertEquals(64, info?.actualQn)
        assertEquals(120000L, info?.durationMs)
        assertEquals(listOf(64, 32, 16), info?.qualities?.map { it.qn })
    }

    @Test
    fun rejectsMultiSegmentStreams() {
        val json = JSONObject(
            """
            {
              "quality": 64,
              "durl": [
                { "url": "https://example.com/1.mp4", "order": 1 },
                { "url": "https://example.com/2.mp4", "order": 2 }
              ]
            }
            """.trimIndent(),
        )
        assertNull(decodeDurl(json))
    }

    @Test
    fun rejectsEmptyStreams() {
        assertNull(decodeDurl(JSONObject("""{"durl":[]}""")))
        assertNull(decodeDurl(JSONObject("""{}""")))
    }

    @Test
    fun rejectsNonOfficialHosts() {
        // 播放源白名单：第三方域名/IP 直连/带凭据的地址一律判不可用。
        assertNull(decodeDurl(JSONObject("""{"durl":[{"url":"https://evil.example.com/v.mp4"}]}""")))
        assertNull(decodeDurl(JSONObject("""{"durl":[{"url":"https://1.2.3.4/v.mp4"}]}""")))
        assertNull(decodeDurl(JSONObject("""{"durl":[{"url":"https://u:p@upos.bilivideo.com/v.mp4"}]}""")))
        assertNull(decodeDurl(JSONObject("""{"durl":[{"url":"http://upos.bilivideo.com/v.mp4"}]}""")))
    }
}
