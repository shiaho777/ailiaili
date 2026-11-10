package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecommendDecodeTest {
    private fun item(followedField: String) = JSONObject(
        """{"goto":"av","bvid":"BV1xx411c7mD","title":"t","pic":"//x","owner":{"mid":1,"name":"UP"}$followedField}""",
    )

    @Test
    fun readsIsFollowedFlag() {
        assertTrue(parseRecommend(item(""","is_followed":1"""))!!.authorFollowed)
        assertFalse(parseRecommend(item(""","is_followed":0"""))!!.authorFollowed)
    }

    @Test
    fun parsesAppRecommendCard() {
        val item = JSONObject(
            """
            {"goto":"av","card_goto":"av","param":"117582279476757","title":"标题","cover":"//x",
             "cover_left_text_1":"26.8万","cover_left_text_2":"217","cover_right_text":"3:07",
             "args":{"aid":117582279476757,"up_id":42,"up_name":"UP"},
             "player_args":{"aid":117582279476757,"cid":99,"duration":187}}
            """.trimIndent(),
        )
        val parsed = parseAppRecommend(item)!!
        assertEquals(117582279476757L, parsed.aid)
        assertEquals(99L, parsed.cid)
        assertEquals("UP", parsed.author)
        assertEquals(42L, parsed.ownerMid)
        assertEquals(187, parsed.durationSec)
        assertEquals(268_000L, parsed.viewCount)
        assertEquals(217L, parsed.danmakuCount)
        assertEquals("3:07", parsed.durationText)
    }

    @Test
    fun parsesCountText() {
        assertEquals(268_000L, parseCountText("26.8万"))
        assertEquals(120_000_000L, parseCountText("1.2亿"))
        assertEquals(217L, parseCountText("217"))
        assertEquals(0L, parseCountText(""))
    }

    @Test
    fun reasonTypeAlsoMeansFollowed() {
        assertTrue(parseRecommend(item(""","rcmd_reason":{"reason_type":1}"""))!!.authorFollowed)
    }
}
