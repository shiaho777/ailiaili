package app.ailiaili.core.designsystem

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.VideoItem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentFilterTest {
    @AfterTest
    fun reset() {
        ContentFilter.load(AppSettings())
    }

    @Test
    fun keywordMatchesTitleAndAuthor() {
        ContentFilter.load(AppSettings(filterTitleKeyword = "广告", filterUpKeyword = "搬运"))
        assertFalse(ContentFilter.allows(VideoItem(title = "这是一条广告", author = "正常UP")))
        assertFalse(ContentFilter.allows(VideoItem(title = "正常标题", author = "搬运工")))
        assertTrue(ContentFilter.allows(VideoItem(title = "正常标题", author = "正常UP")))
    }

    @Test
    fun exactUpRuleMatchesMidEvenWhenNameChanged() {
        ContentFilter.load(AppSettings(blockedUps = listOf(BlockedUp(42, "旧名字"))))
        assertFalse(ContentFilter.allows(VideoItem(title = "t", author = "新名字", ownerMid = 42)))
        assertTrue(ContentFilter.allows(VideoItem(title = "t", author = "别的UP", ownerMid = 43)))
    }

    @Test
    fun exactUpRuleFallsBackToExactName() {
        ContentFilter.load(AppSettings(blockedUps = listOf(BlockedUp(0, "某UP"))))
        assertFalse(ContentFilter.allows(VideoItem(title = "t", author = "某UP", ownerMid = 0)))
        assertTrue(ContentFilter.allows(VideoItem(title = "t", author = "某UP的粉丝", ownerMid = 0)))
    }

    @Test
    fun exactTitleRuleDoesNotMatchPartial() {
        ContentFilter.load(AppSettings(blockedTitles = listOf("完整标题")))
        assertFalse(ContentFilter.allows(VideoItem(title = "完整标题")))
        assertTrue(ContentFilter.allows(VideoItem(title = "完整标题 加长版")))
    }
}
