package app.ailiaili.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ailiaili.core.designsystem.AliMaterial
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.BiliSectionHeader
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.aliMaterial
import app.ailiaili.core.designsystem.biliPressScale
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.model.UrlHit
import app.ailiaili.core.model.formatClock

internal enum class SearchSuggestionSource { Recent, Trending, Link }

internal data class SearchSuggestion(val value: String, val label: String, val source: SearchSuggestionSource)

internal fun searchSuggestions(draft: String, recent: List<String>, hot: List<String>): List<SearchSuggestion> {
    val query = draft.trim()
    if (query.isEmpty()) return emptyList()
    val candidates = recent.map { SearchSuggestion(it, it, SearchSuggestionSource.Recent) } +
        hot.map { SearchSuggestion(it, it, SearchSuggestionSource.Trending) }
    return candidates.asSequence()
        .filter { it.value.contains(query, ignoreCase = true) && !it.value.equals(query, ignoreCase = true) }
        .distinctBy { it.value.lowercase() }
        .sortedBy { !it.value.startsWith(query, ignoreCase = true) }
        .take(5)
        .toList()
}

internal fun linkSuggestion(raw: String, hit: UrlHit): SearchSuggestion? {
    val label = when (hit) {
        is UrlHit.Video -> listOfNotNull(
            "打开视频",
            hit.page?.let { "第 $it P" },
            hit.positionMs?.let(::formatClock),
        ).joinToString(" · ")
        is UrlHit.Live -> "打开直播间"
        is UrlHit.Space -> "打开 UP 主空间"
        is UrlHit.Keyword -> return null
    }
    return SearchSuggestion(raw, label, SearchSuggestionSource.Link)
}

@Composable
internal fun SearchDiscovery(
    history: List<String>,
    hot: List<String>,
    hint: String,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(
        Modifier.fillMaxWidth().smoothWheelScroll(scroll, LocalWheelSpeed.current).verticalScroll(scroll).padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (history.isEmpty() && hot.isEmpty()) {
            Text(hint, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else if (maxWidth >= 700.dp && history.isNotEmpty() && hot.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RecentSearches(history, onSearch, onRemove, onClear, Modifier.weight(1f))
                TrendingSearches(hot, onSearch, Modifier.weight(1.18f))
            }
        } else {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (history.isNotEmpty()) RecentSearches(history, onSearch, onRemove, onClear, Modifier.fillMaxWidth())
                if (hot.isNotEmpty()) TrendingSearches(hot, onSearch, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RecentSearches(
    history: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        BiliSectionHeader(title = "最近搜索", action = "清空历史", onAction = onClear)
        AliStagePanel(Modifier.fillMaxWidth(), PaddingValues(6.dp)) {
            (if (expanded) history else history.take(5)).forEach { word ->
                SearchDiscoveryRow(
                    word, SearchSuggestionSource.Recent, onSelect = { onSearch(word) },
                    onRemove = { onRemove(word) },
                )
            }
            if (history.size > 5) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.Start)) {
                    Text(if (expanded) "收起" else "显示全部 ${history.size} 条")
                }
            }
        }
    }
}

@Composable
private fun TrendingSearches(hot: List<String>, onSearch: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        BiliSectionHeader(title = "热搜")
        AliStagePanel(Modifier.fillMaxWidth(), PaddingValues(6.dp)) {
            hot.forEachIndexed { index, word ->
                SearchDiscoveryRow(word, SearchSuggestionSource.Trending, rank = index + 1, onSelect = { onSearch(word) })
            }
        }
    }
}

@Composable
internal fun SearchSuggestionPanel(
    suggestions: List<SearchSuggestion>,
    selectedIndex: Int,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.aliMaterial(AliMaterial.Floating, MaterialTheme.shapes.large, elevated = true)
            .verticalScroll(rememberScrollState()).padding(6.dp),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            SearchDiscoveryRow(
                suggestion.label, suggestion.source, highlighted = index == selectedIndex,
                onSelect = { onSelect(suggestion.value) },
            )
        }
    }
}

@Composable
private fun SearchDiscoveryRow(
    label: String,
    source: SearchSuggestionSource,
    onSelect: () -> Unit,
    rank: Int? = null,
    highlighted: Boolean = false,
    onRemove: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = highlighted || focused
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 44.dp).biliPressScale(interaction, .985f)
                .clip(MaterialTheme.shapes.medium)
                .background(when {
                    active -> colors.primaryContainer
                    hovered -> colors.surfaceContainer
                    else -> Color.Transparent
                })
                .then(if (highlighted) Modifier.semantics { selected = true } else Modifier)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onSelect)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (rank != null) {
                    Text(
                        rank.toString(), color = if (rank <= 3) colors.onSurface else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Icon(
                        when (source) {
                            SearchSuggestionSource.Recent -> Icons.Filled.History
                            SearchSuggestionSource.Trending -> Icons.Filled.TrendingUp
                            SearchSuggestionSource.Link -> Icons.Filled.Link
                        },
                        null, Modifier.size(18.dp), tint = colors.onSurfaceVariant,
                    )
                }
            }
            Text(label, Modifier.weight(1f), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onRemove == null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            }
        }
        if (onRemove != null) AliStageIconButton(Icons.Filled.Close, "移除搜索记录：$label", onRemove)
    }
}
