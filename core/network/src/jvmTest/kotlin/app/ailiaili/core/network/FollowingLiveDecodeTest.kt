package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowingLiveDecodeTest {
    @Test
    fun keepsOnlyLiveRoomsAndUsesFace() {
        val data = JSONObject(
            """
            {"rooms":[
              {"room_id":101,"uid":11,"uname":"主播A","title":"<em>标题</em>","live_status":1,
               "face":"//i0.hdslb.com/a.jpg","online":0},
              {"room_id":102,"uid":12,"uname":"主播B","title":"未开播","live_status":0,
               "face":"//i0.hdslb.com/b.jpg"},
              {"room_id":101,"uid":11,"uname":"主播A","title":"重复房间","live_status":1},
              {"roomid":103,"uid":13,"nickname":"主播C","title":"轮播","live_status":1,
               "cover_from_user":"//i0.hdslb.com/c.jpg"}
            ]}
            """.trimIndent(),
        )
        val items = parseFollowingLive(data)
        assertEquals(2, items.size)
        assertEquals(101L, items[0].roomId)
        assertEquals("主播A", items[0].author)
        assertEquals("标题", items[0].title)
        assertTrue(items[0].cover.startsWith("https://"))
        assertEquals(103L, items[1].roomId)
        assertEquals("主播C", items[1].author)
    }

    @Test
    fun fallsBackToListAndSortsByOnline() {
        val data = JSONObject(
            """
            {"list":[
              {"room_id":201,"uid":21,"uname":"冷门","title":"A","live_status":1,"online":3},
              {"room_id":202,"uid":22,"uname":"热门","title":"B","live_status":1,"online":900}
            ]}
            """.trimIndent(),
        )
        val items = parseFollowingLive(data)
        assertEquals(listOf(202L, 201L), items.map { it.roomId })
    }

    @Test
    fun emptyDataMeansNobodyLive() {
        assertTrue(parseFollowingLive(JSONObject("{}")).isEmpty())
    }
}
