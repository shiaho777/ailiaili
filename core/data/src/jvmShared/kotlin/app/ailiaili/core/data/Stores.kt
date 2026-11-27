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
import app.ailiaili.core.model.PlayerScale
import app.ailiaili.core.model.RecommendSource
import app.ailiaili.core.model.Session
import app.ailiaili.core.model.ShellLayout
import app.ailiaili.core.model.StartupPage
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
            file.writeTextAtomic(o.toString(2))
        }
    }
}

/**
 * 多账号槽位存储（accounts.json，与 session.json 同目录同规格）。
 * 只存身份 cookie 与展示信息；最多 8 个，按 uid 去重，新登录的排最前。
 * 设备指纹不随账号走：切换时保留当前 Session 的 buvid/bNut。
 */
class AccountStore(private val file: AppFile) {
    private val _accounts = MutableStateFlow<List<app.ailiaili.core.model.SavedAccount>>(emptyList())
    val accounts: StateFlow<List<app.ailiaili.core.model.SavedAccount>> = _accounts

    fun load() {
        _accounts.value = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val uid = o.optString("uid")
                    val sess = o.optString("sessData")
                    val jct = o.optString("biliJct")
                    if (uid.isBlank() || sess.isBlank() || jct.isBlank()) continue
                    add(
                        app.ailiaili.core.model.SavedAccount(
                            uid = uid,
                            name = o.optString("name"),
                            face = o.optString("face"),
                            sessData = sess,
                            biliJct = jct,
                            dedeUserIdCkMd5 = o.optString("ckMd5").ifBlank { null },
                        ),
                    )
                    if (size >= MAX_ACCOUNTS) break
                }
            }.distinctBy { it.uid }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            _accounts.value.forEach { a ->
                arr.put(
                    JSONObject()
                        .put("uid", a.uid)
                        .put("name", a.name)
                        .put("face", a.face)
                        .put("sessData", a.sessData)
                        .put("biliJct", a.biliJct)
                        .put("ckMd5", a.dedeUserIdCkMd5),
                )
            }
            file.writeTextAtomic(arr.toString())
        }
    }

    /** 登录成功/切换后归档：同 uid 覆盖并置顶。 */
    fun upsert(account: app.ailiaili.core.model.SavedAccount) {
        if (account.uid.isBlank() || account.sessData.isBlank()) return
        _accounts.value = (listOf(account) + _accounts.value.filterNot { it.uid == account.uid })
            .take(MAX_ACCOUNTS)
        persist()
    }

    /** 补齐展示信息（昵称/头像），不动凭据。 */
    fun updateProfile(uid: String, name: String, face: String) {
        val list = _accounts.value
        val idx = list.indexOfFirst { it.uid == uid }
        if (idx < 0) return
        val old = list[idx]
        if (old.name == name && old.face == face) return
        _accounts.value = list.toMutableList().also { it[idx] = old.copy(name = name, face = face) }
        persist()
    }

    fun remove(uid: String) {
        _accounts.value = _accounts.value.filterNot { it.uid == uid }
        persist()
    }

    companion object {
        const val MAX_ACCOUNTS = 8
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
            file.writeTextAtomic(encodeAppSettings(next).toString(2))
        }
    }

    /** 设置备份导出：带格式标记的完整 JSON（不含登录凭据）。 */
    fun exportJson(): String = JSONObject()
        .put("ailiailiSettingsBackup", 1)
        .put("settings", encodeAppSettings(current))
        .toString(2)

    /**
     * 设置备份导入：校验格式标记后整体替换并落盘。
     * 解码对缺失字段取默认值，旧版本备份也能导入。
     */
    fun importJson(text: String): Result<Unit> = runCatching {
        val root = JSONObject(text)
        require(root.optInt("ailiailiSettingsBackup", 0) == 1) { "不是 ailiaili 的设置备份文件" }
        val settingsObj = root.optJSONObject("settings") ?: error("备份里没有设置内容")
        val next = decodeAppSettings(settingsObj)
        _settings.value = next
        file.parentFile?.mkdirs()
        file.writeTextAtomic(encodeAppSettings(next).toString(2))
    }
}

