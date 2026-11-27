package app.ailiaili

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.DownloadNetwork
import app.ailiaili.core.data.DownloadPhase
import app.ailiaili.core.data.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "离线下载", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) AppGraph.downloads.pauseAll()
        if (observer == null) {
            val initial = downloadNotice(AppGraph.downloads.tasks.value, AppGraph.downloads.network.value)
            val started = runCatching {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification(initial ?: DownloadNotice(null, 0, null)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }.isSuccess
            if (!started) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            observer = scope.launch {
                combine(AppGraph.downloads.tasks, AppGraph.downloads.network, AppGraph.repo.settings) { tasks, network, settings ->
                    if (settings.backgroundDownloads) downloadNotice(tasks, network) else null
                }.distinctUntilChanged().collect { notice ->
                    if (notice == null) stopSelf() else notifications.notify(NOTIFICATION_ID, notification(notice))
                }
            }
        }
        return START_NOT_STICKY
    }

    private var observer: kotlinx.coroutines.Job? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        AppGraph.downloads.pauseAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun downloadNotice(tasks: List<DownloadTask>, network: DownloadNetwork): DownloadNotice? {
        val work = tasks.filter { it.phase.isWorking || it.phase == DownloadPhase.Queued }
        val current = work.firstOrNull { it.phase.isWorking }
        if (work.isEmpty() || current == null && network != DownloadNetwork.Available) return null
        val percent = current?.takeIf { it.total > 0 }?.let {
            (it.done.toDouble() * 100 / it.total).toInt().coerceIn(0, 100)
        }
        return DownloadNotice((current ?: work.first()).request.title, work.size, percent)
    }

    private fun notification(notice: DownloadNotice): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val contentIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java).setAction(SHOW_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
        val pauseIntent = PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).setAction(PAUSE), flags)
        return Notification.Builder(this, CHANNEL)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (notice.count > 0) "离线下载 · ${notice.count} 项" else "准备离线下载")
            .setContentText(notice.title ?: "正在准备任务")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setProgress(100, notice.percent ?: 0, notice.percent == null)
            .addAction(Notification.Action.Builder(null, "暂停全部", pauseIntent).build())
            .build()
    }

    private data class DownloadNotice(val title: String?, val count: Int, val percent: Int?)

    companion object {
        const val SHOW_DOWNLOADS = "app.ailiaili.SHOW_DOWNLOADS"
        private const val PAUSE = "app.ailiaili.PAUSE_DOWNLOADS"
        private const val CHANNEL = "offline_downloads"
        private const val NOTIFICATION_ID = 3601

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
