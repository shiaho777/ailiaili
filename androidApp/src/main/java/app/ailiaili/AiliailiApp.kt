package app.ailiaili

import android.app.Application
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.data.AppGraph

class AiliailiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidHost.install(this)
        AppGraph.init()
    }
}
