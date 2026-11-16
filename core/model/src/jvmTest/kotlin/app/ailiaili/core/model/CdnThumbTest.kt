package app.ailiaili.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CdnThumbTest {
    @Test
    fun appendsSizeSuffix() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/x.jpg@800w_500h",
            cdnThumb("https://i0.hdslb.com/bfs/archive/x.jpg", 800, 500),
        )
    }

    @Test
    fun roundsToTenForCacheHits() {
        assertEquals(
            "https://i0.hdslb.com/a.jpg@270w_170h",
            cdnThumb("https://i0.hdslb.com/a.jpg", 272, 170),
        )
    }

    @Test
    fun keepsQueryAfterSuffix() {
        assertEquals(
            "https://i0.hdslb.com/a.jpg@80w_80h?x=1",
            cdnThumb("https://i0.hdslb.com/a.jpg?x=1", 80),
        )
    }

    @Test
    fun skipsAlreadyProcessedAndForeignAndGif() {
        val processed = "https://i0.hdslb.com/a.jpg@100w"
        assertEquals(processed, cdnThumb(processed, 800))
        val foreign = "https://example.com/a.jpg"
        assertEquals(foreign, cdnThumb(foreign, 800))
        val gif = "https://i0.hdslb.com/a.gif"
        assertEquals(gif, cdnThumb(gif, 800))
        assertEquals("", cdnThumb("", 800))
    }

    @Test
    fun normalizesProtocolRelativeBeforeThumb() {
        assertEquals(
            "https://i0.hdslb.com/a.jpg@80w_80h",
            cdnThumb(normalizeUrl("//i0.hdslb.com/a.jpg"), 80),
        )
    }
}
