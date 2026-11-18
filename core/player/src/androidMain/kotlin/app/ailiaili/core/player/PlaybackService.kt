package app.ailiaili.core.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * 后台播放服务（借鉴 BiliPai 的 Media3 媒体集成方案）：
 * - MediaSessionService 自动维护媒体通知与锁屏/蓝牙控制；
 * - 音频焦点由 Media3 接管：来电/其他应用出声自动暂停，恢复焦点自动续播；
 * - 播放器实例由 [PlayerEngine] 创建后 attach 进来，UI 与服务共用同一个
 *   ExoPlayer，不额外起第二路解码。
 *
 * 服务随播放启动、随释放停止；暂停后退出前台（通知保留），不长期占保活。
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())
    private var pendingAttach: Runnable? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // 被系统重启时没有可 attach 的播放器：直接停掉，等 UI 重新起播。
        if (session == null && pendingAttach == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pendingAttach?.let { main.removeCallbacks(it) }
        pendingAttach = null
        session?.run {
            player.removeListener(playbackListener)
            release()
        }
        session = null
        if (attached === this) attached = null
        super.onDestroy()
    }

    private val playbackListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // 暂停后不再需要前台保活：退出前台但保留通知，
            // 用户点通知播放时 Media3 会重新进前台。
            if (!isPlaying) stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    internal fun attach(player: ExoPlayer, metadata: MediaMetadata) {
        if (session?.player === player) return
        session?.run {
            player.removeListener(playbackListener)
            release()
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        session = MediaSession.Builder(this, player)
            .apply { if (launchIntent != null) setSessionActivity(launchIntent) }
            .build()
        player.addListener(playbackListener)
        // 通知/锁屏显示稿件标题与 UP 名：只改当前条目的元数据，
        // 不动播放位置与播放状态。
        runCatching {
            val index = player.currentMediaItemIndex
            val current = player.getMediaItemAt(index)
            player.replaceMediaItem(
                index,
                current.buildUpon().setMediaMetadata(metadata).build(),
            )
        }
        attached = this
    }

    companion object {
        @Volatile
        internal var attached: PlaybackService? = null

        internal fun start(context: android.content.Context) {
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java),
                )
            }
        }

        internal fun stop(context: android.content.Context) {
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
        }
    }
}

/**
 * 引擎侧的媒体集成入口：起播时打开（通知+焦点），释放时关闭。
 * 服务 onCreate 异步于 startForegroundService，attach 统一 post 到主线程
 * 队列尾部执行；服务未就绪时挂一个待办，onStartCommand 后补交。
 */
object AndroidMediaIntegration {
    @Volatile var enabled: Boolean = true

    private val main = Handler(Looper.getMainLooper())

    fun onPlayStarted(context: android.content.Context, player: ExoPlayer, title: String, artist: String) {
        if (!enabled) return
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "哔哩哔哩" })
            .setArtist(artist)
            .build()
        PlaybackService.start(context)
        main.post {
            val service = PlaybackService.attached
            if (service != null) {
                service.attach(player, metadata)
            } else {
                // 服务还没 onCreate：稍后重试一次（仍失败就放弃，
                // 下次起播会再走一遍，不影响播放本身）。
                main.postDelayed({
                    PlaybackService.attached?.attach(player, metadata)
                }, 120)
            }
        }
    }

    fun onReleased(context: android.content.Context) {
        PlaybackService.stop(context)
    }
}
