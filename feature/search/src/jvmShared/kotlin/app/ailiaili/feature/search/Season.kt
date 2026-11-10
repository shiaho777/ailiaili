package app.ailiaili.feature.search

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.ailiaili.core.designsystem.BiliBackHeader
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.BiliSurfaceCard
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
    onPlay: (BangumiEpisode, seasonTitle: String) -> Unit,
    vm: SeasonViewModel = appViewModel(key = "season-$seasonId") { SeasonViewModel(seasonId) },
) {
    val detail = vm.detail
    val episodes = detail?.episodes.orEmpty()
    Scaffold(
        topBar = { BiliBackHeader(detail?.title ?: title, onBack) },
    ) { padding ->
        LoadBox(
            vm.loading,
            vm.error,
            detail == null || episodes.isEmpty(),
            "暂无选集",
            vm::reload,
            Modifier.padding(padding),
        ) {
            val shown = detail ?: return@LoadBox
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "header") {
                    SeasonHeader(shown)
                }
                item(key = "episodes-title") {
                    BiliSectionTitle(
                        "选集 · 共 ${shown.episodes.size} 集",
                        Modifier.padding(horizontal = 16.dp),
                    )
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
                        onClick = { onPlay(ep, shown.title) },
                        modifier = Modifier.animateItem(),
                        coverWidth = 112.dp,
                        coverPlaceholder = false,
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
                                    tone = BiliBadgeTone.Accent,
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

@Composable
private fun SeasonHeader(detail: BangumiDetail) {
    BiliSurfaceCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (detail.cover.isNotBlank()) {
                AsyncImage(
                    model = detail.cover,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .width(112.dp)
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BiliBadge(
                        text = "全 ${detail.episodes.size} 集",
                        tone = BiliBadgeTone.Accent,
                        style = BiliBadgeStyle.Soft,
                    )
                    BiliBadge(
                        text = "番剧",
                        tone = BiliBadgeTone.Neutral,
                        style = BiliBadgeStyle.Outline,
                    )
                }
                if (detail.evaluate.isNotBlank()) {
                    Text(
                        detail.evaluate,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