internal fun decodeAppSettings(o: JSONObject): AppSettings = AppSettings(
    theme = runCatching { ThemeMode.valueOf(o.optString("theme", ThemeMode.System.name)) }.getOrDefault(ThemeMode.System),
    shellLayout = runCatching { ShellLayout.valueOf(o.optString("shellLayout", "Auto")) }.getOrDefault(ShellLayout.Auto),
    startupPage = StartupPage.entries.firstOrNull { it.name == o.optString("startupPage") } ?: StartupPage.Home,
    favoriteGrid = o.optBoolean("favoriteGrid", true),
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
    danmakuOpacityPercent = o.optInt("danmakuOpacityPercent", 100).coerceIn(30, 100),
    danmakuMaxOnScreen = o.optInt("danmakuMaxOnScreen", 80).coerceIn(10, 80),
    defaultQn = o.optInt("defaultQn", 80),
    noLogin1080 = o.optBoolean("noLogin1080", true),
    showRelated = o.optBoolean("showRelated", true),
    subtitleEnabled = o.optBoolean("subtitleEnabled", false),
    subtitleLan = o.optString("subtitleLan"),
    subtitleDelaySec = o.optDouble("subtitleDelaySec", 0.0),
    subtitleFontSp = o.optInt("subtitleFontSp", 17).coerceIn(12, 36),
    subtitleBottomPercent = o.optInt("subtitleBottomPercent", 4).coerceIn(0, 40),
    customUserAgent = o.optString("customUserAgent").trim().take(300),
    httpProxy = o.optString("httpProxy").trim().take(200),
    playbackSpeed = o.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.25f, 4f),
    autoPlayNext = o.optBoolean("autoPlayNext", true),
    autoPlayRelated = o.optBoolean("autoPlayRelated", false),
    desktopHardwareDecoding = o.optBoolean("desktopHardwareDecoding", true),
    desktopHighQualityAudio = o.optBoolean("desktopHighQualityAudio", false),
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
    fontPath = o.optString("fontPath"),
    accentFollowSystem = o.optBoolean("accentFollowSystem", false),
    reduceMotion = o.optBoolean("reduceMotion", false),
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
    liveStartNotify = o.optBoolean("liveStartNotify", true),
    playerVolume = o.optDouble("playerVolume", 0.8).toFloat().coerceIn(0f, 1f),
    lastAudiblePlayerVolume = o.optDouble("lastAudiblePlayerVolume", o.optDouble("playerVolume", 0.8))
        .toFloat().takeIf { it.isFinite() && it > 0f }?.coerceAtMost(1f) ?: 0.8f,
    downloadDir = o.optString("downloadDir"),
    downloadQuality = o.optInt("downloadQuality", 0).coerceAtLeast(0),
    backgroundDownloads = o.optBoolean("backgroundDownloads", true),
    unmeteredDownloads = o.optBoolean("unmeteredDownloads"),
    downloadDanmaku = o.optBoolean("downloadDanmaku"),
    downloadSubtitles = o.optBoolean("downloadSubtitles", true),
    seekStepSeconds = o.optInt("seekStepSeconds", 0).coerceIn(0, 60),
    playerScale = PlayerScale.entries.firstOrNull { it.name == o.optString("playerScale") } ?: PlayerScale.Fit,
    danmakuDelayMs = o.optInt("danmakuDelayMs", 0).coerceIn(-30_000, 30_000),
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
    .put("danmakuOpacityPercent", next.danmakuOpacityPercent.coerceIn(30, 100))
    .put("danmakuMaxOnScreen", next.danmakuMaxOnScreen.coerceIn(10, 80))
    .put("defaultQn", next.defaultQn)
    .put("noLogin1080", next.noLogin1080)
    .put("showRelated", next.showRelated)
    .put("subtitleEnabled", next.subtitleEnabled)
    .put("subtitleLan", next.subtitleLan)
    .put("subtitleDelaySec", next.subtitleDelaySec)
    .put("subtitleFontSp", next.subtitleFontSp)
    .put("subtitleBottomPercent", next.subtitleBottomPercent)
    .put("customUserAgent", next.customUserAgent)
    .put("httpProxy", next.httpProxy)
    .put("playbackSpeed", next.playbackSpeed.coerceIn(0.25f, 4f).toDouble())
    .put("autoPlayNext", next.autoPlayNext)
    .put("autoPlayRelated", next.autoPlayRelated)
    .put("desktopHardwareDecoding", next.desktopHardwareDecoding)
    .put("desktopHighQualityAudio", next.desktopHighQualityAudio)
    .put("sponsorBlock", next.sponsorBlock)
    .put("sponsorCategories", SponsorCategories.sanitize(next.sponsorCategories))
    .put("cdnMode", next.cdnMode.name)
    .put("cdnModeMigrated", true)
    .put("cdnManualId", next.cdnManualId)
    .put("blockPcdn", next.blockPcdn)
    .put("bufferAuto", next.bufferAuto)
    .put("networkCachingMs", next.networkCachingMs.coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS))
    .put("accentHex", next.accentHex)
    .put("fontPath", next.fontPath)
    .put("accentFollowSystem", next.accentFollowSystem)
    .put("reduceMotion", next.reduceMotion)
    .put("autoCheckUpdate", next.autoCheckUpdate)
    .put("skippedVersion", next.skippedVersion)
    .put("acceptedDisclaimerVersion", next.acceptedDisclaimerVersion)
    .put("closeToTray", next.closeToTray)
    .put("liveStartNotify", next.liveStartNotify)
    .put("playerVolume", next.playerVolume.coerceIn(0f, 1f).toDouble())
    .put("lastAudiblePlayerVolume", next.lastAudiblePlayerVolume.toDouble())
    .put("downloadDir", next.downloadDir)
    .put("downloadQuality", next.downloadQuality)
    .put("backgroundDownloads", next.backgroundDownloads)
    .put("unmeteredDownloads", next.unmeteredDownloads)
    .put("downloadDanmaku", next.downloadDanmaku)
    .put("downloadSubtitles", next.downloadSubtitles)
    .put("startupPage", next.startupPage.name)
    .put("favoriteGrid", next.favoriteGrid)
    .put("seekStepSeconds", next.seekStepSeconds)
    .put("playerScale", next.playerScale.name)
    .put("danmakuDelayMs", next.danmakuDelayMs)
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
            buildList {
                for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }.distinctBy { it.lowercase() }.take(20)
        }.getOrDefault(emptyList())
    }

    fun add(keyword: String) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        val next = (listOf(word) + _items.value.filterNot { it.equals(word, ignoreCase = true) }).take(20)
        if (next == _items.value) return
        _items.value = next
        persist()
    }

    fun remove(keyword: String) {
        val next = _items.value.filterNot { it.equals(keyword, ignoreCase = true) }
        if (next == _items.value) return
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
            file.writeTextAtomic(arr.toString())
        }
    }
}

