package app.ailiaili.core.data

import app.ailiaili.core.network.BiliClient
import app.ailiaili.core.network.HttpEngine
import app.ailiaili.core.network.SharedHttp

object AppGraph {
    lateinit var repo: BiliRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var searchHistory: SearchHistory
        private set
    lateinit var cache: OfflineCache
        private set
    lateinit var dynamicSeen: DynamicSeenStore
        private set
    lateinit var http: HttpEngine
        private set

    fun init() {
        if (::repo.isInitialized) return
        val dir = appStorageDir()
        val sessionStore = SessionStore(AppFile(dir, "session.json")).apply { load() }
        settings = SettingsStore(AppFile(dir, "settings.json")).apply { load() }
        searchHistory = SearchHistory(AppFile(dir, "search-history.json")).apply { load() }
        dynamicSeen = DynamicSeenStore(AppFile(dir, "dynamic-seen.json")).apply { load() }
        cache = OfflineCache(AppFile(dir, "vcache")).apply {
            setExtraRoot(settings.current.downloadDir)
        }
        http = SharedHttp.default
        val client = BiliClient(
            session = { sessionStore.current },
            updateSession = { sessionStore.save(it) },
        )
        repo = BiliRepositoryImpl(client, settings, sessionStore)
    }
}
