package app.ailiaili.core.data

import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.CacheEntry
import app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS
import app.ailiaili.core.model.NETWORK_CACHING_MAX_MS
import app.ailiaili.core.model.NETWORK_CACHING_MIN_MS
import app.ailiaili.core.model.CdnMode
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.RecommendSource
import app.ailiaili.core.model.Session
import app.ailiaili.core.model.ShellLayout
import app.ailiaili.core.model.SponsorCategories
import app.ailiaili.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import app.ailiaili.core.network.HttpEngine
import app.ailiaili.core.network.JSONArray
import app.ailiaili.core.network.JSONObject
import app.ailiaili.core.network.get

class SessionStore(private val file: AppFile) {
    private val _session = MutableStateFlow(Session())
    val session: StateFlow<Session> = _session
    var current: Session
        get() = _session.value
        private set(value) {
            _session.value = value
        }

    fun load() {
        current = runCatching {
            if (!file.exists()) return@runCatching Session()
            val o = JSONObject(file.readText())
            Session(
                sessData = o.optString("sessData").ifBlank { null },
                biliJct = o.optString("biliJct").ifBlank { null },
                dedeUserId = o.optString("dedeUserId").ifBlank { null },
                dedeUserIdCkMd5 = o.optString("dedeUserIdCkMd5").ifBlank { null },
                buvid3 = o.optString("buvid3").ifBlank { null },
                buvid4 = o.optString("buvid4").ifBlank { null },
                bNut = o.optString("bNut").ifBlank { null },
            )
        }.getOrDefault(Session())
    }

    fun save(session: Session) {
        current = session
        runCatching {
            file.parentFile?.mkdirs()
            val o = JSONObject()
                .put("sessData", session.sessData)
                .put("biliJct", session.biliJct)
                .put("dedeUserId", session.dedeUserId)
                .put("dedeUserIdCkMd5", session.dedeUserIdCkMd5)
                .put("buvid3", session.buvid3)
                .put("buvid4", session.buvid4)
                .put("bNut", session.bNut)
            file.writeText(o.toString(2))
        }
    }
}

class SettingsStore(private val file: AppFile) {
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    val current: AppSettings get() = _settings.value

    fun load() {
        _settings.value = runCatching {
            if (!file.exists()) return@runCatching AppSettings()
            decodeAppSettings(JSONObject(file.readText()))
        }.getOrDefault(AppSettings())
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(current).let { it.copy(playerVolume = it.playerVolume.coerceIn(0f, 1f)) }
        _settings.value = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(encodeAppSettings(next).toString(2))
        }
    }
}