class OfflineCache(private val root: AppFile) {
    private val locationFile = AppFile(root, "locations.json")
    @Volatile private var knownLocations = runCatching {
        val entries = JSONArray(locationFile.readText())
        (0 until entries.length()).map { entries.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
    @Volatile private var extraRoot: AppFile? = null
    /** Android SAF 导出目录（content:// tree Uri）：下载仍写应用缓存，完成后复制过去。 */
    @Volatile private var exportTree: String? = null

    @Synchronized
    fun setExtraRoot(path: String?) {
        val trimmed = path?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmed != null && trimmed.startsWith("content://")) {
            exportTree = trimmed
            extraRoot = null
        } else {
            exportTree = null
            extraRoot = trimmed?.let { AppFile(it) }
            trimmed?.let(::rememberLocation)
        }
    }

    @Synchronized
    private fun rememberLocation(path: String) {
        if (path in knownLocations) return
        knownLocations = knownLocations + path
        runCatching {
            root.mkdirs()
            val data = JSONArray()
            knownLocations.forEach { data.put(it) }
            locationFile.writeTextAtomic(data.toString())
        }
    }

    private fun roots(): List<AppFile> =
        (listOfNotNull(root, extraRoot) + knownLocations.map { AppFile(it) }).distinctBy { it.absolutePath }

    fun list(): List<CacheEntry> = roots().flatMap { base ->
        base.listFiles().flatMap { folder ->
            listOfNotNull(readEntry(folder)) + folder.listFiles()
                .filter { it.name.toLongOrNull()?.let { cid -> cid > 0L } == true }
                .mapNotNull(::readEntry)
        }
    }.sortedByDescending { it.storedAtMs }.distinctBy { it.key }

    private fun readEntry(folder: AppFile): CacheEntry? = runCatching {
        val meta = AppFile(folder, "meta.json")
        if (!meta.isFile()) return null
        val data = JSONObject(meta.readText())
        fun track(key: String, default: String): AppFile? = cacheTrack(folder, data.optString(key, default))
        val video = track("video", "video.m4s")
        val merged = track("merged", "merged.mp4")
        val audio = track("audio", "audio.m4s")
        val primary = merged ?: video ?: return null
        CacheEntry(
            bvid = data.optString("bvid"),
            title = data.optString("title"),
            cover = data.optString("cover"),
            videoPath = primary.absolutePath,
            audioPath = audio?.absolutePath.takeIf { merged == null },
            qn = data.optInt("qn"),
            cid = data.optLong("cid", 0L),
            storedAtMs = primary.lastModified(),
            storedBytes = listOfNotNull(video, audio, merged).distinctBy { it.absolutePath }.sumOf { it.length() } +
                (primary.parentFile?.let { AppFile(it, "playback.json").length() } ?: 0L),
            episodeId = data.optLong("episodeId"),
        )
    }.getOrNull()

    private fun cacheTrack(folder: AppFile, name: String): AppFile? {
        if (name.isBlank() || '\\' in name || name.split('/').any { it == "." || it == ".." }) return null
        val rootPath = java.nio.file.Path.of(folder.absolutePath)
        val resolved = rootPath.resolve(name).normalize()
        if (resolved == rootPath || !resolved.startsWith(rootPath)) return null
        return AppFile(resolved.toString()).takeIf { it.isFile() && it.length() > 0 }
    }

    private val downloadGates = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun download(
        request: DownloadRequest,
        info: PlayInfo,
        client: HttpEngine,
        cancellation: app.ailiaili.core.network.DownloadCancellation,
        onProgress: (DownloadPhase, Long, Long) -> Unit,
    ): CacheEntry {
        val destination = request.destination
        val tree = if (destination == null) exportTree else destination.takeIf { it.startsWith("content://") }
        val path = destination?.takeIf { it.isNotBlank() && tree == null }
        val base = if (destination == null) extraRoot ?: root else path?.let { AppFile(it) } ?: root
        path?.let(::rememberLocation)
        val titleRoot = AppFile(base, cacheFolderName(request.bvid))
        val gate = downloadGates.getOrPut(titleRoot.absolutePath) { Any() }
        synchronized(gate) {
            val folder = AppFile(titleRoot, request.cid.toString())
            val staging = AppFile(folder, ".pending-${request.qn}")
            staging.mkdirs()
            fun fetch(url: String, name: String, phase: DownloadPhase): AppFile {
                cancellation.check()
                val file = AppFile(staging, name)
                val origin = AppFile(staging, "$name.source")
                onProgress(phase, 0, -1)
                if (!file.isFile() || file.length() == 0L || runCatching { origin.readText() }.getOrNull() != url) {
                    client.downloadTo(
                        url,
                        mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to app.ailiaili.core.model.effectiveUserAgent()),
                        file.absolutePath, cancellation,
                    ) { done, total -> onProgress(phase, done, total) }
                    origin.writeTextAtomic(url)
                } else {
                    onProgress(phase, file.length(), file.length())
                }
                return file
            }

            val video = fetch(info.videoUrl, "video.m4s", DownloadPhase.Video)
            val audio = info.audioUrl?.let { fetch(it, "audio.m4s", DownloadPhase.Audio) }
            if (audio == null) staging.listFiles().filter { it.name.startsWith("audio.m4s") }.forEach { it.deleteRecursively() }
            cancellation.check()
            onProgress(DownloadPhase.Finalizing, 0, -1)
            val merged = AppFile(staging, "merged.mp4")
            merged.deleteRecursively()
            val muxed = runCatching {
                muxToMp4(video.absolutePath, audio?.absolutePath, merged.absolutePath, info.videoCodecs, info.audioCodecs)
            }.getOrDefault(false) && merged.length() > 0
            val generation = "media-${java.util.UUID.randomUUID()}"
            val media = AppFile(folder, generation)
            java.nio.file.Files.move(java.nio.file.Path.of(staging.absolutePath), java.nio.file.Path.of(media.absolutePath))
            val metadata = JSONObject()
                .put("bvid", request.bvid)
                .put("cid", request.cid)
                .put("episodeId", request.epId)
                .put("title", request.title)
                .put("cover", request.cover)
                .put("qn", info.actualQn)
                .put("video", "$generation/${video.name}")
                .put("audio", audio?.let { "$generation/${it.name}" }.orEmpty())
                .put("merged", if (muxed) "$generation/${merged.name}" else "")
                .put("videoCodecs", info.videoCodecs)
                .put("audioCodecs", info.audioCodecs)
            try {
                AppFile(folder, "meta.json").writeTextAtomic(metadata.toString())
            } catch (failure: Exception) {
                runCatching {
                    java.nio.file.Files.move(java.nio.file.Path.of(media.absolutePath), java.nio.file.Path.of(staging.absolutePath))
                }
                throw failure
            }
            media.listFiles().filter { it.name.endsWith(".source") }.forEach { it.deleteRecursively() }
            folder.listFiles().filter { it.name.startsWith("media-") && it.name != generation }
                .forEach { it.deleteRecursively() }
            val entry = checkNotNull(readEntry(folder)) { "无法读取已保存的缓存" }
            if (tree != null && !isDesktopHost()) {
                runCatching {
                    kotlinx.coroutines.runBlocking {
                        exportCacheFiles(downloadExports(entry), tree)
                    }
                }
            }
            return entry
        }
    }

