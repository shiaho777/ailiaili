package app.ailiaili.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 回归：壳层从带侧栏的布局切到无壳层布局时，页面内容（movableContentOf）
 * 必须继续响应 AnimatedContent 的 target 更新。
 *
 * 之前 RailFrame 用 BoxWithConstraints 包住页面，页面移出这个 SubcomposeLayout
 * 后就不再重组：侧边栏消失、页面停在旧内容、点作者名打不开空间页。
 */
@OptIn(ExperimentalTestApi::class)
class ShellContentTest {
    @Test
    fun tabFadeKeepsTheCanvasOpaque() = runComposeUiTest {
        var selected by mutableStateOf(false)
        setContent {
            Box(Modifier.size(100.dp).background(Color.Black).testTag("canvas")) {
                AnimatedContent(
                    selected,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { aliScreenTransition(AliNavigationMotion.Tab, reducedMotion = false) },
                ) { incoming ->
                    Box(Modifier.fillMaxSize().background(if (incoming) Color.White else Color(0xFFF8F8F8)))
                }
            }
        }
        mainClock.autoAdvance = false
        runOnUiThread { selected = true }
        mainClock.advanceTimeBy(48)
        val pixels = onNodeWithTag("canvas").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, pixels.height / 2].red >= .97f)
    }

    @Test
    fun bottomDockOverlaysTheViewportAndLeavesTheLastItemReachable() = runComposeUiTest {
        val scroll = ScrollState(0)
        var pageClicks = 0
        var dockClicks = 0
        setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp, 420.dp).testTag("shell")) {
                    BiliShell(
                        chrome = BiliChrome.Bottom,
                        rail = {},
                        bottomBar = {
                            Box(Modifier.testTag("dock")) {
                                BiliBottomBar(listOf(BiliRailItem("首页", Icons.Filled.Home)), 0, { dockClicks++ })
                            }
                        },
                        content = {
                            Column(
                                Modifier.fillMaxSize().testTag("page").clickable { pageClicks++ }
                                    .verticalScroll(scroll).padding(bottom = shellBottomClearance()),
                            ) {
                                repeat(12) { index ->
                                    Box(Modifier.fillMaxWidth().height(60.dp).testTag("row-$index"))
                                }
                            }
                        },
                    )
                }
            }
        }

        val shell = onNodeWithTag("shell").fetchSemanticsNode().boundsInRoot
        val page = onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val dock = onNodeWithTag("dock").fetchSemanticsNode().boundsInRoot
        assertEquals(shell.bottom, page.bottom)
        assertTrue(dock.top < page.bottom)

        onNodeWithTag("page").performTouchInput { click(Offset(2f, height - 8f)) }
        onNodeWithText("首页").performTouchInput { click() }
        runOnIdle {
            assertEquals(1, pageClicks)
            assertEquals(1, dockClicks)
            scroll.dispatchRawDelta(10_000f)
        }
        assertTrue(onNodeWithTag("row-11", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom <= dock.top)
    }

    @Test
    fun pushedPageComposesWhenRailDisappears() = runComposeUiTest {
        var chrome by mutableStateOf(BiliChrome.Rail)
        var target by mutableStateOf("home")
        setContent {
            MaterialTheme {
                BiliShell(
                    chrome = chrome,
                    rail = { Text("rail") },
                    bottomBar = { Text("bottom") },
                    content = {
                        AnimatedContent(targetState = target) { page ->
                            Text("page:$page")
                        }
                    },
                )
            }
        }
        onNodeWithText("page:home").assertExists()
        onNodeWithText("rail").assertExists()
        runOnUiThread {
            target = "space"
            chrome = BiliChrome.None
        }
        waitForIdle()
        onNodeWithText("page:space").assertExists()
        onNodeWithText("rail").assertDoesNotExist()
    }

    @Test
    fun pushedPageComposesAfterRailAlreadyGone() = runComposeUiTest {
        var chrome by mutableStateOf(BiliChrome.Rail)
        var target by mutableStateOf("home")
        setContent {
            MaterialTheme {
                BiliShell(
                    chrome = chrome,
                    rail = { Text("rail") },
                    bottomBar = { Text("bottom") },
                    content = {
                        AnimatedContent(targetState = target) { page ->
                            Text("page:$page")
                        }
                    },
                )
            }
        }
        onNodeWithText("page:home").assertExists()
        runOnUiThread { chrome = BiliChrome.None }
        waitForIdle()
        runOnUiThread { target = "space" }
        waitForIdle()
        onNodeWithText("page:space").assertExists()
    }

    @Test
    fun pushedPageComposesFromBottomBar() = runComposeUiTest {
        var chrome by mutableStateOf(BiliChrome.Bottom)
        var target by mutableStateOf("home")
        setContent {
            MaterialTheme {
                BiliShell(
                    chrome = chrome,
                    rail = { Text("rail") },
                    bottomBar = { Text("bottom") },
                    content = {
                        AnimatedContent(targetState = target) { page ->
                            Text("page:$page")
                        }
                    },
                )
            }
        }
        onNodeWithText("page:home").assertExists()
        onNodeWithText("bottom").assertExists()
        runOnUiThread {
            target = "space"
            chrome = BiliChrome.None
        }
        waitForIdle()
        onNodeWithText("page:space").assertExists()
        onNodeWithText("bottom").assertDoesNotExist()
    }

    @Test
    fun overlayKeepsUpdatingAfterRailDisappears() = runComposeUiTest {
        var chrome by mutableStateOf(BiliChrome.Rail)
        var label by mutableStateOf("a")
        setContent {
            MaterialTheme {
                BiliShell(
                    chrome = chrome,
                    rail = { Text("rail") },
                    bottomBar = { Text("bottom") },
                    overlay = { Text("overlay:$label") },
                    content = { Text("page") },
                )
            }
        }
        onNodeWithText("overlay:a").assertExists()
        runOnUiThread { chrome = BiliChrome.None }
        waitForIdle()
        runOnUiThread { label = "b" }
        waitForIdle()
        onNodeWithText("overlay:b").assertExists()
    }

    @Test
    fun targetChangeKeepsWorkingWhileRailVisible() = runComposeUiTest {
        var target by mutableStateOf("home")
        setContent {
            MaterialTheme {
                BiliShell(
                    chrome = BiliChrome.Rail,
                    rail = { Text("rail") },
                    bottomBar = { Text("bottom") },
                    content = {
                        AnimatedContent(targetState = target) { page ->
                            Text("page:$page")
                        }
                    },
                )
            }
        }
        onNodeWithText("page:home").assertExists()
        runOnUiThread { target = "space" }
        waitForIdle()
        onNodeWithText("page:space").assertExists()
        onNodeWithText("rail").assertExists()
    }
}