internal fun decodeAppSettings(o: JSONObject): AppSettings = AppSettings(
    theme = runCatching { ThemeMode.valueOf(o.optString("theme", "System")) }.getOrDefault(ThemeMode.System),
    shellLayout = runCatching { ShellLayout.valueOf(o.optString("shellLayout", "Auto")) }.getOrDefault(ShellLayout.Auto),
    recommendSource = runCatching { RecommendSource.valueOf(o.optString("recommendSource", "Web")) }
        .getOrDefault(RecommendSource.Web),
    danmakuEnabled = o.optBoolean("danmakuEnabled", true),
    danmakuAreaPercent = o.optInt("danmakuAreaPercent", 25).coerceIn(25, 100),
    danmakuSmartFilter = o.optBoolean("danmakuSmartFilter", true),
    danmakuColorful = o.optBoolean("danmakuColorful", true),
    danmakuPillBackground = o.optBoolean("danmakuPillBackground", true),
    danmakuBlockKeywords = o.optString("danmakuBlockKeywords"),
    danmakuFilterScroll = o.optBoolean("danmakuFilterScroll", true),
    danmakuFilterFixed = o.optBoolean("danmakuFilterFixed", true),
    danmakuFilterColorful = o.optBoolean("danmakuFilterColorful", true),
    danmakuFilterAdvanced = o.optBoolean("danmakuFilterAdvanced", true),
    defaultQn = o.optInt("defaultQn", 80),
    noLogin1080 = o.optBoolean("noLogin1080", true),
    sponsorBlock = o.optBoolean("sponsorBlock", false),
    sponsorCategories = SponsorCategories.sanitize(o.optString("sponsorCategories")),
    cdnMode = if (!o.optBoolean("cdnModeMigrated", false)) {
        // v1.6.2 起默认跟随服务端；老配置一次性迁移，之后用户手动切回自动测速仍会保留。
        CdnMode.Server
    } else {
        runCatching { CdnMode.valueOf(o.optString("cdnMode", "Server")) }.getOrDefault(CdnMode.Server)
    },
    cdnManualId = o.optString("cdnManualId", "ali").ifBlank { "ali" },
    blockPcdn = o.optBoolean("blockPcdn", true),
    bufferAuto = o.optBoolean("bufferAuto", true),
    networkCachingMs = o.optInt("networkCachingMs", NETWORK_CACHING_DEFAULT_MS)
        .coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS),
    accentHex = o.optString("accentHex"),
    accentFollowSystem = o.optBoolean("accentFollowSystem", false),
    autoCheckUpdate = o.optBoolean("autoCheckUpdate", true),
    skippedVersion = o.optString("skippedVersion"),
    acceptedDisclaimerVersion = run {
        val stored = o.optInt("acceptedDisclaimerVersion", 0)
        when {
            stored > 0 -> stored
            o.optBoolean("disclaimerAccepted", false) -> 1
            else -> 0
        }
    },
    closeToTray = o.optBoolean("closeToTray", true),
    playerVolume = o.optDouble("playerVolume", 0.8).toFloat().coerceIn(0f, 1f),
    downloadDir = o.optString("downloadDir"),
    wheelSpeedPercent = o.optInt("wheelSpeedPercent", 250).coerceIn(100, 500),
    lastSearchKind = runCatching { ContentKind.valueOf(o.optString("lastSearchKind", "Video")) }
        .getOrDefault(ContentKind.Video),
    filterTitleKeyword = o.optString("filterTitleKeyword"),
    filterUpKeyword = o.optString("filterUpKeyword"),
    blockedUps = run {
        val arr = o.optJSONArray("blockedUps") ?: return@run emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val mid = item.optLong("mid").coerceAtLeast(0)
                val name = item.optString("name").trim().take(80)
                if (mid > 0 || name.isNotBlank()) add(BlockedUp(mid, name))
                if (size >= 200) break
            }
        }.distinctBy { if (it.mid > 0) "m${it.mid}" else "n${it.name}" }
    },
    blockedTitles = run {
        val arr = o.optJSONArray("blockedTitles") ?: return@run emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val title = arr.optString(i).trim()
                if (title.isNotBlank()) add(title.take(200))
                if (size >= 200) break
            }
        }.distinct()
    },
)

internal fun encodeAppSettings(next: AppSettings): JSONObject = JSONObject()
    .put("theme", next.theme.name)
    .put("shellLayout", next.shellLayout.name)
    .put("recommendSource", next.recommendSource.name)
    .put("danmakuEnabled", next.danmakuEnabled)
    .put("danmakuAreaPercent", next.danmakuAreaPercent)
    .put("danmakuSmartFilter", next.danmakuSmartFilter)
    .put("danmakuColorful", next.danmakuColorful)
    .put("danmakuPillBackground", next.danmakuPillBackground)
    .put("danmakuBlockKeywords", next.danmakuBlockKeywords)
    .put("danmakuFilterScroll", next.danmakuFilterScroll)
    .put("danmakuFilterFixed", next.danmakuFilterFixed)
    .put("danmakuFilterColorful", next.danmakuFilterColorful)
    .put("danmakuFilterAdvanced", next.danmakuFilterAdvanced)
    .put("defaultQn", next.defaultQn)
    .put("noLogin1080", next.noLogin1080)
    .put("sponsorBlock", next.sponsorBlock)
    .put("sponsorCategories", SponsorCategories.sanitize(next.sponsorCategories))
    .put("cdnMode", next.cdnMode.name)
    .put("cdnModeMigrated", true)
    .put("cdnManualId", next.cdnManualId)
    .put("blockPcdn", next.blockPcdn)
    .put("bufferAuto", next.bufferAuto)
    .put("networkCachingMs", next.networkCachingMs.coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS))
    .put("accentHex", next.accentHex)
    .put("accentFollowSystem", next.accentFollowSystem)
    .put("autoCheckUpdate", next.autoCheckUpdate)
    .put("skippedVersion", next.skippedVersion)
    .put("acceptedDisclaimerVersion", next.acceptedDisclaimerVersion)
    .put("closeToTray", next.closeToTray)
    .put("playerVolume", next.playerVolume.coerceIn(0f, 1f).toDouble())
    .put("downloadDir", next.downloadDir)
    .put("wheelSpeedPercent", next.wheelSpeedPercent.coerceIn(100, 500))
    .put("lastSearchKind", next.lastSearchKind.name)
    .put("filterTitleKeyword", next.filterTitleKeyword)
    .put("filterUpKeyword", next.filterUpKeyword)
    .put("blockedUps", JSONArray().apply {
        next.blockedUps.forEach { rule ->
            put(
                JSONObject()
                    .put("mid", rule.mid)
                    .put("name", rule.name),
            )
        }
    })
    .put("blockedTitles", JSONArray().apply {
        next.blockedTitles.forEach { put(it) }
    })

