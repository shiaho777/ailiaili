package app.ailiaili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliTheme
import app.ailiaili.core.designsystem.InstallBiliImages

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidHost.activity = this
        enableEdgeToEdge()
        setContent {
            InstallBiliImages()
            val settings by AppGraph.repo.settings.collectAsState()
            BiliTheme(settings.theme, settings.accentHex, settings.accentFollowSystem) {
                AppRoot()
                UpdatePrompt(settings.autoCheckUpdate, settings.skippedVersion)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AndroidHost.activity = this
    }
}
