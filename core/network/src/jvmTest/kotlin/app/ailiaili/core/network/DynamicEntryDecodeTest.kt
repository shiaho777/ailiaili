package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicEntryDecodeTest {
    private fun opusItem(): JSONObject = JSONObject(
        """
        {
          "id_str": "123456",
          "type": "DYNAMIC_TYPE_DRAW",
          "modules": {
            "module_author": {"mid": 42, "name": "阿婆主", "face": "//i0.hdslb.com/f.jpg", "pub_ts": 1700000000},
            "module_dynamic": {
              "major": {
                "type": "MAJOR_TYPE_OPUS",
                "opus": {
                  "jump_url": "//www.bilibili.com/opus/123456",
                  "title": "图文标题",
                  "summary": {"text": "正文内容 https://example.com"},
                  "pics": [{"url": "//i0.hdslb.com/p1.jpg"}, {"url": "//i0.hdslb.com/p2.jpg"}]
                }
              }
            },
            "module_stat": {"like": {"count": 10, "status": 1}, "comment": {"count": 3}}
          }
        }
        """.trimIndent(),
    )

    @Test
    fun parsesOpusPost() {
        val entry = parseDynamicEntry(opusItem())!!
        assertTrue(!entry.isVideo)
        val post = entry.post!!
        assertEquals("图文标题", post.title)
        assertEquals(123456L, post.oid)
        assertEquals(11, post.commentType)
        assertEquals(2, post.pics.size)
        assertTrue(post.pics.all { it.startsWith("https:") })
        assertEquals(10, post.likeCount)
        assertTrue(post.liked)
        assertEquals("阿婆主", entry.authorName)
    }

    @Test
    fun parsesArticleWithCvCommentType() {
        val item = JSONObject(
            """
            {
              "id_str": "9", "type": "DYNAMIC_TYPE_ARTICLE",
              "modules": {
                "module_author": {"mid": 1, "name": "a", "pub_ts": 5},
                "module_dynamic": {"major": {"type": "MAJOR_TYPE_ARTICLE",
                  "article": {"id": 777, "title": "专栏", "desc": "摘要", "covers": ["//i0.hdslb.com/c.jpg"]}}}
              }
            }
            """.trimIndent(),
        )
        val post = parseDynamicEntry(item)!!.post!!
        assertEquals(777L, post.oid)
        assertEquals(12, post.commentType)
        assertEquals("摘要", post.text)
    }

    @Test
    fun parsesLegacyDraw() {
        val item = JSONObject(
            """
            {
              "id_str": "8", "type": "DYNAMIC_TYPE_DRAW",
              "item": {"id": 555, "description": "老图文", "pictures": [{"img_src": "//i0.hdslb.com/x.jpg"}]},
              "modules": {"module_author": {"mid": 2, "name": "b", "pub_ts": 7}}
            }
            """.trimIndent(),
        )
        val post = parseDynamicEntry(item)!!.post!!
        assertEquals(555L, post.oid)
        assertEquals(2, post.commentType)
        assertEquals(listOf("https://i0.hdslb.com/x.jpg"), post.pics)
    }

    @Test
    fun forwardKeepsForwarderAsAuthor() {
        val inner = opusItem()
        val outer = JSONObject(
            """
            {
              "id_str": "999", "type": "DYNAMIC_TYPE_FORWARD",
              "modules": {
                "module_author": {"mid": 7, "name": "转发者", "pub_ts": 1700000999},
                "module_dynamic": {"desc": {"text": "转发附言"}}
              }
            }
            """.trimIndent(),
        ).put("orig", inner)
        val entry = parseDynamicEntry(outer)!!
        assertEquals("999", entry.id)
        assertEquals("转发者", entry.authorName)
        assertEquals("转发附言", entry.desc)
        assertNotNull(entry.post)
    }

    @Test
    fun parsesVideoEntry() {
        val item = JSONObject(
            """
            {
              "id_str": "5", "type": "DYNAMIC_TYPE_AV",
              "modules": {
                "module_author": {"mid": 3, "name": "c", "pub_ts": 9},
                "module_dynamic": {"major": {"type": "MAJOR_TYPE_ARCHIVE",
                  "archive": {"bvid": "BV1xx411c7mD", "aid": 11, "title": "视频", "cover": "//i0.hdslb.com/v.jpg",
                              "duration_text": "10:00", "stat": {"play": "100", "danmaku": "5"}}}}
              }
            }
            """.trimIndent(),
        )
        val entry = parseDynamicEntry(item)!!
        assertTrue(entry.isVideo)
        assertEquals("BV1xx411c7mD", entry.video!!.bvid)
        assertEquals(100, entry.video!!.viewCount)
    }

    @Test
    fun unsupportedTypeReturnsNull() {
        val item = JSONObject(
            """
            {"id_str": "1", "type": "DYNAMIC_TYPE_LIVE_RCMD",
             "modules": {"module_author": {"mid": 1, "name": "x", "pub_ts": 1}}}
            """.trimIndent(),
        )
        assertNull(parseDynamicEntry(item))
    }
}
