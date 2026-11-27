package app.ailiaili.core.data

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.CdnMode
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.RecommendSource
import app.ailiaili.core.model.ShellLayout
import app.ailiaili.core.model.StartupPage
import app.ailiaili.core.model.PlayerScale
import app.ailiaili.core.model.ThemeMode
import app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS
import app.ailiaili.core.model.NETWORK_CACHING_MAX_MS
import app.ailiaili.core.network.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsCodecTest {
    @Test
    fun roundTripsVolumeAndSearchKind() {
        val src = decodeAppSettings(JSONObject()).copy(
            playerVolume = 0f, lastAudiblePlayerVolume = .32f, lastSearchKind = ContentKind.Bangumi,
        )
        val json = encodeAppSettings(src)
        val out = decodeAppSettings(json)
        assertEquals(0f, out.playerVolume)
        assertEquals(.32f, out.lastAudiblePlayerVolume)
        assertEquals(ContentKind.Bangumi, out.lastSearchKind)
    }

    @Test
    fun missingFieldsDefault() {
        val out = decodeAppSettings(JSONObject())
        assertEquals(0.8f, out.playerVolume)
        assertEquals(ContentKind.Video, out.lastSearchKind)
    }

    @Test
    fun readsTheLastAudibleVolumeFromOlderSettings() {
        assertEquals(.35f, decodeAppSettings(JSONObject().put("playerVolume", .35)).lastAudiblePlayerVolume)
        assertEquals(.8f, decodeAppSettings(JSONObject().put("playerVolume", 0)).lastAudiblePlayerVolume)
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
    fun roundTripsWheelSpeed() {
        val src = decodeAppSettings(JSONObject()).copy(wheelSpeedPercent = 400)
        val out = decodeAppSettings(encodeAppSettings(src))
        assertEquals(400, out.wheelSpeedPercent)
        assertEquals(250, decodeAppSettings(JSONObject()).wheelSpeedPercent)
    }

    @Test
    fun roundTripsRecommendSource() {
        val src = decodeAppSettings(JSONObject()).copy(recommendSource = RecommendSource.App)
        val out = decodeAppSettings(encodeAppSettings(src))
        assertEquals(RecommendSource.App, out.recommendSource)
    }

    @Test
    fun migratesOldCdnModeOnceThenKeepsManualChoice() {
        val old = JSONObject("""{"cdnMode":"Auto"}""")
        assertEquals(CdnMode.Server, decodeAppSettings(old).cdnMode)
        val saved = encodeAppSettings(decodeAppSettings(old).copy(cdnMode = CdnMode.Auto))
        assertEquals(CdnMode.Auto, decodeAppSettings(saved).cdnMode)
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
    fun roundTripsSubtitleRelatedAndFont() {
        val src = decodeAppSettings(JSONObject()).copy(
            showRelated = false,
            subtitleEnabled = true,
            subtitleLan = "ai-zh",
            subtitleDelaySec = -0.4,
            fontPath = "fonts/my.ttf",
        )
        val out = decodeAppSettings(encodeAppSettings(src))
        assertEquals(false, out.showRelated)
        assertEquals(true, out.subtitleEnabled)
        assertEquals("ai-zh", out.subtitleLan)
        assertEquals(-0.4, out.subtitleDelaySec)
        assertEquals("fonts/my.ttf", out.fontPath)
    }

    @Test
    fun missingSubtitleRelatedAndFontFieldsDefault() {
        val out = decodeAppSettings(JSONObject())
        assertEquals(true, out.showRelated)
        assertEquals(false, out.subtitleEnabled)
        assertEquals("", out.subtitleLan)
        assertEquals(0.0, out.subtitleDelaySec)
        assertEquals("", out.fontPath)
    }

    /** 全字段非默认值往返：新增设置字段忘了同步编解码时，这里会直接红。 */
    @Test
    fun everyFieldSurvivesRoundTrip() {
        val src = AppSettings(
            theme = ThemeMode.Dark,
            shellLayout = ShellLayout.Rail,
            startupPage = StartupPage.Mine,
            favoriteGrid = false,
            recommendSource = RecommendSource.App,
            downloadQuality = 64,
            backgroundDownloads = false,
            unmeteredDownloads = true,
            downloadDanmaku = true,
            downloadSubtitles = false,
            playerScale = PlayerScale.Crop,
            seekStepSeconds = 15,
            danmakuDelayMs = -1500,
            danmakuEnabled = false,
            danmakuAreaPercent = 50,
            danmakuSmartFilter = false,
            danmakuColorful = false,
            danmakuPillBackground = false,
            danmakuBlockKeywords = "词A 词B",
            danmakuFilterScroll = false,
            danmakuFilterFixed = false,
            danmakuFilterColorful = false,
            danmakuFilterAdvanced = false,
            danmakuOpacityPercent = 55,
            danmakuMaxOnScreen = 30,
            defaultQn = 116,
            noLogin1080 = false,
            showRelated = false,
            subtitleEnabled = true,
            subtitleLan = "zh-CN",
            subtitleDelaySec = 0.6,
            subtitleFontSp = 24,
            subtitleBottomPercent = 12,
            customUserAgent = "ailiaili/1.3.0 (test)",
            httpProxy = "127.0.0.1:7890",
            playbackSpeed = 1.75f,
            autoPlayNext = false,
            autoPlayRelated = true,
            desktopHardwareDecoding = false,
            desktopHighQualityAudio = true,
            sponsorBlock = true,
            sponsorCategories = "sponsor,selfpromo",
            cdnMode = CdnMode.Auto,
            cdnManualId = "cos",
            blockPcdn = false,
            bufferAuto = false,
            networkCachingMs = 2_500,
            accentHex = "#123456",
            fontPath = "fonts/x.ttf",
            accentFollowSystem = true,
            autoCheckUpdate = false,
            reduceMotion = true,
            skippedVersion = "9.9.9",
            acceptedDisclaimerVersion = 2,
            closeToTray = false,
            playerVolume = 0.3f,
            lastSearchKind = ContentKind.Live,
            downloadDir = "/tmp/dl",
            wheelSpeedPercent = 400,
            filterTitleKeyword = "标题词",
            filterUpKeyword = "UP词",
            blockedUps = listOf(BlockedUp(7, "甲")),
            blockedTitles = listOf("标题A"),
        )
        assertEquals(src, decodeAppSettings(encodeAppSettings(src)))
    }

    @Test
    fun clampsNetworkCaching() {
        val json = encodeAppSettings(
            decodeAppSettings(JSONObject()).copy(networkCachingMs = 9_999),
        )
        assertEquals(NETWORK_CACHING_MAX_MS, json.optInt("networkCachingMs"))
    }
}
