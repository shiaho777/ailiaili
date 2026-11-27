package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlParserTest {
    @Test
    fun momentLinksRoundTripAtSecondPrecision() {
        val bvid = "BV1xx411c7mD"
        assertEquals(UrlHit.Video(bvid, 3, 62_000), UrlParser.parse(videoMomentUrl(bvid, 3, 62_500)))
        assertEquals(UrlHit.Video(bvid, 1, 0), UrlParser.parse(videoMomentUrl(bvid, 1, 0)))
    }

    @Test
    fun retainsValidParametersInPastedTextAndIgnoresInvalidParameters() {
        val bvid = "BV1xx411c7mD"
        assertEquals(
            UrlHit.Video(bvid, 2, 12_345),
            UrlParser.parse("分享 https://www.bilibili.com/video/$bvid/?t=12.345&p=2#t=8 继续分享"),
        )
        assertEquals(UrlHit.Video(bvid), UrlParser.parse("https://www.bilibili.com/video/$bvid/?p=0&t=-10"))
        assertEquals(UrlHit.Video(bvid), UrlParser.parse(bvid))
        assertEquals(UrlHit.Video(UrlParser.avToBv(170001), 2, 15_000), UrlParser.parse("https://www.bilibili.com/video/av170001/?p=2&t=15"))
    }
}
