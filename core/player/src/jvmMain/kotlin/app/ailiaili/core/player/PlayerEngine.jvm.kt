package app.ailiaili.core.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ailiaili.core.model.PlayInfo
import app.ailiaili.core.model.PlayerScale
import app.ailiaili.core.model.USER_AGENT
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.EventQueue
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

actual class PlayerEngine actual constructor(private val preview: Boolean) {
    private val listeners = linkedSetOf<PlaybackListener>()
    private var backups: List<String> = emptyList()
    private var audioUrl: String? = null
    private var videoUrl: String? = null
    @Volatile private var audioOnly = false
    @Volatile private var live = false
    private var speed = 1f
    private var volumeLevel = 1f

    /** 已自然播完：此时对 input 发 seek 无效（只改 Time 不重建画面），必须重建 Media。 */
    @Volatile private var finished = false

    private enum class SeekStage { Initial, Retried, Rebuilt, Failed }

    private data class SeekAttempt(val target: Long, val stage: SeekStage, val confirmedPosition: Long)

    private val pendingSeek = AtomicReference<SeekAttempt?>()
    private val seekWatchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "vlc-seek-timeout").apply { isDaemon = true }
    }

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    private val frameProbe = AtomicInteger()
    @Volatile private var pausedBefore = false
    @Volatile private var softwareRecovery = false
    @Volatile private var hardwareDecoding = true
    @Volatile private var highQualityAudio = false

    actual fun configureCompatibility(hardwareDecoding: Boolean, highQualityAudio: Boolean) {
        this.hardwareDecoding = hardwareDecoding
        this.highQualityAudio = highQualityAudio
    }

    @Volatile var statusMessage: String? = null
    private val pump = FramePump()
    private val firstFrameDelivered = AtomicBoolean(false)
    @Volatile private var decodedAspectRatio: Float? = null
    private val clock = PlaybackClock()
    val frameGeneration: Int get() = pump.frameGeneration

    private val gate = Any()
    @Volatile private var dead = false
    private var factory: MediaPlayerFactory? = null
    private var media: EmbeddedMediaPlayer? = null

    // vlcj/JNA 不会替我们强引用这些回调；只把对象传给 native 的话，
    // 播放一段时间后 JVM GC 掉回调，native 再调就会 SIGTRAP。
    // 必须持有到 release() 为止。
    private var bufferFormatCallback: BufferFormatCallback? = null
    private var renderCallback: RenderCallback? = null
    private var videoSurface: VideoSurface? = null
    private var eventAdapter: MediaPlayerEventAdapter? = null

    actual val currentPosition: Long get() = clock.positionMs
    actual val duration: Long get() = clock.durationMs
    actual val videoAspectRatio: Float? get() = decodedAspectRatio
    actual val bufferedPositionMs: Long?
        get() = clock.durationMs.takeIf { videoUrl?.startsWith("file:") == true && it > 0L }
    actual val isPlaying: Boolean get() = clock.playing
    actual val isSeekPending: Boolean get() = pendingSeek.get() != null
    actual val isBuffering: Boolean get() = clock.buffering || pendingSeek.get()?.let { it.stage != SeekStage.Failed } == true
    actual val playbackSpeed: Float get() = clock.speed
    actual var volume: Float
        get() = volumeLevel
        set(value) {
            volumeLevel = value.coerceIn(0f, 1f)
            media?.audio()?.setVolume((volumeLevel * 100).toInt())
        }

    @Volatile
    private var networkCaching = app.ailiaili.core.model.NETWORK_CACHING_DEFAULT_MS

    actual var networkCachingMs: Int
        get() = networkCaching
        set(value) {
            networkCaching = value.coerceIn(
                app.ailiaili.core.model.NETWORK_CACHING_MIN_MS,
                app.ailiaili.core.model.NETWORK_CACHING_MAX_MS,
            )
        }

    actual fun setMediaInfo(title: String, artist: String) {
        if (preview) return
        // Windows 全局媒体键：起播时把控制权登记给当前引擎。
        // macOS/Linux 无系统媒体会话（MPRIS/Now Playing 待立项），start 内部直接跳过。
        DesktopMediaKeys.start()
        DesktopMediaKeys.handler = DesktopMediaKeys.Handler { action ->
            when (action) {
                0 -> if (isPlaying) pause() else play()
                3 -> pause()
                // 上一曲/下一曲由播放器层消费（队列/分P），引擎层只做暂停兜底。
                else -> mediaKeyNav?.invoke(action)
            }
        }
    }

    /** 播放器登记的媒体键导航回调（1=下一条 2=上一条）；离开播放页置 null。 */
    @Volatile
    private var mediaKeyNav: ((Int) -> Unit)? = null

    actual fun setMediaKeyNav(nav: ((Int) -> Unit)?) {
        if (preview) return
        mediaKeyNav = nav
        if (nav == null && DesktopMediaKeys.handler != null) {
            // 离开播放页：不再响应媒体键，避免误触已冻结的引擎。
            DesktopMediaKeys.handler = null
        }
    }

    actual fun play() {
        media?.controls()?.play()
    }

    actual fun pause() {
        media?.controls()?.setPause(true)
    }

    actual fun seekTo(positionMs: Long) {
        synchronized(gate) {
            if (dead) return
            val target = positionMs.coerceAtLeast(0L)
            val confirmed = pendingSeek.get()?.confirmedPosition ?: clock.positionMs
            frameProbe.incrementAndGet()
            clock.acceptSeek(target)
            if (finished && !live && !(if (audioOnly) audioUrl else videoUrl).isNullOrBlank()) {
                runCatching { rebuildAt(target, SeekStage.Initial) }
            } else {
                val attempt = SeekAttempt(target, SeekStage.Initial, confirmed)
                pendingSeek.set(attempt)
                runCatching { media?.controls()?.setTime(target) }
                scheduleSeekCheck(attempt, 2_500)
            }
        }
        notify { it.onPositionJump() }
    }

    private fun scheduleSeekCheck(attempt: SeekAttempt, delayMs: Long) {
        runCatching {
            seekWatchdog.schedule({ recoverSeek(attempt, endedEarly = false) }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun recoverSeek(attempt: SeekAttempt, endedEarly: Boolean) {
        synchronized(gate) {
            if (dead || pendingSeek.get() !== attempt || attempt.stage == SeekStage.Failed) return
            if (!endedEarly) {
                val actual = runCatching { media?.status()?.time() }.getOrNull()
                if (actual != null && isSeekSettled(actual, attempt.target)) {
                    if (pendingSeek.compareAndSet(attempt, null)) clock.acceptTime(actual)
                    return
                }
            }
            if (attempt.stage == SeekStage.Initial && !endedEarly) {
                val retry = attempt.copy(stage = SeekStage.Retried)
                if (!pendingSeek.compareAndSet(attempt, retry)) return
                runCatching { media?.controls()?.setTime(attempt.target) }
                scheduleSeekCheck(retry, 2_000)
                return
            }
            val url = if (audioOnly) audioUrl else videoUrl
            if (attempt.stage != SeekStage.Rebuilt && !live && url?.startsWith("http") == true) {
                if (pendingSeek.get() !== attempt) return
                runCatching { rebuildAt(attempt.target, SeekStage.Rebuilt) }
                    .onFailure { notify { listener -> listener.onError() } }
                return
            }
            val requiresRecovery = endedEarly || !live && url?.startsWith("http") == true
            val failed = if (requiresRecovery) attempt.copy(stage = SeekStage.Failed) else null
            if (pendingSeek.compareAndSet(attempt, failed)) {
                clock.acceptSeek(if (requiresRecovery) attempt.target else attempt.confirmedPosition)
                if (requiresRecovery) {
                    clock.acceptPlaying(false)
                    notify { it.onError() }
                }
            }
        }
    }

    actual fun setPlaybackSpeed(speed: Float) {
        this.speed = speed
        clock.acceptSpeed(speed)
        media?.controls()?.setRate(speed)
    }

    actual fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    actual fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    actual fun prepare() {
        synchronized(gate) {
            if (dead || media != null) return
            val opened = runCatching { openPlayer() }
            factory = opened.getOrNull()?.first
            media = opened.getOrNull()?.second
            if (media == null) {
                statusMessage = VlcLocator.friendlyError(opened.exceptionOrNull()?.message)
            }
        }
    }

    actual fun freeze() {
        clock.acceptPlaying(false)
        pump.halt()
        runCatching { pause() }
    }

    actual fun play(info: PlayInfo, positionMs: Long, audioOnly: Boolean) {
        live = false
        backups = info.backupUrls
        audioUrl = info.audioUrl
        videoUrl = info.videoUrl
        loadedTag = info.videoUrl
        this.audioOnly = audioOnly && !info.audioUrl.isNullOrBlank()
        val url = if (this.audioOnly) info.audioUrl.orEmpty() else info.videoUrl
        start(url, if (this.audioOnly) null else info.audioUrl, positionMs, expectedDuration = info.durationMs)
    }

    /**
     * 听视频/还原视频的重建走后台串行：media().play() 内部会停上一路媒体，
     * 等解码线程退出可能阻塞约 1 秒，放 UI 线程就是窗口定格（BiliDesk v1.7.6
     * 同款修复）。单线程执行器保证连点按序生效，不会交叠出半状态。
     */
    private val switchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vlc-media-switch").apply { isDaemon = true }
    }

    actual fun playAudioOnly(positionMs: Long): Boolean {
        val audio = audioUrl?.takeIf { it.isNotBlank() } ?: return false
        live = false
        audioOnly = true
        val expectedTag = loadedTag
        val knownLength = clock.durationMs
        return runCatching {
            switchExecutor.execute {
                synchronized(gate) {
                    if (dead || loadedTag != expectedTag || !audioOnly) return@execute
                    runCatching { start(audio, null, positionMs, expectedDuration = knownLength) }
                        .onFailure { notify { listener -> listener.onError() } }
                }
            }
        }.isSuccess
    }

    actual fun restoreVideo(positionMs: Long) {
        val video = videoUrl?.takeIf { it.isNotBlank() } ?: return
        live = false
        audioOnly = false
        val expectedTag = loadedTag
        val knownLength = clock.durationMs
        runCatching {
            switchExecutor.execute {
                synchronized(gate) {
                    if (dead || loadedTag != expectedTag || audioOnly) return@execute
                    runCatching { start(video, audioUrl, positionMs, expectedDuration = knownLength) }
                        .onFailure { notify { listener -> listener.onError() } }
                }
            }
        }
    }

    actual fun failOver(positionMs: Long): Boolean {
        val next = backups.firstOrNull() ?: return false
        live = false
        backups = backups.drop(1)
        videoUrl = next
        start(next, audioUrl, positionMs, expectedDuration = clock.durationMs)
        return true
    }

    actual fun playLive(url: String) {
        live = true
        audioOnly = false
        audioUrl = null
        videoUrl = url
        loadedTag = url
        start(url, null, 0)
    }

    actual fun playFiles(videoPath: String, audioPath: String?, positionMs: Long) {
        live = false
        audioOnly = false
        backups = emptyList()
        videoUrl = localMediaUri(videoPath)
        loadedTag = videoUrl.orEmpty()
        audioUrl = audioPath?.let(::localMediaUri)
        start(videoUrl.orEmpty(), audioUrl, positionMs)
    }

    actual fun release() {
        synchronized(gate) {
            dead = true
            runCatching { media?.release() }
            runCatching { factory?.release() }
            media = null
            factory = null
            bufferFormatCallback = null
            renderCallback = null
            videoSurface = null
            eventAdapter = null
            runCatching { seekWatchdog.shutdownNow() }
            runCatching { switchExecutor.shutdownNow() }
            pump.release()
        }
    }

    fun peekImage() = pump.peek()

    actual suspend fun snapshot(displayName: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                // 独立副本：编码期间播放侧换分辨率/释放不会关掉它；
                // 也不把截图误标成「正在显示」干扰帧槽位选择。
                val image = pump.snapshotCopy() ?: return@runCatching null
                val data = image.use { it.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG) }
                    ?: return@runCatching null
                val safe = displayName.ifBlank { "snapshot" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
                val dir = File(System.getProperty("user.home"), "Pictures/ailiaili").also { it.mkdirs() }
                val file = File(dir, "$safe-${System.currentTimeMillis() / 1000}.png")
                file.writeBytes(data.bytes)
                "截图已保存到 ${file.absolutePath}"
            }.getOrNull()
        }

    @Volatile private var loadedTag = ""

    actual val mediaTag: String get() = loadedTag

    private fun start(
        url: String,
        slave: String?,
        positionMs: Long,
        seekStage: SeekStage = SeekStage.Initial,
        expectedDuration: Long = 0,
    ) = synchronized(gate) {
        val player = media ?: return
        if (url.isBlank()) return
        frameProbe.incrementAndGet()
        firstFrameDelivered.set(false)
        decodedAspectRatio = null
        pausedBefore = false
        val confirmed = pendingSeek.get()?.confirmedPosition ?: clock.positionMs
        val attempt = positionMs.takeIf { it > 0 && !live }?.let { SeekAttempt(it, seekStage, confirmed) }
        pendingSeek.set(attempt)
        finished = false
        statusMessage = null
        clock.resetLength(expectedDuration)
        val options = buildList {
            add(if (live) ":http-referrer=https://live.bilibili.com/" else ":http-referrer=https://www.bilibili.com/")
            add(":http-user-agent=${app.ailiaili.core.model.effectiveUserAgent()}")
            add(":network-caching=$networkCaching")
            if (!hardwareDecoding || softwareRecovery) add(":avcodec-hw=none")
            // libvlc 自带的 mp4 解复用器读不出 B 站 fMP4 的最后一个分片，
            // 长视频片尾最后 1~2 秒会没有声音；avformat 能完整读到 sidx 末段。
            add(":demux=avformat")
            if (!slave.isNullOrBlank()) add(":input-slave=$slave")
            if (positionMs > 0) add(":start-time=${positionMs / 1000.0}")
        }
        clock.acceptSeek(positionMs.coerceAtLeast(0L))
        clock.acceptSpeed(speed)
        check(player.media().play(url, *options.toTypedArray())) { "VLC 无法打开这条媒体" }
        player.audio().setVolume((volumeLevel * 100).toInt())
        player.controls().setRate(speed)
        if (attempt != null) scheduleSeekCheck(attempt, if (seekStage == SeekStage.Rebuilt) 6_000 else 4_000)
        Unit
    }

    private fun notify(block: (PlaybackListener) -> Unit) {
        val copy = listeners.toList()
        EventQueue.invokeLater { copy.forEach(block) }
    }

    private fun openPlayer(): Pair<MediaPlayerFactory, EmbeddedMediaPlayer> {
        val created = openFactory(highQualityAudio && windows)
        val player = created.mediaPlayers().newEmbeddedMediaPlayer()
        val formatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                if (sourceWidth > 0 && sourceHeight > 0) decodedAspectRatio = sourceWidth.toFloat() / sourceHeight
                val (width, height) = pump.sizeFor(sourceWidth, sourceHeight)
                return RV32BufferFormat(width, height)
            }

            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
        }
        val render = object : RenderCallback {
            override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                val source = nativeBuffers.firstOrNull() ?: return
                val width = bufferFormat.width
                val height = bufferFormat.height
                if (width <= 0 || height <= 0) return
                val pitch = bufferFormat.pitches.firstOrNull()?.takeIf { it >= width * 4 } ?: (width * 4)
                pump.publish(source, width, height, pitch)
                if (preview && !firstFrameDelivered.get() && firstFrameDelivered.compareAndSet(false, true)) {
                    notify { it.onFirstFrame() }
                }
            }
        }
        val surface = created.videoSurfaces().newVideoSurface(formatCallback, render, true)
        bufferFormatCallback = formatCallback
        renderCallback = render
        videoSurface = surface
        player.videoSurface().set(surface)
        val events = object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) {
                finished = false
                clock.acceptPlaying(false)
                pendingSeek.get()?.let { attempt ->
                    pendingSeek.compareAndSet(attempt, attempt.copy(stage = SeekStage.Failed))
                    clock.acceptSeek(attempt.target)
                }
                notify { it.onError() }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                val attempt = pendingSeek.get()
                if (attempt != null) {
                    runCatching { seekWatchdog.execute { recoverSeek(attempt, endedEarly = true) } }
                    return
                }
                clock.acceptPlaying(false)
                if (!live && videoUrl?.startsWith("http") == true && !isCrediblePlaybackEnd(clock.positionMs, clock.durationMs)) {
                    notify { it.onError() }
                    return
                }
                finished = true
                notify { it.onEnded(true) }
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                finished = false
                clock.acceptBuffering(false)
                clock.acceptPlaying(true)
                acceptTime(mediaPlayer.status().time())
                clock.acceptLength(mediaPlayer.status().length())
                if (pausedBefore) {
                    pausedBefore = false
                    watchForFrozenFrames()
                }
                notify {
                    it.onEnded(false)
                    it.onIsPlayingChanged(true)
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                clock.acceptBuffering(newCache < 100f)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                frameProbe.incrementAndGet()
                pausedBefore = true
                clock.acceptPlaying(false)
                notify { it.onIsPlayingChanged(false) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                clock.acceptPlaying(false)
                notify { it.onIsPlayingChanged(false) }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                acceptTime(newTime)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                clock.acceptLength(newLength)
            }

            override fun forward(mediaPlayer: MediaPlayer) {
                notify { it.onPositionJump() }
            }

            override fun backward(mediaPlayer: MediaPlayer) {
                notify { it.onPositionJump() }
            }
        }
        eventAdapter = events
        player.events().addMediaPlayerEventListener(events)
        return created to player
    }

    private fun watchForFrozenFrames() {
        val frame = pump.frameGeneration
        if (!windows || !hardwareDecoding || softwareRecovery || audioOnly || live || frame == 0 || isSeekPending) return
        val ticket = frameProbe.incrementAndGet()
        val resumedAt = clock.positionMs
        runCatching {
            seekWatchdog.schedule({
                synchronized(gate) {
                    if (!dead && frameProbe.get() == ticket && clock.playing && !isBuffering &&
                        pump.frameGeneration == frame && clock.positionMs - resumedAt >= 500
                    ) {
                        softwareRecovery = true
                        runCatching { rebuildAt(clock.positionMs, SeekStage.Initial) }
                            .onFailure { notify { listener -> listener.onError() } }
                    }
                }
            }, 4_500, TimeUnit.MILLISECONDS)
        }
    }

    private fun acceptTime(newTime: Long) {
        val attempt = pendingSeek.get()
        if (attempt != null) {
            if (!isSeekSettled(newTime, attempt.target)) return
            if (!pendingSeek.compareAndSet(attempt, null)) return
        }
        clock.acceptTime(newTime)
    }

    private fun rebuildAt(positionMs: Long, stage: SeekStage) {
        if (audioOnly) {
            start(audioUrl.orEmpty(), null, positionMs, stage, clock.durationMs)
        } else {
            start(videoUrl.orEmpty(), audioUrl, positionMs, stage, clock.durationMs)
        }
    }

}

