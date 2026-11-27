package app.ailiaili.feature.search

import app.ailiaili.core.model.UrlHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchSuggestionsTest {
    @Test
    fun startsWithRanksAheadOfContainsAndMatchingHistoryKeepsItsSource() {
        val suggestions = searchSuggestions(
            " kot ",
            recent = listOf("Learn Kotlin", "KOTLIN basics", "KOT", "Java"),
            hot = listOf("Kotlin basics", "Kotlin Compose", "Kotlin native"),
        )

        assertEquals(listOf("KOTLIN basics", "Kotlin Compose", "Kotlin native", "Learn Kotlin"), suggestions.map { it.value })
        assertEquals(SearchSuggestionSource.Recent, suggestions.first().source)
        assertEquals(SearchSuggestionSource.Trending, suggestions[1].source)
    }

    @Test
    fun limitsSuggestionsAndDoesNotSuggestOnBlankInput() {
        val history = List(9) { "k$it" }

        assertEquals(history.take(5), searchSuggestions("k", history, emptyList()).map { it.value })
        assertEquals(emptyList(), searchSuggestions("  ", history, emptyList()))
    }

    @Test
    fun linkPreviewKeepsRawTextAndShowsPartAndTimestamp() {
        val raw = "https://www.bilibili.com/video/BV1xx411c7mD/?p=3&t=68"

        assertEquals(
            SearchSuggestion(raw, "打开视频 · 第 3 P · 1:08", SearchSuggestionSource.Link),
            linkSuggestion(raw, UrlHit.Video("BV1xx411c7mD", 3, 68_000)),
        )
        assertNull(linkSuggestion("kotlin", UrlHit.Keyword("kotlin")))
    }
}
