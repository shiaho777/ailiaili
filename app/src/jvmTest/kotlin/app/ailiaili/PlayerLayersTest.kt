package app.ailiaili

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerLayersTest {
    @Test
    fun feedHandoffPositionSurvivesNavigationRestore() {
        val saved = SavedStateHandle()
        val destination = Dest.Play(bvid = "BV1", cid = 42, startAtMs = 18_350L)
        ShellViewModel(saved).push(destination)

        assertEquals(destination, ShellViewModel(saved).current)
    }

    @Test
    fun olderSavedPlayerRoutesHaveNoHandoffPosition() {
        val saved = SavedStateHandle(mapOf("stack" to "home\nplay3\tBV1\t42"))

        assertEquals(Dest.Play(bvid = "BV1", cid = 42), ShellViewModel(saved).current)
    }

    @Test
    fun startOfSharedPartSurvivesNavigationRestore() {
        val saved = SavedStateHandle()
        val destination = Dest.Play(bvid = "BV1", startAtMs = 0, startPage = 3)
        ShellViewModel(saved).push(destination)

        assertEquals(destination, ShellViewModel(saved).current)
    }

    @Test
    fun tabStaysOnBase() {
        val (base, overlay) = playerLayers(listOf(Dest.Home))
        assertEquals(Dest.Home, base)
        assertNull(overlay)
    }

    @Test
    fun playSitsOnHome() {
        val play = Dest.Play(bvid = "BV1")
        val (base, overlay) = playerLayers(listOf(Dest.Home, play))
        assertEquals(Dest.Home, base)
        assertEquals(play, overlay)
    }

    @Test
    fun playKeepsSpaceUnderneath() {
        val play = Dest.Play(bvid = "BV2")
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Space(7), play))
        assertEquals(Dest.Space(7), base)
        assertEquals(play, overlay)
    }

    @Test
    fun stackedPlaysKeepNonPlayerBase() {
        val first = Dest.Play(bvid = "BV1")
        val second = Dest.Play(bvid = "BV2")
        val (base, overlay) = playerLayers(listOf(Dest.Home, first, second))
        assertEquals(Dest.Home, base)
        assertEquals(second, overlay)
    }

    @Test
    fun liveAndLocalAreOverlays() {
        val live = Dest.Live(99, "直播")
        val local = Dest.Local("BV9")
        assertEquals(Dest.Home to live, playerLayers(listOf(Dest.Home, live)))
        assertEquals(Dest.Cache to local, playerLayers(listOf(Dest.Cache, local)))
    }

    @Test
    fun tabKeepsChromeUnderPlayerOverlay() {
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Home, base)
        assertEquals(Dest.Play(bvid = "BV1"), overlay)
        assertTrue(base.showsShellChrome())
    }

    @Test
    fun spaceHidesChromeUnderPlayerOverlay() {
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Space(1), Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Space(1), base)
        assertNotNull(overlay)
        assertFalse(base.showsShellChrome())
    }

    @Test
    fun railFollowsBaseNotPlayer() {
        val (base, overlay) = playerLayers(listOf(Dest.Dynamic, Dest.Play(bvid = "BV1")))
        assertEquals(Dest.Dynamic, base)
        assertNotNull(overlay)
        assertEquals(2, railSelection(base))
        assertEquals(0, railSelection(Dest.Regions))
        assertEquals(1, railSelection(Dest.Home))
        assertEquals(3, railSelection(Dest.Mine))
        assertEquals(4, railSelection(Dest.Settings))
    }

    @Test
    fun overlayLayerGoneWhenIdle() {
        assertFalse(composeOverlayLayer(overlay = null, held = null))
    }

    @Test
    fun overlayLayerPresentForPlayerDest() {
        val play = Dest.Play(bvid = "BV1")
        assertTrue(composeOverlayLayer(overlay = play, held = null))
    }

    @Test
    fun overlayLayerPresentDuringExitHold() {
        val play = Dest.Play(bvid = "BV1")
        assertTrue(composeOverlayLayer(overlay = null, held = play))
    }

    @Test
    fun bangumiSeasonIsNotPlayerOverlay() {
        val season = Dest.Bangumi(8, "剧")
        assertFalse(season.isPlayerLayer())
        val (base, overlay) = playerLayers(listOf(Dest.Home, Dest.Search("咒术"), season))
        assertEquals(season, base)
        assertNull(overlay)
    }

    // 播放层保活：从播放器进别的页面，播放器仍在组合里（引擎不断），
    // 当前浏览页是栈顶，返回时立刻恢复画面。
    @Test
    fun playerStaysAliveWhenNavigatingIntoSpace() {
        val play = Dest.Play(bvid = "BV1")
        val stack = listOf(Dest.Home, play, Dest.Space(7))
        val (base, overlay) = playerLayers(stack)
        assertEquals(Dest.Space(7), base)
        assertEquals(play, overlay)
        assertFalse(playerOnTop(stack))
    }

    @Test
    fun playerStaysAliveUnderSearchAndCommentDetail() {
        val play = Dest.Play(bvid = "BV1")
        val search = listOf(Dest.Home, play, Dest.Search("标签"))
        assertEquals(play, playerLayers(search).second)
        assertFalse(playerOnTop(search))
        val detail = listOf(Dest.Home, play, Dest.CommentDetail(1, 2))
        assertEquals(play, playerLayers(detail).second)
        assertEquals(Dest.CommentDetail(1, 2), playerLayers(detail).first)
    }

    @Test
    fun playerOnTopWhenItIsStackTop() {
        assertTrue(playerOnTop(listOf(Dest.Home, Dest.Play(bvid = "BV1"))))
        assertTrue(playerOnTop(listOf(Dest.Home, Dest.Space(7), Dest.Live(9, "直播"))))
        assertFalse(playerOnTop(listOf(Dest.Home)))
        assertFalse(playerOnTop(emptyList()))
    }

    @Test
    fun popFromSpaceReturnsPlayerOnTop() {
        val play = Dest.Play(bvid = "BV1")
        val back = listOf(Dest.Home, play)
        assertTrue(playerOnTop(back))
        assertEquals(Dest.Home, playerLayers(back).first)
        assertEquals(play, playerLayers(back).second)
    }
}
