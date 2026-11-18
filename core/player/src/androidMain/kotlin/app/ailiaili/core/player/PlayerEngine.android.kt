package app.ailiaili.core.player

import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.USER_AGENT
import kotlin.coroutines.resume

actual class PlayerEngine actual constructor() {
    /**
     * ExoPlayer 的 LoadControl 构造后不可替换；用委托包一层，
     * 换片时按 BufferPolicy 的输出重建内层 DefaultLoadControl。
     * 未换片期间行为与直接持有完全一致（全部调用透传）。
     */
    private val loadControl = SwitchableLoadControl()

    val player: ExoPlayer = ExoPlayer.Builder(AndroidHost.context)
        .setLoadControl(loadControl)
        .build()

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(USER_AGENT)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
            ),
        )
    private val progressive = ProgressiveMediaSource.Factory(http)
    private var backups: List<String> = emptyList()
    private var audioUrl: String? = null
    private var videoUrl: String? = null
    private val listeners = linkedSetOf<PlaybackListener>()
    private val bridge = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            listeners.toList().forEach { it.onError() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ended = playbackState == Player.STATE_ENDED
            listeners.toList().forEach { it.onEnded(ended) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            listeners.toList().forEach { it.onPositionJump() }
        }
    }

    init {
        player.addListener(bridge)
    }

    actual val currentPosition: Long get() = player.currentPosition
    actual val duration: Long get() = player.duration
    actual val isPlaying: Boolean get() = player.isPlaying
    actual val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    actual val playbackSpeed: Float get() = player.playbackParameters.speed
    actual var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    private var live = false

    private var loadedTag = ""

    actual val mediaTag: String get() = loadedTag

    /**
     * 与桌面同一套 BufferPolicy 决策值；换片时映射成 Media3 缓冲参数生效。
     * 弱网深预读抗抖动，快线浅预读省内存。
     */
    actual var networkCachingMs: Int = app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS
        set(value) {
            field = value.coerceIn(
                app.ailiaili.core.model.NETWORK_CACHING_MIN_MS,
                app.ailiaili.core.model.NETWORK_CACHING_MAX_MS,
            )
        }

    private fun applyBufferPolicy() {
        val spec = app.ailiaili.core.player.BufferPolicy.androidDurations(networkCachingMs, live)
        loadControl.swap(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(spec.minBufferMs, spec.maxBufferMs, spec.playbackMs, spec.rebufferMs)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
    }

    actual fun play() {
        player.play()
    }

    actual fun pause() {
        player.pause()
    }

    actual fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    actual fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    actual fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    actual fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    actual fun prepare() = Unit

    actual fun freeze() {
        player.pause()
    }

    actual fun play(info: PlayInfo, positionMs: Long, audioOnly: Boolean) {
        backups = info.backupUrls
        val audio = info.audioUrl
        audioUrl = audio
        videoUrl = info.videoUrl
        if (audioOnly && !audio.isNullOrBlank()) attachSingle(audio, positionMs) else attach(info.videoUrl, positionMs)
    }

    actual fun playAudioOnly(positionMs: Long): Boolean {
        val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return false
        attachSingle(audio, positionMs)
        return true
    }

    actual fun restoreVideo(positionMs: Long) {
        val video = videoUrl?.takeIf { it.isNotBlank() } ?: return
        attach(video, positionMs)
    }

    actual fun failOver(positionMs: Long): Boolean {
        val next = backups.firstOrNull() ?: return false
        backups = backups.drop(1)
        attach(next, positionMs)
        return true
    }

    actual fun playLive(url: String) {
        live = true
        loadedTag = url
        applyBufferPolicy()
        val mime = when {
            url.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.contains(".flv") -> MimeTypes.VIDEO_FLV
            else -> null
        }
        val item = MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
        val props = mutableMapOf(
            "Referer" to "https://live.bilibili.com/",
            "Origin" to "https://live.bilibili.com",
        )
        runCatching {
            val cookie = app.ailiaili.core.data.AppGraph.repo.session.value.cookieHeader()
            if (cookie.isNotBlank()) props["Cookie"] = cookie
        }
        val liveHttp = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(props)
            .setAllowCrossProtocolRedirects(true)
        player.setMediaSource(DefaultMediaSourceFactory(liveHttp).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
    }

    actual fun playFiles(videoPath: String, audioPath: String?, positionMs: Long) {
        loadedTag = videoPath
        val video = progressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(videoPath))))
        val source = if (audioPath.isNullOrBlank()) {
            video
        } else {
            MergingMediaSource(
                video,
                progressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(audioPath)))),
            )
        }
        player.setMediaSource(source, positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    actual fun release() {
        // Media3 强制要求 release 在创建 player 的线程（主线程）执行。
        // 调用方即使从 IO 线程进来，也切回主线程，避免 “Player is accessed on the wrong thread” 崩溃。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { player.release() }
        } else {
            Handler(Looper.getMainLooper()).post {
                runCatching { player.release() }
            }
        }
    }

    /** 当前挂到引擎上的 PlayerView；VideoSurface 组合时登记。 */
    @Volatile internal var attachedView: PlayerView? = null

    actual suspend fun snapshot(displayName: String): String? {
        val view = attachedView ?: return null
        // 只抓视频表面（SurfaceView），不带弹幕/控制栏叠层，画面干净。
        val surfaceView = view.videoSurfaceView as? android.view.SurfaceView ?: return null
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0) return null
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val ok = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            val listener = android.view.PixelCopy.OnPixelCopyFinishedListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result == android.view.PixelCopy.SUCCESS)
                }
            }
            val launched = runCatching {
                android.view.PixelCopy.request(surfaceView, bitmap, listener, Handler(Looper.getMainLooper()))
            }
            if (launched.isFailure && continuation.isActive) continuation.resume(false)
        }
        if (!ok) return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                app.ailiaili.core.data.saveImageBytes(stream.toByteArray(), displayName.ifBlank { "snapshot" })
            }.getOrNull()
        }
    }

    private fun attach(videoUrl: String, positionMs: Long) {
        live = false
        loadedTag = videoUrl
        applyBufferPolicy()
        val video = progressive.createMediaSource(MediaItem.fromUri(videoUrl))
        val audio = audioUrl
        val source = if (audio.isNullOrBlank()) {
            video
        } else {
            MergingMediaSource(video, progressive.createMediaSource(MediaItem.fromUri(audio)))
        }
        player.setMediaSource(source)
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
    }

    private fun attachSingle(url: String, positionMs: Long) {
        live = false
        // 听视频只是同一条视频换了流，媒体身份沿用视频 URL，进度归属不变。
        loadedTag = videoUrl?.takeIf { it.isNotBlank() } ?: url
        applyBufferPolicy()
        player.setMediaSource(progressive.createMediaSource(MediaItem.fromUri(url)))
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
    }
}

