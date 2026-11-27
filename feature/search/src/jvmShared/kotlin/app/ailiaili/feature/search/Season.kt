package app.ailiaili.feature.search

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import app.ailiaili.core.model.cdnThumb
import app.ailiaili.core.designsystem.BiliPrimaryButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStageNotice
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.LoadBox
import app.ailiaili.core.designsystem.MetaRow
import app.ailiaili.core.designsystem.MetaSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.VideoListRow
import app.ailiaili.core.designsystem.appViewModel
import app.ailiaili.core.model.BangumiDetail
import app.ailiaili.core.model.BangumiEpisode
import app.ailiaili.core.model.formatSeconds
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

class SeasonViewModel(private val seasonId: Long) : ViewModel() {
    var detail by mutableStateOf<BangumiDetail?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    init { reload() }

    fun reload() {
        loading = true
        error = null
        viewModelScope.launch {
            AppGraph.repo.bangumiDetail(seasonId)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
}

@Composable
fun SeasonRoute(
    seasonId: Long,
    title: String,
    onBack: () -> Unit,
    onPlay: (BangumiEpisode, String, List<BangumiEpisode>) -> Unit,
    onLogin: () -> Unit = {},
    vm: SeasonViewModel = appViewModel(key = "season-$seasonId") { SeasonViewModel(seasonId) },
) {
    app.ailiaili.core.designsystem.RegisterPageRefresh(vm::reload)
    val detail = vm.detail
    val episodes = detail?.episodes.orEmpty()
    val session by AppGraph.repo.session.collectAsState()
    val uid = session.dedeUserId
    val scope = rememberCoroutineScope()
    var followed by remember(uid, seasonId) { mutableStateOf<Boolean?>(null) }
    var updating by remember(uid, seasonId) { mutableStateOf(false) }
    var retry by remember(uid, seasonId) { mutableStateOf(0) }
    var followError by remember(uid, seasonId) { mutableStateOf(false) }
    val followType = detail?.followType
    LaunchedEffect(uid, followType, retry) {
        if (!session.loggedIn || followType == null) return@LaunchedEffect
        followError = false
        AppGraph.repo.bangumiFollowed(seasonId, followType)
            .onSuccess { followed = it }
            .onFailure { followError = true }
    }
    Scaffold(
        topBar = {
            AliStagePageHeader(detail?.title?.ifBlank { title } ?: title, onBack = onBack) {
                if (detail != null) {
                    val label = if (followType == 2) "追剧" else "追番"
                    TextButton(
                        onClick = {
                            val known = followed
                            when {
                                !session.loggedIn -> onLogin()
                                followError -> retry++
                                known != null && !updating -> {
                                    updating = true
                                    scope.launch {
                                        AppGraph.repo.setBangumiFollow(seasonId, !known)
                                            .onSuccess { if (AppGraph.repo.session.value.dedeUserId == uid) followed = !known }
                                            .onFailure { app.ailiaili.core.designsystem.QuickActions.showToast(it.message ?: "操作失败") }
                                        updating = false
                                    }
                                }
                            }
                        },
                        enabled = !updating && (!session.loggedIn || followed != null || followError),
                    ) {
                        Text(when {
                            followError -> "重试${label}状态"
                            updating || session.loggedIn && followed == null -> "$label…"
                            followed == true -> "取消$label"
                            else -> label
                        })
                    }
                }
            }
        },
    ) { padding ->
        AliStageBody(Modifier.padding(padding), maxWidth = 960.dp) {
            LoadBox(
                vm.loading,
                vm.error,
                detail == null,
                "暂无选集",
                vm::reload,
            ) {
                val shown = detail ?: return@LoadBox
                val seasonState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    Modifier.fillMaxSize().smoothWheelScroll(seasonState, app.ailiaili.core.designsystem.LocalWheelSpeed.current),
                    state = seasonState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") {
                        val first = episodes.firstOrNull()
                        SeasonHeader(shown, onPlayFirst = first?.let { { onPlay(it, shown.title, episodes) } })
                    }
                    if (episodes.isEmpty()) {
                        item(key = "empty-episodes") { AliStageNotice("暂无选集", null, Icons.Filled.PlayArrow) }
                    } else {
                        stickyHeader(key = "episodes-title") {
                            BiliSectionTitle(
                                "选集 · ${episodes.size} 集",
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
                            )
                        }
                    }
                    itemsIndexed(
                        shown.episodes,
                        key = { _, ep -> "${ep.epId}-${ep.bvid}-${ep.cid}" },
                    ) { index, ep ->
                        val latest = index == shown.episodes.lastIndex
                        VideoListRow(
                            // title 常常只是集号，长标题才是真正的标题；没有长标题时回退。
                            title = ep.longTitle.ifBlank { ep.title },
                            cover = ep.cover,
                            onClick = { onPlay(ep, shown.title, shown.episodes) },
                            modifier = Modifier.animateItem().padding(horizontal = 8.dp),
                            coverWidth = 96.dp,
                            coverPlaceholder = false,
                            panel = false,
                            leading = {
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier.width(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            meta = if (ep.durationSec > 0) {
                                {
                                    MetaRow(
                                        items = listOf(
                                            MetaSpec(
                                                Icons.Filled.Schedule,
                                                formatSeconds(ep.durationSec),
                                                MetricTone.Neutral,
                                            ),
                                        ),
                                        maxItems = 1,
                                    )
                                }
                            } else {
                                null
                            },
                            trailing = if (latest) {
                                {
                                    BiliBadge(
                                        text = "最新",
                                        style = BiliBadgeStyle.Soft,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(detail: BangumiDetail, onPlayFirst: (() -> Unit)?) {
    var expanded by remember(detail.seasonId) { mutableStateOf(false) }
    var overflow by remember(detail.seasonId) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val backdrop = colors.surfaceContainerLow
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        .clip(MaterialTheme.shapes.large).background(backdrop)) {
        if (detail.cover.isNotBlank()) {
            AsyncImage(
                model = cdnThumb(detail.cover, 360),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                alpha = .28f,
                alignment = Alignment.CenterEnd,
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().drawWithCache {
                val veil = Brush.horizontalGradient(
                    0f to backdrop,
                    .45f to backdrop.copy(alpha = .92f),
                    1f to backdrop.copy(alpha = .36f),
                )
                onDrawBehind { drawRect(veil) }
            })
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (detail.cover.isNotBlank()) {
                    AsyncImage(
                        model = detail.cover,
                        contentDescription = detail.title,
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        detail.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val kind = if (detail.followType == 2) "影视" else "番剧"
                    val episodeLabel = if (detail.episodes.isEmpty()) "暂无选集" else "${detail.episodes.size} 集"
                    Text(
                        "$kind · $episodeLabel",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (onPlayFirst != null) {
                        BiliPrimaryButton(onClick = onPlayFirst) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("播放第 1 集")
                        }
                    }
                }
            }
            if (detail.evaluate.isNotBlank()) {
                Text(
                    detail.evaluate,
                    Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                )
                if (overflow || expanded) {
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                        Text(if (expanded) "收起简介" else "展开简介")
                    }
                }
            }
        }
    }
}
