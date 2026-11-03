package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentDecodeTest {
    @Test
    fun decodesRichCommentFields() {
        val json = JSONObject(
            """
            {
              "rpid": 1,
              "member": {
                "uname": "测试用户",
                "avatar": "//i0.hdslb.com/avatar.jpg",
                "mid": 42,
                "level_info": { "current_level": 5 }
              },
              "content": {
                "message": "回复 @雨霁影清 看这个[微笑]",
                "emote": {
                  "[微笑]": {
                    "url": "//i0.hdslb.com/emote.png",
                    "meta": { "size": 2 }
                  }
                },
                "pictures": [
                  {
                    "img_src": "//i0.hdslb.com/pic.jpg",
                    "img_width": 1200,
                    "img_height": 800
                  }
                ]
              },
              "like": 3,
              "rcount": 2,
              "action": 1,
              "reply_control": {
                "location": "IP属地：上海",
                "time_desc": "1小时前"
              },
              "replies": []
            }
            """.trimIndent(),
        )

        val comment = parseComment(json, upperMid = 42)

        assertEquals("测试用户", comment.name)
        assertEquals("https://i0.hdslb.com/avatar.jpg", comment.face)
        assertEquals(listOf("雨霁影清"), comment.mentions)
        assertEquals(1, comment.emotes.size)
        assertEquals("https://i0.hdslb.com/emote.png", comment.emotes["[微笑]"]?.url)
        assertEquals(2, comment.emotes["[微笑]"]?.size)
        assertEquals(1, comment.pictures.size)
        assertEquals("https://i0.hdslb.com/pic.jpg", comment.pictures[0].url)
        assertEquals(1200, comment.pictures[0].width)
        assertEquals(800, comment.pictures[0].height)
        assertEquals("上海", comment.location)
        assertEquals(5, comment.level)
        assertTrue(comment.up)
        assertTrue(comment.liked)
    }

    @Test
    fun dropsEmoteNotUsedInMessage() {
        val json = JSONObject(
            """
            {
              "rpid": 2,
              "content": {
                "message": "纯文本",
                "emote": {
                  "[微笑]": { "url": "//i0.hdslb.com/emote.png" }
                }
              }
            }
            """.trimIndent(),
        )
        val comment = parseComment(json)
        assertTrue(comment.emotes.isEmpty())
    }
}