internal fun localMediaUri(path: String): String = Path.of(path).toUri().toASCIIString()

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier, scale: PlayerScale) {
    Box(
        modifier
            .clipToBounds()
            .background(Color.Black)
            .then(VideoFrameElement(engine, scale)),
        contentAlignment = Alignment.Center,
    ) {
        engine.statusMessage?.let {
            Text(
                it,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp).fillMaxSize(),
            )
        }
    }
}

private class VideoFrameElement(
    private val engine: PlayerEngine,
    private val scale: PlayerScale,
) : ModifierNodeElement<VideoFrameNode>() {
    override fun create() = VideoFrameNode(engine, scale)

    override fun update(node: VideoFrameNode) {
        node.rebind(engine)
        node.scale = scale
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean =
        other is VideoFrameElement && other.engine === engine && other.scale == scale

    override fun hashCode(): Int = 31 * engine.hashCode() + scale.hashCode()
}

private class VideoFrameNode(
    var engine: PlayerEngine,
    var scale: PlayerScale,
) : Modifier.Node(), DrawModifierNode {
    private var clock: Job? = null
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

    override fun onAttach() {
        clock = coroutineScope.launch {
            var seen = -1
            while (isActive) {
                withFrameNanos {
                    val gen = engine.frameGeneration
                    if (gen != seen) {
                        seen = gen
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun onDetach() {
        clock?.cancel()
        clock = null
    }

    fun rebind(next: PlayerEngine) {
        if (engine === next) return
        engine = next
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val image = engine.peekImage() ?: return
        val srcW = image.width.toFloat()
        val srcH = image.height.toFloat()
        if (srcW < 1f || srcH < 1f) return
        val horizontal = size.width / srcW
        val vertical = size.height / srcH
        val factor = if (scale == PlayerScale.Crop) maxOf(horizontal, vertical) else minOf(horizontal, vertical)
        val dw = if (scale == PlayerScale.Stretch) size.width else srcW * factor
        val dh = if (scale == PlayerScale.Stretch) size.height else srcH * factor
        val left = (size.width - dw) / 2f
        val top = (size.height - dh) / 2f
        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                image,
                Rect.makeXYWH(0f, 0f, srcW, srcH),
                Rect.makeXYWH(left, top, dw, dh),
                sampling,
                null,
                true,
            )
        }
    }
}

private fun openFactory(highQualityAudio: Boolean): MediaPlayerFactory {
    prepareNativeVlc()
    val discovery = NativeDiscovery()
    val mac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
    val args = buildList {
        add(if (mac) "--avcodec-hw=videotoolbox" else "--avcodec-hw=any")
        add("--no-osd")
        add("--drop-late-frames")
        add("--skip-frames")
        add("--no-video-title-show")
        if (highQualityAudio) add("--audio-resampler=speex")
    }
    return runCatching { MediaPlayerFactory(discovery, *args.toTypedArray()) }
        .getOrElse { MediaPlayerFactory(NativeDiscovery()) }
}

private fun prepareNativeVlc() {
    val dir = VlcLocator.locate() ?: return
    NativeLibrary.addSearchPath("vlc", dir)
    NativeLibrary.addSearchPath("vlccore", dir)
    val current = System.getProperty("jna.library.path")
    System.setProperty(
        "jna.library.path",
        if (current.isNullOrBlank()) dir else "$dir${File.pathSeparator}$current",
    )
    val plugins = File(dir, "../plugins")
    if (plugins.isDirectory) {
        System.setProperty("VLC_PLUGIN_PATH", plugins.canonicalPath)
    }
}