    fun pruneSources(entry: CacheEntry) {
        roots().forEach { base ->
            val parent = AppFile(base, cacheFolderName(entry.bvid))
            val gate = downloadGates.getOrPut(parent.absolutePath) { Any() }
            synchronized(gate) {
                val folder = listOf(AppFile(parent, entry.cid.toString()), parent)
                    .firstOrNull { readEntry(it)?.videoPath == entry.videoPath } ?: return@synchronized
                val meta = AppFile(folder, "meta.json")
                val data = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return@synchronized
                val merged = AppFile(folder, data.optString("merged", "merged.mp4"))
                if (merged.absolutePath != entry.videoPath || !merged.isFile()) return@synchronized
                val video = data.optString("video", "video.m4s")
                val audio = data.optString("audio", "audio.m4s")
                if (video == data.optString("merged", "merged.mp4") && audio.isEmpty()) return@synchronized
                data.put("video", data.optString("merged", "merged.mp4")).put("audio", "")
                meta.writeTextAtomic(data.toString())
                val now = java.nio.file.Path.of(entry.videoPath).toAbsolutePath().normalize()
                listOf(video, audio).filter { it.isNotEmpty() }.forEach { track ->
                    val path = java.nio.file.Path.of(folder.absolutePath).resolve(track).normalize()
                    if (path.parent == now.parent && path != now) AppFile(path.toString()).deleteRecursively()
                }
            }
        }
    }

