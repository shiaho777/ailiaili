package app.ailiaili.core.network

import kotlin.test.Test
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
    fun reasonTypeAlsoMeansFollowed() {
        assertTrue(parseRecommend(item(""","rcmd_reason":{"reason_type":1}"""))!!.authorFollowed)
    }
}
