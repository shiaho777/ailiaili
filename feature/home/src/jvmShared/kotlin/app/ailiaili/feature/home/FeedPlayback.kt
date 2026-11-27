package app.ailiaili.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.player.PlaybackListener
import app.ailiaili.core.player.PlayerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FeedPlayback {
    val engine = PlayerEngine(preview = true)
    var ready by mutableStateOf(false)
        private set
    var hasFrame by mutableStateOf(false)
        private set
    var aspectRatio by mutableStateOf<Float?>(null)
        private set
    var ended by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    private var released = false

    private val listener = object : PlaybackListener {
        override fun onError() {
            if (!released) fail()
        }

        override fun onEnded(ended: Boolean) {
            if (!released) this@FeedPlayback.ended = ended
        }

        override fun onFirstFrame() {
            if (!released) {
                aspectRatio = engine.videoAspectRatio ?: aspectRatio
                hasFrame = true
            }
        }
    }

    fun attach() = engine.addListener(listener)

    suspend fun load(item: VideoItem) {
        val repository = AppGraph.repo
        val settings = repository.settings.value
        engine.configureCompatibility(settings.desktopHardwareDecoding, settings.desktopHighQualityAudio)
        engine.networkCachingMs = settings.networkCachingMs
        try {
            withContext(ioDispatcher) { engine.prepare() }
            if (item.isLive) {
                val stream = repository.livePlay(item.roomId).getOrThrow()
                check(stream.url.isNotBlank())
                if (failed) return
                engine.playLive(stream.url)
            } else {
                val cid = item.cid.takeIf { it > 0 } ?: repository.video(item.bvid).getOrThrow().cid
                check(cid > 0)
                val stream = repository.playAt(item.bvid, cid, qn = 64, forceDash = false).getOrThrow()
                check(stream.videoUrl.isNotBlank())
                if (failed) return
                if (stream.width > 0 && stream.height > 0) aspectRatio = stream.width.toFloat() / stream.height
                engine.play(stream)
            }
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            fail()
        }
    }

    fun toggle() {
        if (failed) return
        if (ended) {
            ended = false
            paused = false
            engine.seekTo(0)
            engine.play()
        } else {
            paused = !paused
        }
    }

    fun updatePlaying(allowed: Boolean) {
        if (!ready) return
        if (allowed && !paused && !ended && !failed) engine.play() else engine.pause()
    }

    fun fail() {
        failed = true
        engine.pause()
    }

    fun dispose() {
        released = true
        engine.removeListener(listener)
        engine.freeze()
        if (isDesktopHost()) CoroutineScope(ioDispatcher).launch { engine.release() }
        else engine.release()
    }
}

@Composable
internal fun rememberFeedPlayback(
    item: VideoItem,
    allowed: Boolean,
    volume: Float,
    retry: Int,
): FeedPlayback {
    val playback = remember(item.stableKey, retry) { FeedPlayback() }
    val latestAllowed by rememberUpdatedState(allowed)
    var activated by remember(playback) { mutableStateOf(allowed) }
    DisposableEffect(playback) {
        playback.attach()
        onDispose { playback.dispose() }
    }
    LaunchedEffect(playback, volume) { playback.engine.volume = volume }
    LaunchedEffect(playback, allowed) { if (allowed) activated = true }
    LaunchedEffect(playback, activated) {
        if (!activated) return@LaunchedEffect
        playback.load(item)
        playback.updatePlaying(latestAllowed)
    }
    LaunchedEffect(playback, allowed, playback.paused) {
        playback.updatePlaying(allowed)
    }
    val waiting = allowed && !playback.paused && !playback.hasFrame && !playback.failed
    LaunchedEffect(playback, waiting) {
        if (!waiting) return@LaunchedEffect
        delay(25_000)
        playback.fail()
    }
    return playback
}
