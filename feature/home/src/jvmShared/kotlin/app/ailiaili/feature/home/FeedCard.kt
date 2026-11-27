package app.ailiaili.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import app.ailiaili.core.designsystem.shellBottomClearance
import app.ailiaili.core.designsystem.aliAmbient
import app.ailiaili.core.designsystem.biliPressScale
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.aliPlaybackAccent
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.AliStageMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.AliColors
import app.ailiaili.core.designsystem.AliStageSeekBar
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.QuickActions
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.PlayerScale
import app.ailiaili.core.model.cdnThumb
import app.ailiaili.core.model.formatClock
import app.ailiaili.core.model.formatCount
import app.ailiaili.core.player.VideoSurface
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun FeedCard(
    item: VideoItem,
    overlay: Boolean,
    active: Boolean,
    autoplay: Boolean,
    muted: Boolean,
    volume: Float,
    onMute: () -> Unit,
    onVideo: (Long) -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onComments: () -> Unit,
    onDismiss: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onRetryPage: (() -> Unit)?,
) {
    var retry by remember { mutableIntStateOf(0) }
    val playback = if (active) rememberFeedPlayback(item, autoplay, if (muted) 0f else volume.takeIf { it > 0f } ?: .8f, retry) else null
    val openPlayer = {
        val position = if (!item.isLive && playback?.hasFrame == true && !playback.ended && !playback.failed &&
            !playback.engine.isSeekPending
        ) playback.engine.currentPosition.coerceAtLeast(0L) else 0L
        onVideo(position)
    }
    val navBottom = shellBottomClearance()
    if (overlay) {
        Box(Modifier.fillMaxSize()) {
            FeedStage(item, playback, autoplay, { retry++ }, openPlayer, Modifier.fillMaxSize(), overlay = true)
            FeedOverlay(
                item, playback, muted, onMute, openPlayer, onAuthor, onComments, onDismiss, onRetryPage,
                Modifier.align(Alignment.BottomCenter), navBottom,
            )
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp && maxHeight >= 300.dp) {
            Row(
                Modifier.fillMaxSize().padding(start = 12.dp, top = 4.dp, end = 8.dp, bottom = navBottom + 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.large)) {
                    val scale = feedStageScale(playback?.aspectRatio, maxWidth.value / maxHeight.value)
                    FeedStage(item, playback, autoplay, { retry++ }, openPlayer, Modifier.fillMaxSize(), overlay = true, scale = scale)
                    FeedWideOverlay(item, playback, muted, onMute, openPlayer, onAuthor, onComments, onDismiss)
                }
                Column(
                    Modifier.aliMaterial(AliMaterial.Floating, MaterialTheme.shapes.medium).padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AliStageIconButton(Icons.Filled.KeyboardArrowUp, "上一条", { onPrevious?.invoke() }, enabled = onPrevious != null)
                    AliStageIconButton(Icons.Filled.KeyboardArrowDown, "下一条", { onNext?.invoke() }, enabled = onNext != null)
                    if (onRetryPage != null) AliStageIconButton(Icons.Filled.Replay, "重试加载", onRetryPage)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                FeedStage(item, playback, autoplay, { retry++ }, openPlayer, Modifier.weight(1f).fillMaxWidth())
                FeedMetadata(
                    item, playback, muted, onMute, openPlayer, onAuthor, onComments, onDismiss, onRetryPage,
                    Modifier.fillMaxWidth().padding(bottom = navBottom).padding(9.dp)
                        .aliMaterial(AliMaterial.Playback, MaterialTheme.shapes.large).padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private fun feedStageScale(videoRatio: Float?, stageRatio: Float): PlayerScale {
    if (videoRatio == null || !videoRatio.isFinite() || !stageRatio.isFinite() || videoRatio < 1f || stageRatio < 1f) {
        return PlayerScale.Fit
    }
    return if (minOf(videoRatio, stageRatio) / maxOf(videoRatio, stageRatio) >= .82f) PlayerScale.Crop else PlayerScale.Fit
}

@Composable
private fun FeedStage(
    item: VideoItem,
    playback: FeedPlayback?,
    autoplay: Boolean,
    onRetry: () -> Unit,
    onVideo: () -> Unit,
    modifier: Modifier,
    overlay: Boolean = false,
    scale: PlayerScale = PlayerScale.Fit,
) {
    val backdrop = if (overlay) Modifier.aliAmbient(onMedia = true) else Modifier.background(AliColors.StageMedia)
    Box(modifier.then(backdrop), contentAlignment = Alignment.Center) {
        if (playback != null) {
            VideoSurface(playback.engine, Modifier.fillMaxSize(), scale)
        }
        if (playback?.hasFrame != true) {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (scale == PlayerScale.Crop) ContentScale.Crop else ContentScale.Fit,
            )
        }
        if (playback?.failed == true) {
            Column(
                Modifier.padding(16.dp).aliMaterial(AliMaterial.Panel, MaterialTheme.shapes.large, elevated = true),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FeedNotice("视频暂时无法播放", null, "重试", onRetry)
                TextButton(onClick = onVideo) { Text("打开完整播放器") }
            }
        } else if (playback != null) {
            val label = when {
                playback.ended -> "重新播放"
                playback.paused -> "播放"
                else -> "暂停"
            }
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = playback::toggle,
                ).semantics { contentDescription = "视频画面" },
                contentAlignment = Alignment.Center,
            ) {
                val reducedMotion = LocalAliReducedMotion.current
                val showPlay = playback.paused || playback.ended
                AnimatedVisibility(
                    visible = showPlay,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn() + scaleIn(initialScale = .88f),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut() + scaleOut(targetScale = .88f),
                ) {
                    Box(
                        Modifier.size(68.dp).aliMaterial(AliMaterial.Playback, CircleShape, elevated = true),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playback.ended) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                            null,
                            Modifier.size(34.dp),
                            tint = AliColors.StageInk,
                        )
                    }
                }
                if (!showPlay && !playback.hasFrame && autoplay) {
                    CircularProgressIndicator(Modifier.size(30.dp), color = aliPlaybackAccent(), trackColor = AliColors.StagePanel)
                }
            }
        }
        if (item.isLive && !overlay) {
            Text(
                "直播",
                Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(AliColors.StagePanel, CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AliColors.StageInk,
            )
        }
    }
}

@Composable
private fun FeedMetadata(
    item: VideoItem,
    playback: FeedPlayback?,
    muted: Boolean,
    onMute: () -> Unit,
    onVideo: () -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onComments: () -> Unit,
    onDismiss: (() -> Unit)?,
    onRetryPage: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedAuthor(item, onAuthor, Modifier.weight(1f))
            FeedMediaButtons(muted, onMute, onVideo)
        }
        FeedTitle(item.title, wide = false)
        if (!item.isLive) {
            FeedTimeline(playback, item.durationSec)
        }
        FeedActionDock(item, playback, onComments, onDismiss)
        if (onRetryPage != null) TextButton(onClick = onRetryPage) { Text("重试加载", color = aliPlaybackAccent()) }
    }
}

