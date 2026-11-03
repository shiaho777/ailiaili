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
                { "url": "https://example.com/video.mp4", "order": 1 }
              ]
            }
            """.trimIndent(),
        )
        val info = decodeDurl(json)
        assertEquals("https://example.com/video.mp4", info?.videoUrl)
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
}
