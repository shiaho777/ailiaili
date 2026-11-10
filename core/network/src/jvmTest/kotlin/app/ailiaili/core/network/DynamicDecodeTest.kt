package app.ailiaili.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicDecodeTest {
    @Test
    fun readsPubdateFromModuleAuthor() {
        val item = JSONObject(
            """
            {"type":0,"modules":{
              "module_author":{"name":"UP","mid":42,"pub_ts":1700000000},
              "module_dynamic":{"major":{"type":"MAJOR_TYPE_ARCHIVE","archive":{
                "bvid":"BV1xx411c7mD","aid":1,"cid":2,"title":"标题","cover":"//i0.hdslb.com/c.jpg",
                "duration_text":"1:00","duration":60,"stat":{"view":10}
              }}}
            }}
            """.trimIndent(),
        )
        val parsed = parseDynamic(item)!!
        assertEquals(1_700_000_000L, parsed.pubdate)
        assertEquals("UP", parsed.author)
    }

    @Test
    fun readsPubdateFromFlatDynamic() {
        val item = JSONObject(
            """{"type":8,"title":"标题","cover":"//i0.hdslb.com/c.jpg","bvid":"BV1xx411c7mD","pubdate":1700000123}""",
        )
        assertEquals(1_700_000_123L, parseDynamic(item)!!.pubdate)
    }
}