@Composable
private fun FeedWideOverlay(
    item: VideoItem,
    playback: FeedPlayback?,
    muted: Boolean,
    onMute: () -> Unit,
    onVideo: () -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onComments: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().drawWithCache {
        val bottom = Brush.verticalGradient(
            0f to Color.Transparent, .64f to Color.Black.copy(alpha = .46f), 1f to Color.Black.copy(alpha = .82f),
            startY = size.height * .45f, endY = size.height,
        )
        val side = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = .45f)),
            startX = size.width * .8f,
            endX = size.width,
        )
        onDrawBehind {
            drawRect(bottom)
            if (!item.isLive) drawRect(side)
        }
    }) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 84.dp, bottom = 62.dp).widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (item.isLive) Text("直播中", color = AliColors.StageInk, style = MaterialTheme.typography.labelMedium)
            FeedAuthor(item, onAuthor)
            FeedTitle(item.title, wide = true)
        }
        if (!item.isLive || onDismiss != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 61.dp)) {
                FeedActionDock(item, playback, onComments, onDismiss, vertical = true, floating = true)
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedPlayButton(playback)
            if (item.isLive) {
                Spacer(Modifier.weight(1f))
                FeedIconButton(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    if (muted) "打开声音" else "静音", onMute,
                )
                TextButton(onClick = onVideo) { Text("进入直播间", color = AliColors.StageInk) }
            } else {
                Box(Modifier.weight(1f)) { FeedTimeline(playback, item.durationSec) }
                FeedMediaButtons(muted, onMute, onVideo)
            }
        }
    }
}