/**
 * LoadControl 委托：ExoPlayer 构造后不能换 LoadControl，
 * 但换片（setMediaSource + prepare）之间可以安全替换内层实现。
 * 所有回调透传；swap 只应在换片前调用。
 */
private class SwitchableLoadControl : androidx.media3.exoplayer.LoadControl {
    @Volatile
    private var delegate: androidx.media3.exoplayer.LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 1_500, 5_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    fun swap(next: androidx.media3.exoplayer.LoadControl) {
        val old = delegate
        if (old === next) return
        delegate = next
        runCatching { old.onReleased() }
    }

    override fun onPrepared() = delegate.onPrepared()

    override fun onTracksSelected(
        renderers: Array<androidx.media3.exoplayer.Renderer>,
        trackGroups: androidx.media3.exoplayer.source.TrackGroupArray,
        trackSelections: Array<androidx.media3.exoplayer.trackselection.ExoTrackSelection>,
    ) = delegate.onTracksSelected(renderers, trackGroups, trackSelections)

    override fun onStopped() = delegate.onStopped()

    override fun onReleased() = delegate.onReleased()

    override fun getAllocator(): androidx.media3.exoplayer.upstream.Allocator = delegate.allocator

    override fun getBackBufferDurationUs(): Long = delegate.backBufferDurationUs

    override fun retainBackBufferFromKeyframe(): Boolean = delegate.retainBackBufferFromKeyframe()

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
    ): Boolean = delegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)

    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean = delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)

    override fun shouldContinuePreloading(
        timeline: androidx.media3.common.Timeline,
        mediaPeriodId: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(timeline, mediaPeriodId, bufferedDurationUs)
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    androidx.compose.runtime.DisposableEffect(engine) {
        onDispose { engine.attachedView = null }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = engine.player
                useController = false
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                clearSubtitleBackdrop(this)
                engine.attachedView = this
            }
        },
        update = { view ->
            if (view.player !== engine.player) view.player = engine.player
            clearSubtitleBackdrop(view)
            engine.attachedView = view
        },
        modifier = modifier,
    )
}

private fun clearSubtitleBackdrop(view: PlayerView) {
    view.subtitleView?.apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setStyle(
            CaptionStyleCompat(
                AndroidColor.WHITE,
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                AndroidColor.TRANSPARENT,
                null,
            ),
        )
    }
}
