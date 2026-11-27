package app.ailiaili.core.data

import app.ailiaili.core.network.BiliClient
import app.ailiaili.core.network.HttpEngine
import app.ailiaili.core.network.SharedHttp

object AppGraph {
    lateinit var repo: BiliRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var accounts: AccountStore
        private set
    lateinit var searchHistory: SearchHistory
        private set
    lateinit var playbackMemory: PlaybackMemory
        private set
    lateinit var cache: OfflineCache
        private set
    lateinit var downloads: DownloadQueue
        private set
    lateinit var dynamicSeen: DynamicSeenStore
        private set
    lateinit var http: HttpEngine
        private set

    fun init() {
        if (::repo.isInitialized) return
        val dir = appStorageDir()
        val sessionStore = SessionStore(AppFile(dir, "session.json")).apply { load() }
        accounts = AccountStore(AppFile(dir, "accounts.json")).apply { load() }
        settings = SettingsStore(AppFile(dir, "settings.json")).apply { load() }
        searchHistory = SearchHistory(AppFile(dir, "search-history.json")).apply { load() }
        playbackMemory = PlaybackMemory(AppFile(dir, "play-memory.json"))
        dynamicSeen = DynamicSeenStore(AppFile(dir, "dynamic-seen.json")).apply { load() }
        cache = OfflineCache(AppFile(dir, "vcache")).apply {
            setExtraRoot(settings.current.downloadDir)
        }
        http = SharedHttp.default
        // 自定义 UA（高级设置）：空串回退默认浏览器 UA。
        app.ailiaili.core.model.userAgentProvider = {
            settings.current.customUserAgent.ifBlank { app.ailiaili.core.model.USER_AGENT }
        }
        // HTTP 代理（高级设置）：空串直连；网络层按当前值热切换客户端。
        app.ailiaili.core.model.proxyProvider = { settings.current.httpProxy }
        val client = BiliClient(
            session = { sessionStore.current },
            updateSession = { sessionStore.save(it) },
        )
        repo = BiliRepositoryImpl(client, settings, sessionStore, accounts)
        downloads = DownloadQueue(
            AppFile(dir, "downloads.json"),
            resolve = { request ->
                val episode = if (request.epId > 0) {
                    repo.pgcPlay(request.epId, request.aid, request.cid, request.qn).getOrNull()
                } else null
                episode ?: repo.playAt(request.bvid, request.cid, request.qn).getOrThrow()
            },
            transfer = { request, info, cancellation, progress ->
                cache.download(request, info, http, cancellation, progress)
            },
            removePartial = cache::discard,
            saveExtras = { request, info, entry ->
                cachePlaybackExtras(repo, request, info, entry) {
                    downloads.network.value == DownloadNetwork.Available
                }
            },
        )
        LiveAlerts.start()
    }
}
