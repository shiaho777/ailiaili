package app.ailiaili

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DownloadNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun monitorDownloadNetwork(context: Context) {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val lock = Any()
    var defaultNetwork: Network? = null
    var capabilities: NetworkCapabilities? = null
    fun refresh() {
        val current = capabilities
        val state = when {
            current?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> DownloadNetwork.Offline
            AppGraph.repo.settings.value.unmeteredDownloads &&
                !current.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> DownloadNetwork.Metered
            else -> DownloadNetwork.Available
        }
        AppGraph.downloads.setNetwork(state)
    }
    AppGraph.downloads.setNetwork(DownloadNetwork.Offline)
    connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = synchronized(lock) {
            defaultNetwork = network
            capabilities = null
        }

        override fun onLost(network: Network) = synchronized(lock) {
            if (defaultNetwork == network) {
                defaultNetwork = null
                capabilities = null
                refresh()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = synchronized(lock) {
            if (defaultNetwork == network) {
                capabilities = networkCapabilities
                refresh()
            }
        }
    })
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        AppGraph.repo.settings.map { it.unmeteredDownloads }.distinctUntilChanged().collect {
            synchronized(lock) { refresh() }
        }
    }
}
