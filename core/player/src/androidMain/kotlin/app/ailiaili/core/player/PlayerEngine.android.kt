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
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.ailiaili.core.data.AndroidHost
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.PlayerScale
import androidx.media3.ui.AspectRatioFrameLayout
import app.ailiaili.core.model.USER_AGENT
import kotlin.coroutines.resume

actual class PlayerEngine actual constructor(private val preview: Boolean) {
    private val loadControl = SwitchableLoadControl(
        BufferPolicy.androidDurations(app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS, live = false),
    )

    private val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(AndroidHost.context)
        .setLoadControl(loadControl)
        // 音频焦点交给 Media3：来电/其他应用出声自动暂停，焦点恢复自动续播。
        .setAudioAttributes(audioAttributes, !preview)
        .build()

    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(app.ailiaili.core.model.effectiveUserAgent())
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to "https://www.bilibili.com/",
                "Origin" to "https://www.bilibili.com",
            ),
        )
    private val progressive = ProgressiveMediaSource.Factory(http)
    private val localProgressive = ProgressiveMediaSource.Factory(FileDataSource.Factory())
    private var backups: List<String> = emptyList()
    private var audioUrl: String? = null
    private var videoUrl: String? = null
    private val listeners = linkedSetOf<PlaybackListener>()
    private var previewAudioFocus = false
    private var seeking = false
    private val bridge = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            listeners.toList().forEach { it.onError() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_BUFFERING) seeking = false
            val ended = playbackState == Player.STATE_ENDED
            listeners.toList().forEach { it.onEnded(ended) }
        }

        override fun onRenderedFirstFrame() {
            listeners.toList().forEach { it.onFirstFrame() }
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
    actual val videoAspectRatio: Float?
        get() = player.videoSize.let { size ->
            if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else null
        }
    actual val bufferedPositionMs: Long? get() = player.bufferedPosition.takeIf { it >= 0L }
    actual val isPlaying: Boolean get() = player.isPlaying
    actual val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    actual val isSeekPending: Boolean get() = seeking
    actual val playbackSpeed: Float get() = player.playbackParameters.speed
    actual var volume: Float
        get() = player.volume
        set(value) {
            if (preview && (value > 0f) != previewAudioFocus) {
                previewAudioFocus = value > 0f
                player.setAudioAttributes(audioAttributes, previewAudioFocus)
            }
            player.volume = value
        }

    private var live = false

    private var loadedTag = ""

    actual val mediaTag: String get() = loadedTag

    private var mediaTitle = ""
    private var mediaArtist = ""

    /** 通知/锁屏元数据：起播前由界面写入稿件标题与 UP 名。 */
    actual fun setMediaInfo(title: String, artist: String) {
        mediaTitle = title
        mediaArtist = artist
    }

    /** Android 的媒体键由 MediaSessionService 统一处理，无需额外登记。 */
    actual fun setMediaKeyNav(nav: ((Int) -> Unit)?) = Unit

    private fun startMediaSession() {
        if (!preview) AndroidMediaIntegration.onPlayStarted(AndroidHost.context, player, mediaTitle, mediaArtist)
    }

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
        val spec = BufferPolicy.androidDurations(networkCachingMs, live)
        if (loadControl.configure(spec)) player.stop()
    }

    actual fun play() {
        player.play()
    }

    actual fun pause() {
        player.pause()
    }

    actual fun seekTo(positionMs: Long) {
        seeking = true
        player.seekTo(positionMs)
        if (player.playbackState != Player.STATE_BUFFERING) seeking = false
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

    actual fun configureCompatibility(hardwareDecoding: Boolean, highQualityAudio: Boolean) = Unit

    actual fun freeze() {
        player.pause()
    }

    actual fun play(info: PlayInfo, positionMs: Long, audioOnly: Boolean) {
        backups = info.backupUrls
        val audio = info.audioUrl
        audioUrl = audio
        videoUrl = info.videoUrl
        loadedTag = info.videoUrl
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
        videoUrl = next
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
            .setUserAgent(app.ailiaili.core.model.effectiveUserAgent())
            .setDefaultRequestProperties(props)
            .setAllowCrossProtocolRedirects(true)
        player.setMediaSource(DefaultMediaSourceFactory(liveHttp).createMediaSource(item))
        player.prepare()
        player.playWhenReady = true
        startMediaSession()
    }

    actual fun playFiles(videoPath: String, audioPath: String?, positionMs: Long) {
        loadedTag = videoPath
        live = false
        applyBufferPolicy()
        val video = localProgressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(videoPath))))
        val source = if (audioPath.isNullOrBlank()) {
            video
        } else {
            MergingMediaSource(
                video,
                localProgressive.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(audioPath)))),
            )
        }
        player.setMediaSource(source, positionMs)
        player.prepare()
        player.playWhenReady = true
        startMediaSession()
    }

    actual fun release() {
        // Media3 强制要求 release 在创建 player 的线程（主线程）执行。
        // 调用方即使从 IO 线程进来，也切回主线程，避免 “Player is accessed on the wrong thread” 崩溃。
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runCatching { player.release() }
            if (!preview) AndroidMediaIntegration.onReleased(AndroidHost.context)
        } else {
            Handler(Looper.getMainLooper()).post {
                runCatching { player.release() }
                if (!preview) AndroidMediaIntegration.onReleased(AndroidHost.context)
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
        startMediaSession()
    }

    private fun attachSingle(url: String, positionMs: Long) {
        live = false
        applyBufferPolicy()
        player.setMediaSource(progressive.createMediaSource(MediaItem.fromUri(url)))
        player.prepare()
        if (positionMs > 0) player.seekTo(positionMs)
        player.playWhenReady = true
        startMediaSession()
    }
}

private class SwitchableLoadControl(initial: AndroidBufferSpec) : LoadControl {
    private val allocator = DefaultAllocator(true, androidx.media3.common.C.DEFAULT_BUFFER_SEGMENT_SIZE)
    @Volatile private var requested = initial
    private var applied = initial
    @Volatile private var delegate = makeDelegate(initial)

    fun configure(spec: AndroidBufferSpec): Boolean {
        if (spec == requested) return false
        requested = spec
        return true
    }

    override fun onPrepared(playerId: PlayerId) {
        val spec = requested
        if (spec != applied) {
            delegate.onReleased(playerId)
            delegate = makeDelegate(spec)
            applied = spec
        }
        delegate.onPrepared(playerId)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator() = allocator

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    override fun shouldContinuePreloading(
        timeline: androidx.media3.common.Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(timeline, mediaPeriodId, bufferedDurationUs)

    private fun makeDelegate(spec: AndroidBufferSpec): DefaultLoadControl =
        DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(spec.minBufferMs, spec.maxBufferMs, spec.playbackMs, spec.rebufferMs)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, scale: PlayerScale) {
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
            val resize = when (scale) {
                PlayerScale.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                PlayerScale.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                PlayerScale.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            if (view.resizeMode != resize) view.resizeMode = resize
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
