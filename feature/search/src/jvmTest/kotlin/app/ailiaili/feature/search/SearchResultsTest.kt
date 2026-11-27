package app.ailiaili.feature.search

import app.ailiaili.core.model.BangumiItem
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.SearchPage
import app.ailiaili.core.model.SearchUser
import app.ailiaili.core.model.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchResultsTest {
    private val query = SearchQuery("committed query", ContentKind.Video, SearchOrder.Latest)

    @Test
    fun failedPageRetainsResultsAndRetriesTheSamePage() {
        val video = VideoItem(bvid = "first")
        val first = SearchResults(query).receive(1, Result.success(SearchPage(listOf(video), hasMore = true)))
        val failed = first.receive(2, Result.failure(IllegalStateException("offline")))

        assertEquals(query, failed.query)
        assertEquals(2, failed.nextPage)
        assertEquals(2, failed.requestedPage)
        assertEquals(listOf(video), failed.content.items)
        assertTrue(failed.content.hasMore)
        assertFalse(failed.loading)
        assertEquals("offline", failed.error)

        val nextVideo = VideoItem(bvid = "second")
        val retried = failed.receive(2, Result.success(SearchPage(listOf(video, nextVideo), hasMore = false)))
        assertEquals(listOf(video, nextVideo), retried.content.items)
        assertEquals(3, retried.nextPage)
        assertFalse(retried.content.hasMore)
        assertNull(retried.error)
        assertNull(retried.requestedPage)
    }

    @Test
    fun refreshingKeepsCurrentPagesOnFailureAndRestartsPaginationOnSuccess() {
        val old = VideoItem(aid = 1)
        val latest = VideoItem(aid = 2)
        val loaded = SearchResults(query).receive(2, Result.success(SearchPage(listOf(old), hasMore = false)))
        val failed = loaded.receive(1, Result.failure(IllegalStateException("offline")))
        assertEquals(listOf(old), failed.content.items)
        assertEquals(1, failed.requestedPage)

        val refreshed = failed.receive(1, Result.success(SearchPage(listOf(latest), hasMore = true)))
        assertEquals(listOf(latest), refreshed.content.items)
        assertEquals(2, refreshed.nextPage)
        assertTrue(refreshed.content.hasMore)
        assertNull(refreshed.requestedPage)
    }

    @Test
    fun blockedPageDoesNotReplaceContentOrAdvanceCursor() {
        val first = SearchResults(query).receive(1, Result.success(SearchPage(listOf(VideoItem(aid = 1)), hasMore = true)))
        val blocked = first.receive(2, Result.success(SearchPage(emptyList(), hasMore = false, blocked = true)))

        assertEquals(first.content, blocked.content)
        assertEquals(first.nextPage, blocked.nextPage)
        assertNotNull(blocked.error)
    }

    @Test
    fun pagesDeduplicateEachResultTypeByStableId() {
        val video = VideoItem(aid = 1)
        val season = BangumiItem(seasonId = 2, title = "season")
        val user = SearchUser(mid = 3, name = "user")
        val duplicate = SearchPage(listOf(video, video), listOf(season, season), true, userItems = listOf(user, user))
        val first = SearchResults(query).receive(1, Result.success(duplicate))
        val second = first.receive(2, Result.success(duplicate))

        assertEquals(listOf(video), second.content.items)
        assertEquals(listOf(season), second.content.bangumiItems)
        assertEquals(listOf(user), second.content.userItems)
    }
}
