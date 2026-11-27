package app.ailiaili.feature.player

import app.ailiaili.core.designsystem.AliColors
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStageSeekBar
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.aliPlaybackAccent
import app.ailiaili.core.designsystem.decodeImageBytes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import app.ailiaili.core.designsystem.AliStageMenu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.ShotFrame
import app.ailiaili.core.model.SponsorSegment
import app.ailiaili.core.model.USER_AGENT
import app.ailiaili.core.model.VideoShot
import app.ailiaili.core.model.ViewChapter
import app.ailiaili.core.model.formatClock
import app.ailiaili.core.player.PlayerEngine
import app.ailiaili.core.model.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import app.ailiaili.core.network.get
import kotlin.math.roundToInt

@Composable
internal fun PlayerChromeEdge(
    visible: Boolean,
    top: Boolean,
    shadeHeight: Dp,
    reducedMotion: Boolean,
    hideImmediately: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val direction = if (top) -1 else 1
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) EnterTransition.None else
            fadeIn(tween(150)) + slideInVertically(tween(180)) { direction * it / 10 },
        exit = if (reducedMotion || hideImmediately) ExitTransition.None else
            fadeOut(tween(160)) + slideOutVertically(tween(160)) { direction * it / 10 },
    ) {
        Box(contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter) {
            Spacer(Modifier.fillMaxWidth().height(shadeHeight).drawWithCache {
                val edge = AliColors.StageMedia.copy(alpha = .66f)
                val inside = AliColors.StageMedia.copy(alpha = .53f)
                val brush = if (top) Brush.verticalGradient(0f to edge, .42f to inside, 1f to Color.Transparent)
                else Brush.verticalGradient(0f to Color.Transparent, .58f to inside, 1f to edge)
                onDrawBehind { drawRect(brush) }
            })
            content()
        }
    }
}

internal val PlayerCaptionShadow = Shadow(Color.Black.copy(alpha = .85f), Offset(0f, 1f), 3f)

@Composable
internal fun PlayerTopControls(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AliStageIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, overVideo = true)
            if (title != null) {
                Text(
                    title,
                    modifier = Modifier.widthIn(max = 200.dp).padding(end = 12.dp),
                    style = MaterialTheme.typography.labelLarge.copy(shadow = PlayerCaptionShadow),
                    color = AliColors.StageInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    content = content,
                )
                trailing()
            }
        }
    }
}

