package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class FollowingLiveDecodeTest {
    @Test
    fun keepsOnlyLiveRoomsAndSortsByPopularity() {
        val data = JSONObject(
            """
            {
              "1001": {
                "room_id": 1001,
                "title": "正在直播 A",
                "uname": "主播A",
                "uid": 11,
                "live_status": 1,
                "user_cover": "//i0.hdslb.com/a.jpg",
                "online": 30
              },
              "1002": {
                "room_id": 1002,
                "title": "正在直播 B",
                "uname": "主播B",
                "uid": 12,
                "live_status": 1,
                "cover": "//i0.hdslb.com/b.jpg",
                "online": 90
              },
              "1003": {
                "room_id": 1003,
                "title": "没开播",
                "uname": "主播C",
                "uid": 13,
                "live_status": 0,
                "online": 999
              },
              "1004": {
                "room_id": 1004,
                "title": "轮播",
                "uname": "主播D",
                "uid": 14,
                "live_status": 2,
                "online": 999
              }
            }
            """.trimIndent(),
        )

        val items = parseFollowingLive(data)
        assertEquals(listOf(1002L, 1001L), items.map { it.roomId })
        assertEquals("主播B", items[0].author)
        assertEquals("https://i0.hdslb.com/b.jpg", items[0].cover)
        assertEquals("直播中", items[0].durationText)
        assertEquals(90L, items[0].viewCount)
    }
}