class SearchHistory(private val file: AppFile) {
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items

    fun load() {
        _items.value = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            buildList { for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) } }
        }.getOrDefault(emptyList())
    }

    fun add(keyword: String) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        val next = (listOf(word) + _items.value.filter { it != word }).take(20)
        _items.value = next
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            _items.value.forEach { arr.put(it) }
            file.writeText(arr.toString())
        }
    }
}

class OfflineCache(private val root: AppFile) {
    private var extraRoot: AppFile? = null

    fun setExtraRoot(path: String?) {
        extraRoot = path?.trim()?.takeIf { it.isNotEmpty() }?.let { AppFile(it) }
    }

    private fun roots(): List<AppFile> =
        listOfNotNull(root, extraRoot).distinctBy { it.absolutePath }

    fun list(): List<CacheEntry> = roots().flatMap(::scan).sortedByDescending {
        AppFile(it.videoPath).lastModified()
    }

    private fun scan(dir: AppFile): List<CacheEntry> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles().mapNotNull { folder ->
            val meta = AppFile(folder, "meta.json")
            if (!meta.exists()) return@mapNotNull null
            runCatching {
                val o = JSONObject(meta.readText())
                val video = AppFile(folder, o.optString("video", "video.m4s"))
                if (!video.exists()) return@runCatching null
                val audio = AppFile(folder, o.optString("audio", "audio.m4s")).takeIf { it.exists() }
                CacheEntry(
                    bvid = o.optString("bvid"),
                    title = o.optString("title"),
                    cover = o.optString("cover"),
                    videoPath = video.absolutePath,
                    audioPath = audio?.absolutePath,
                    qn = o.optInt("qn"),
                )
            }.getOrNull()
        }
    }

    fun download(
        bvid: String,
        title: String,
        cover: String,
        info: PlayInfo,
        client: HttpEngine,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): CacheEntry {
        val folder = AppFile(extraRoot ?: root, bvid.ifBlank { "local" })
        folder.mkdirs()
        val video = AppFile(folder, "video.m4s")
        val audio = AppFile(folder, "audio.m4s")
        fetch(client, info.videoUrl, video, onProgress)
        val audioUrl = info.audioUrl
        if (audioUrl != null) fetch(client, audioUrl, audio, onProgress)
        val meta = JSONObject()
            .put("bvid", bvid)
            .put("title", title)
            .put("cover", cover)
            .put("qn", info.actualQn)
            .put("video", "video.m4s")
            .put("audio", "audio.m4s")
        AppFile(folder, "meta.json").writeText(meta.toString())
        return CacheEntry(bvid, title, cover, video.absolutePath, audio.takeIf { it.exists() }?.absolutePath, info.actualQn)
    }

    fun delete(bvid: String) {
        roots().forEach { AppFile(it, bvid).deleteRecursively() }
    }

    private fun fetch(
        client: HttpEngine,
        url: String,
        dest: AppFile,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        client.downloadTo(
            url,
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to app.ailiaili.core.model.USER_AGENT,
            ),
            dest.absolutePath,
            onProgress,
        )
    }
}

fun appFiles(): AppFile = appStorageDir()