@Composable
private fun FeedOverlay(
    item: VideoItem,
    playback: FeedPlayback?,
    muted: Boolean,
    onMute: () -> Unit,
    onVideo: () -> Unit,
    onAuthor: (VideoItem) -> Unit,
    onComments: () -> Unit,
    onDismiss: (() -> Unit)?,
    onRetryPage: (() -> Unit)?,
    modifier: Modifier,
    navBottom: Dp,
) {
    Column(
        modifier.fillMaxWidth().drawWithCache {
            val scrim = Brush.verticalGradient(
                0f to AliColors.StageMedia.copy(alpha = 0f),
                .46f to AliColors.StageMedia.copy(alpha = .32f),
                .75f to AliColors.StageMedia.copy(alpha = .88f),
                1f to AliColors.StageMedia,
            )
            onDrawBehind { drawRect(scrim) }
        }.padding(start = 15.dp, end = 9.dp, bottom = navBottom + if (item.isLive) 12.dp else 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.isLive) {
                    Text(
                        "直播",
                        Modifier.background(AliColors.StagePanel, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = AliColors.StageInk,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                FeedAuthor(item, onAuthor)
                FeedTitle(item.title, wide = false)
                FeedMediaButtons(muted, onMute, onVideo, outlined = true)
                if (onRetryPage != null) {
                    TextButton(onClick = onRetryPage) { Text("重试加载", color = aliPlaybackAccent()) }
                }
            }
            FeedActionDock(item, playback, onComments, onDismiss, vertical = true)
        }
        if (!item.isLive) FeedTimeline(playback, item.durationSec, overlay = true)
    }
}

@Composable
private fun FeedAuthor(
    item: VideoItem,
    onAuthor: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val author = modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
    Row(
        if (item.ownerMid > 0) author.clickable(role = Role.Button, onClickLabel = "打开 UP 主空间") { onAuthor(item) }
        else author,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(AliColors.Ink)
                .border(1.dp, AliColors.StageInk.copy(alpha = .26f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.ownerFace.isBlank()) {
                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = AliColors.StageMuted)
            } else {
                AsyncImage(
                    model = cdnThumb(item.ownerFace, 96),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            item.author.ifBlank { "视频作者" },
            color = AliColors.StageInk,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FeedTitle(title: String, wide: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        title,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(
            role = Role.Button, onClickLabel = if (expanded) "收起标题" else "展开标题",
        ) { expanded = !expanded },
        color = AliColors.StageInk,
        style = if (wide) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = if (expanded) 5 else if (wide) 3 else 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FeedMediaButtons(
    muted: Boolean,
    onMute: () -> Unit,
    onVideo: () -> Unit,
    outlined: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (outlined) 5.dp else 0.dp)) {
        FeedIconButton(
            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            if (muted) "打开声音" else "静音",
            onMute,
            outlined = outlined,
        )
        FeedIconButton(Icons.Filled.Fullscreen, "打开完整播放器", onVideo, outlined = outlined)
    }
}

@Composable
private fun FeedTimeline(playback: FeedPlayback?, durationSec: Int, overlay: Boolean = false) {
    val fallback = durationSec * 1000L
    var duration by remember(playback, fallback) { mutableLongStateOf(fallback) }
    var position by remember(playback) { mutableLongStateOf(0L) }
    var draft by remember(playback) { mutableFloatStateOf(-1f) }
    LaunchedEffect(playback, playback?.ready) {
        if (playback == null || !playback.ready) return@LaunchedEffect
        while (true) {
            val reported = playback.engine.duration
            duration = if (reported > 0) reported else fallback
            if (!playback.engine.isSeekPending) position = playback.engine.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }
    val current = if (draft >= 0f) (draft * duration).toLong() else position
    val fraction = if (duration > 0) (current.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val enabled = playback?.hasFrame == true && !playback.failed && duration > 0
    val showClock = !overlay || draft >= 0f || playback?.paused == true || playback?.ended == true
    val slider: @Composable (Modifier) -> Unit = { modifier ->
        AliStageSeekBar(
            value = fraction,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                if (draft >= 0 && playback != null) {
                    position = (draft * duration).toLong()
                    playback.engine.seekTo(position)
                }
                draft = -1f
            },
            modifier = modifier.semantics {
                contentDescription = "播放进度"
                stateDescription = "${formatClock(current)} / ${formatClock(duration)}"
            },
            enabled = enabled,
        )
    }
    if (overlay) {
        Box(Modifier.fillMaxWidth().height(36.dp)) {
            if (showClock) {
                FeedClock(current, Modifier.align(Alignment.TopStart))
                FeedClock(duration, Modifier.align(Alignment.TopEnd))
            }
            slider(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp))
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedClock(current)
            slider(Modifier.weight(1f).height(34.dp))
            FeedClock(duration)
        }
    }
}

@Composable
private fun FeedClock(time: Long, modifier: Modifier = Modifier) {
    Text(formatClock(time), modifier, color = AliColors.StageMuted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun FeedPlayButton(playback: FeedPlayback?, showCaption: Boolean = false) {
    val (icon, label) = when {
        playback?.ended == true -> Icons.Filled.Replay to "重播"
        playback?.paused == true -> Icons.Filled.PlayArrow to "播放"
        else -> Icons.Filled.Pause to "暂停"
    }
    FeedIconButton(icon, label, playback?.takeUnless { it.failed }?.let { it::toggle }, caption = label.takeIf { showCaption }, accent = true)
}

@Composable
private fun FeedActionDock(
    item: VideoItem,
    playback: FeedPlayback?,
    onComments: () -> Unit,
    onDismiss: (() -> Unit)?,
    vertical: Boolean = false,
    floating: Boolean = false,
) {
    val session by AppGraph.repo.session.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var liked by remember(session.dedeUserId) { mutableStateOf<Boolean?>(null) }
    var changedBy by remember(session.dedeUserId) { mutableIntStateOf(0) }
    var resolvedAid by remember { mutableLongStateOf(item.aid) }
    var likePending by remember(session.dedeUserId) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(playback != null, session.dedeUserId, session.loggedIn) {
        if (playback != null && session.loggedIn && !item.isLive && liked == null && item.aid > 0) {
            AppGraph.repo.hasLiked(item.aid).onSuccess { if (liked == null) liked = it }
        }
    }
    val outlined = vertical && !floating
    val copyLink = {
        val url = if (item.isLive) "https://live.bilibili.com/${item.roomId}"
        else "https://www.bilibili.com/video/${item.bvid}"
        clipboard.setText(AnnotatedString(url))
        QuickActions.showToast("已复制链接")
    }
    val actions: @Composable () -> Unit = {
        if (!vertical) FeedPlayButton(playback, showCaption = true)
        if (!item.isLive) {
            val count = (item.likeCount + changedBy).coerceAtLeast(0L)
            FeedIconButton(
                if (liked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (liked == true) "取消点赞" else "点赞",
                onClick = if (likePending) null else ({
                    if (!session.loggedIn) {
                        QuickActions.showToast("点赞需要先登录")
                    } else {
                        likePending = true
                        val uid = session.dedeUserId
                        scope.launch {
                            val previous = liked ?: run {
                                if (resolvedAid <= 0) {
                                    resolvedAid = AppGraph.repo.video(item.bvid).getOrNull()?.aid ?: 0L
                                }
                                if (resolvedAid > 0) AppGraph.repo.hasLiked(resolvedAid).getOrNull() else null
                            }
                            if (AppGraph.repo.session.value.dedeUserId == uid) {
                                if (previous == null) {
                                    QuickActions.showToast("无法取得点赞状态，请重试")
                                } else {
                                    AppGraph.repo.like(item.bvid, !previous).onSuccess {
                                        if (AppGraph.repo.session.value.dedeUserId == uid) {
                                            changedBy += if (previous) -1 else 1
                                            liked = !previous
                                        }
                                    }.onFailure { QuickActions.showToast(it.message ?: "点赞操作失败") }
                                }
                                likePending = false
                            }
                        }
                    }
                }),
                caption = if (count > 0) formatCount(count) else "点赞",
                accent = liked == true,
                outlined = outlined,
            )
            FeedIconButton(Icons.AutoMirrored.Filled.Comment, "评论", onComments, caption = "评论", outlined = outlined)
            val inLater = resolvedAid > 0 && resolvedAid in QuickActions.laterAids
            FeedIconButton(
                if (inLater) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                "稍后再看",
                onClick = {
                    val mark = { video: VideoItem -> QuickActions.addWatchLater(video) }
                    if (!session.loggedIn) QuickActions.showToast("稍后再看需要先登录")
                    else if (resolvedAid > 0) mark(item.copy(aid = resolvedAid))
                    else scope.launch {
                        AppGraph.repo.video(item.bvid).onSuccess {
                            resolvedAid = it.aid
                            mark(item.copy(aid = resolvedAid))
                        }
                            .onFailure { QuickActions.showToast(it.message ?: "无法加入稍后再看") }
                    }
                },
                caption = "稍后",
                accent = inLater,
                outlined = outlined,
            )
        }
        if (vertical) {
            FeedIconButton(Icons.Filled.Share, "复制链接", copyLink, caption = if (floating) "链接" else "复制链接", outlined = outlined)
        }
        if (!vertical || onDismiss != null) {
            Box {
                FeedIconButton(Icons.Filled.MoreHoriz, "视频菜单", { open = true }, caption = "更多", outlined = outlined)
                AliStageMenu(open, onDismissRequest = { open = false }) {
                    if (!vertical) {
                        DropdownMenuItem(
                            text = { Text("复制链接") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { open = false; copyLink() },
                        )
                    }
                    if (onDismiss != null) {
                        DropdownMenuItem(
                            text = { Text("不感兴趣") },
                            leadingIcon = { Icon(Icons.Filled.NotInterested, null) },
                            onClick = { open = false; onDismiss() },
                        )
                    }
                }
            }
        }
    }
    if (vertical) {
        Column(
            if (floating) Modifier else Modifier.aliMaterial(AliMaterial.Playback, CircleShape).padding(horizontal = 2.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = { actions() },
        )
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = { actions() },
        )
    }
}

@Composable
private fun FeedIconButton(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)?,
    caption: String? = null,
    accent: Boolean = false,
    outlined: Boolean = false,
) {
    val tint = if (onClick == null) AliColors.StageMuted.copy(alpha = .45f)
    else if (accent) aliPlaybackAccent() else AliColors.StageInk
    val width = if (outlined && caption != null) 54.dp else 44.dp
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier.width(width).heightIn(min = 44.dp)
            .clip(if (caption == null) CircleShape else MaterialTheme.shapes.small)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = onClick != null,
                role = Role.Button,
            ) { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (outlined) {
            Box(
                Modifier.size(38.dp).biliPressScale(interaction, .91f)
                    .aliMaterial(AliMaterial.Playback, CircleShape, tinted = accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, label, Modifier.size(20.dp), tint = tint)
            }
        } else {
            Icon(icon, label, Modifier.size(21.dp).biliPressScale(interaction, .88f), tint = tint)
        }
        if (caption != null) {
            Text(
                caption,
                modifier = Modifier.padding(top = if (outlined) 2.dp else 0.dp),
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