@Composable
internal fun PlayerControlLabel(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (active) aliPlaybackAccent() else AliColors.StageInk,
            disabledContentColor = AliColors.StageMuted,
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(shadow = PlayerCaptionShadow),
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * 进度时钟只重组这一行。播放页其余部分不跟着 currentPosition 刷新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTransport(
    engine: PlayerEngine,
    live: Boolean,
    playing: Boolean,
    onScrubbing: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onVolume: () -> Unit,
    volumeOpen: Boolean,
    muted: Boolean,
    marks: List<SponsorSegment> = emptyList(),
    chapters: List<ViewChapter> = emptyList(),
    chaptersOpen: Boolean = false,
    onChaptersOpen: (Boolean) -> Unit = {},
    shot: VideoShot? = null,
    fullscreen: Boolean,
    inlineTimeline: Boolean,
    playbackOptions: @Composable () -> Unit,
    onToggleFullscreen: () -> Unit,
    /** A-B 循环区间（毫秒）；null 不画。 */
    abRange: Pair<Long, Long>? = null,
    modifier: Modifier = Modifier,
) {
    var position by remember(engine) { mutableLongStateOf(0L) }
    var duration by remember(engine) { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(0f) }
    var hover by remember { mutableFloatStateOf(-1f) }
    val draggingState = remember { mutableStateOf(false) }
    var dragging by draggingState
    val playbackActive by rememberUpdatedState(playing)
    var buffered by remember(engine) { mutableLongStateOf(-1L) }
    val reportScrubbing by rememberUpdatedState(onScrubbing)
    DisposableEffect(inlineTimeline) {
        hover = -1f
        onDispose {
            if (draggingState.value) {
                draggingState.value = false
                reportScrubbing(false)
            }
        }
    }
    LaunchedEffect(engine) {
        while (isActive) {
            val pos = runCatching { engine.currentPosition }.getOrNull()
            val dur = runCatching { engine.duration }.getOrNull()
            if (pos != null && !draggingState.value) position = pos.coerceAtLeast(0L)
            if (dur != null) duration = dur.coerceAtLeast(0L)
            buffered = runCatching { engine.bufferedPositionMs }.getOrNull() ?: -1L
            delay(if (playbackActive || draggingState.value) 200 else 500)
        }
    }

    val playhead = if (dragging && duration > 0) (drag * duration).toLong() else position
    val timeline: @Composable (Modifier) -> Unit = { lane ->
        Column(lane) {
            if (duration > 0 && (chapters.isNotEmpty() || marks.isNotEmpty() || abRange != null)) {
                TimelineMarks(chapters, marks, abRange, duration)
            }
            AliStageSeekBar(
                value = if (duration > 0) (playhead.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = {
                    if (!dragging) reportScrubbing(true)
                    hover = -1f
                    dragging = true
                    drag = it
                },
                onValueChangeFinished = {
                    if (duration > 0 && dragging) {
                        val target = (drag * duration).toLong().coerceIn(0L, duration)
                        position = target
                        onSeek(target)
                    }
                    dragging = false
                    reportScrubbing(false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .semantics {
                        contentDescription = "播放进度"
                        stateDescription = "${formatClock(playhead)} / ${formatClock(duration)}"
                    }
                    .pointerInput(duration) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Move -> if (duration > 0) {
                                        val pointer = event.changes.firstOrNull()
                                        if (pointer?.type == PointerType.Mouse && !pointer.pressed) {
                                            val inset = TimelineInset.toPx()
                                            hover = ((pointer.position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                        }
                                    }
                                    PointerEventType.Exit -> hover = -1f
                                }
                            }
                        }
                    },
                enabled = duration > 0,
                buffered = if (duration > 0 && buffered >= 0) (buffered.toDouble() / duration).toFloat() else null,
            )
        }
    }
    Column(
        modifier.widthIn(max = 960.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val preview = if (dragging) drag else hover
        if (!live && preview >= 0f && duration > 0) {
            val seconds = (preview * duration / 1000f).toInt()
            val chapter = chapters.lastOrNull { seconds >= it.fromSec && (it.toSec <= it.fromSec || seconds < it.toSec) }
            SeekPreview(shot?.frameAt(seconds), formatClock(seconds * 1000L), chapter?.title)
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            if (!live && !inlineTimeline) timeline(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AliStageIconButton(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "暂停" else "播放",
                    onTogglePlay,
                    overVideo = true,
                )
                Text(
                    if (live) "直播" else "${formatClock(playhead)} / ${formatClock(duration)}",
                    modifier = (if (inlineTimeline) Modifier else Modifier.weight(1f)).padding(horizontal = 4.dp),
                    color = AliColors.StageInk,
                    style = MaterialTheme.typography.labelMedium.copy(shadow = PlayerCaptionShadow, fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inlineTimeline) timeline(Modifier.weight(1f))
                AliStageIconButton(
                    if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    "音量",
                    onVolume,
                    selected = volumeOpen,
                    overVideo = true,
                )
                if (!live && duration > 0 && chapters.isNotEmpty()) {
                    PlayerChapterMenu(chapters, playhead, chaptersOpen, onChaptersOpen, onSeek)
                }
                playbackOptions()
                AliStageIconButton(
                    if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    if (fullscreen) "退出全屏" else "全屏",
                    onToggleFullscreen,
                    overVideo = true,
                )
            }
        }
    }
}

@Composable
private fun PlayerChapterMenu(
    chapters: List<ViewChapter>,
    position: Long,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val seconds = position / 1000
    val current = chapters.lastOrNull { seconds >= it.fromSec && (it.toSec <= it.fromSec || seconds < it.toSec) }
    Box {
        AliStageIconButton(Icons.AutoMirrored.Filled.ViewList, "章节", { onExpanded(true) }, selected = expanded, overVideo = true)
        AliStageMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            chapters.forEach { chapter ->
                DropdownMenuItem(
                    text = {
                        Text(
                            chapter.title,
                            color = if (chapter == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = { Text(formatClock(chapter.fromSec * 1000L)) },
                    onClick = {
                        onExpanded(false)
                        onSeek(chapter.fromSec * 1000L)
                    },
                )
            }
        }
    }
}

private val TimelineInset = 10.dp
internal val StackedTransportClearance = 100.dp
internal val InlineTransportClearance = 68.dp

@Composable
private fun TimelineMarks(
    chapters: List<ViewChapter>,
    marks: List<SponsorSegment>,
    abRange: Pair<Long, Long>?,
    duration: Long,
) {
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = TimelineInset)
            .height(7.dp)
            .drawWithCache {
                fun x(ms: Double): Float = ((ms / duration).coerceIn(0.0, 1.0) * size.width).toFloat()
                val sponsorBands = marks.map { Triple(x(it.startSec * 1000), x(it.endSec * 1000), sponsorMarkColor(it.category)) }
                val chapterPositions = chapters.map { x(it.fromSec * 1000.0).coerceAtMost((size.width - 1.dp.toPx()).coerceAtLeast(0f)) }
                val loop = abRange?.let { x(it.first.toDouble()) to x(it.second.toDouble()) }
                val laneHeight = 2.dp.toPx()
                val corner = CornerRadius(laneHeight / 2f)
                onDrawBehind {
                    sponsorBands.forEach { (start, end, color) ->
                        if (end > start) {
                            drawRoundRect(color, Offset(start, 0f), Size(end - start, laneHeight), corner)
                        }
                    }
                    loop?.let { (start, end) ->
                        if (end > start) {
                            drawRoundRect(Color(0xFFF4CA8D), Offset(start, size.height - laneHeight), Size(end - start, laneHeight), corner)
                        }
                    }
                    chapterPositions.forEach { position ->
                        drawRect(AliColors.StageInk, Offset(position, 0f), Size(1.dp.toPx(), size.height))
                    }
                }
            },
    )
}

@Composable
private fun SeekPreview(frame: ShotFrame?, clock: String, chapter: String?) {
    Column(
        Modifier
            .padding(bottom = 4.dp)
            .aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.small)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (frame != null) ShotStill(frame)
        Text(clock, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
        if (!chapter.isNullOrBlank()) {
            Text(
                chapter,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ShotStill(frame: ShotFrame) {
    val cache = remember { HashMap<String, ImageBitmap>() }
    var image by remember(frame.url) { mutableStateOf(cache[frame.url]) }
    LaunchedEffect(frame.url) {
        val cached = cache[frame.url]
        if (cached != null) {
            image = cached
            return@LaunchedEffect
        }
        val loaded = withContext(ioDispatcher) { loadShot(frame.url) } ?: return@LaunchedEffect
        cache[frame.url] = loaded
        image = loaded
    }
    val src = image ?: return
    if (src.width <= 0 || src.height <= 0) return
    val columns = frame.columns.coerceAtLeast(1)
    val rows = frame.rows.coerceAtLeast(1)
    val cellW = if (frame.cellWidth > 0) frame.cellWidth else src.width / columns
    val cellH = if (frame.cellHeight > 0) frame.cellHeight else src.height / rows
    val left = (frame.column * cellW).coerceIn(0, src.width - 1)
    val top = (frame.row * cellH).coerceIn(0, src.height - 1)
    val cropW = cellW.coerceAtMost(src.width - left).coerceAtLeast(1)
    val cropH = cellH.coerceAtMost(src.height - top).coerceAtLeast(1)
    Canvas(
        Modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(RoundedCornerShape(6.dp)),
    ) {
        drawImage(
            src,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(cropW, cropH),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}

private fun loadShot(url: String): ImageBitmap? = runCatching {
    val resp = AppGraph.http.get(
        url,
        mapOf(
            "Referer" to "https://www.bilibili.com/",
            "User-Agent" to USER_AGENT,
        ),
    )
    if (!resp.isSuccessful) return null
    decodeImageBytes(resp.body)
}.getOrNull()

private fun sponsorMarkColor(category: String): Color = when (category) {
    "sponsor" -> Color(0xFFCBC0E9)
    "selfpromo" -> Color(0xFFFFB74D)
    "intro" -> Color(0xFF4FC3F7)
    "outro" -> Color(0xFF9575CD)
    "preview" -> Color(0xFF4DB6AC)
    "poi_highlight" -> Color(0xFFFFD54F)
    else -> Color(0xFFBDBDBD)
}
