package app.ailiaili.core.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ailiaili.core.model.PlayInfo

interface PlaybackListener {
    fun onError() {}
    fun onEnded(ended: Boolean) {}
    fun onIsPlayingChanged(isPlaying: Boolean) {}
    fun onPositionJump() {}
}

/**
 * 播放器。Android 用 Media3，桌面用系统里的 VLC。界面只看见这一层。
 */
expect class PlayerEngine() {
    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val isBuffering: Boolean
    val playbackSpeed: Float
    var volume: Float

    /** 网络预读缓冲（毫秒）。桌面端映射到 VLC :network-caching；Android 端暂存。 */
    var networkCachingMs: Int

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun addListener(listener: PlaybackListener)
    fun removeListener(listener: PlaybackListener)
    fun prepare()
    fun freeze()

    fun play(info: PlayInfo, positionMs: Long = 0, audioOnly: Boolean = false)
    fun playAudioOnly(positionMs: Long): Boolean
    fun restoreVideo(positionMs: Long)
    fun failOver(positionMs: Long): Boolean
    fun playLive(url: String)
    fun playFiles(videoPath: String, audioPath: String?)
    fun release()

    /**
     * 截取当前视频画面并保存到系统图片目录。
     * 返回给用户看的结果文案；失败返回 null。
     * 桌面从帧泵取现成帧；Android 用 PixelCopy 抓 PlayerView。
     */
    suspend fun snapshot(displayName: String): String?
}

@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier)
