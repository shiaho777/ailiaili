package app.ailiaili.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

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