    fun discard(request: DownloadRequest) {
        val custom = request.destination?.takeIf { it.isNotBlank() && !it.startsWith("content://") }?.let { AppFile(it) }
        (roots() + listOfNotNull(custom)).distinctBy { it.absolutePath }.forEach { base ->
            val parent = AppFile(base, cacheFolderName(request.bvid))
            val gate = downloadGates.getOrPut(parent.absolutePath) { Any() }
            synchronized(gate) {
                AppFile(AppFile(parent, request.cid.toString()), ".pending-${request.qn}").deleteRecursively()
            }
        }
    }

    fun delete(bvid: String, cid: Long? = null) {
        roots().forEach { base ->
            val parent = AppFile(base, cacheFolderName(bvid))
            val gate = downloadGates.getOrPut(parent.absolutePath) { Any() }
            synchronized(gate) {
                if (cid == null) {
                    remove(parent)
                } else {
                    val part = AppFile(parent, cid.toString())
                    part.listFiles().filterNot { it.name.startsWith(".pending-") }.forEach(::remove)
                    if (readEntry(parent)?.cid == cid) {
                        parent.listFiles().filter { it.isFile() }.forEach(::remove)
                    }
                }
            }
        }
    }

    private fun remove(file: AppFile) {
        file.deleteRecursively()
        check(!file.exists()) { "离线缓存未能删除" }
    }

    private fun cacheFolderName(bvid: String): String = bvid.ifBlank { "local" }.also { name ->
        require(name.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

}

internal fun downloadExports(entry: CacheEntry): List<CacheExportFile> {
    val title = entry.title.map { char ->
        if (char.isISOControl() || char in "/\\:*?\"<>|") '_' else char
    }.joinToString("").take(48).trim(' ', '.').ifBlank { entry.bvid }
    val base = "$title-${entry.bvid}-${entry.cid}"
    val videoSuffix = if (entry.videoPath.endsWith(".mp4", ignoreCase = true)) ".mp4" else "-video.m4s"
    return buildList {
        add(CacheExportFile(entry.videoPath, base + videoSuffix, "video/mp4"))
        entry.audioPath?.let { add(CacheExportFile(it, "$base-audio.m4s", "audio/mp4")) }
    }
}

fun appFiles(): AppFile = appStorageDir()
