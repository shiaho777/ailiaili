package app.ailiaili.core.data

import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS
import app.ailiaili.core.model.NETWORK_CACHING_MAX_MS
import app.ailiaili.core.network.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsCodecTest {
    @Test
    fun roundTripsVolumeAndSearchKind() {
        val src = decodeAppSettings(JSONObject()).copy(playerVolume = 0f, lastSearchKind = ContentKind.Bangumi)
        val json = encodeAppSettings(src)
        val out = decodeAppSettings(json)
        assertEquals(0f, out.playerVolume)
        assertEquals(ContentKind.Bangumi, out.lastSearchKind)
    }

    @Test
    fun missingFieldsDefault() {
        val out = decodeAppSettings(JSONObject())
        assertEquals(0.8f, out.playerVolume)
        assertEquals(ContentKind.Video, out.lastSearchKind)
    }

    @Test
    fun clampsVolume() {
        val json = encodeAppSettings(decodeAppSettings(JSONObject()).copy(playerVolume = 3f))
        assertEquals(1f, json.optDouble("playerVolume").toFloat())
    }

    @Test
    fun roundTripsBufferSettings() {
        val src = decodeAppSettings(JSONObject()).copy(bufferAuto = false, networkCachingMs = 2_000)
        val out = decodeAppSettings(encodeAppSettings(src))
        assertEquals(false, out.bufferAuto)
        assertEquals(2_000, out.networkCachingMs)
    }

    @Test
    fun missingBufferFieldsDefaultToAuto() {
        val out = decodeAppSettings(JSONObject())
        assertTrue(out.bufferAuto)
        assertEquals(NETWORK_CACHING_DEFAULT_MS, out.networkCachingMs)
    }

    @Test
    fun roundTripsFilterRules() {
        val src = decodeAppSettings(JSONObject()).copy(
            filterTitleKeyword = "标题词",
            filterUpKeyword = "UP词",
            blockedUps = listOf(BlockedUp(123, "甲"), BlockedUp(0, "乙")),
            blockedTitles = listOf("标题A", "标题B"),
        )
        val out = decodeAppSettings(encodeAppSettings(src))
        assertEquals("标题词", out.filterTitleKeyword)
        assertEquals("UP词", out.filterUpKeyword)
        assertEquals(src.blockedUps, out.blockedUps)
        assertEquals(src.blockedTitles, out.blockedTitles)
    }

    @Test
    fun sanitizesBlankFilterRules() {
        val json = JSONObject(
            """
            {"blockedUps":[{"mid":0,"name":"  "},{"mid":5,"name":""},{"mid":5,"name":"重复"}],
             "blockedTitles":["  ","标题","标题"]}
            """.trimIndent(),
        )
        val out = decodeAppSettings(json)
        assertEquals(listOf(BlockedUp(5, "")), out.blockedUps)
        assertEquals(listOf("标题"), out.blockedTitles)
    }

    @Test
    fun clampsNetworkCaching() {
        val json = encodeAppSettings(
            decodeAppSettings(JSONObject()).copy(networkCachingMs = 9_999),
        )
        assertEquals(NETWORK_CACHING_MAX_MS, json.optInt("networkCachingMs"))
    }
}
