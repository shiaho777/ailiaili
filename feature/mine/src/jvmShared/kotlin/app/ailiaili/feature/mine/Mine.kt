package app.ailiaili.feature.mine

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
import app.ailiaili.core.designsystem.BiliErrorState
import app.ailiaili.core.designsystem.BiliHeaderAction
import app.ailiaili.core.designsystem.BiliSurfaceCard
import app.ailiaili.core.designsystem.biliPressScale
import app.ailiaili.core.designsystem.metricToneColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.designsystem.BiliBadge
import app.ailiaili.core.designsystem.BiliBadgeStyle
import app.ailiaili.core.designsystem.BiliBadgeTone
import app.ailiaili.core.designsystem.BiliEmptyState
import app.ailiaili.core.designsystem.BiliLoadingState
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricStat
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.shellBottomPadding
import app.ailiaili.core.model.FavFolder
import app.ailiaili.core.model.UserState
import coil3.compose.AsyncImage
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MineRoute(
    onLogin: () -> Unit,
    onHistory: () -> Unit,
    onFavorites: () -> Unit,
    onWatchLater: () -> Unit,
    onCache: () -> Unit,
    onSpace: (Long) -> Unit,
    onOpenFolder: (FavFolder) -> Unit,
    onOpenDynamic: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val session by AppGraph.repo.session.collectAsState()
    var user by remember { mutableStateOf<UserState?>(null) }
    var counts by remember { mutableStateOf(MineCounts()) }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var awaitingProfile by remember(session.sessData) { mutableStateOf(session.loggedIn) }

    LaunchedEffect(session.loggedIn, session.sessData, refreshTick) {
        refreshing = refreshTick > 0
        if (!session.loggedIn) {
            user = null
            counts = MineCounts()
            folders = emptyList()
            folderError = null
            folderLoading = false
            refreshing = false
            awaitingProfile = false
            return@LaunchedEffect
        }
        folderLoading = folders.isEmpty()
        val state = AppGraph.repo.userState().getOrNull()?.takeIf { it.loggedIn }
        user = state
        awaitingProfile = false
        if (state == null) {
            counts = MineCounts()
            folders = emptyList()
            folderError = null
            folderLoading = false
            refreshing = false
            return@LaunchedEffect
        }
        val nav = AppGraph.repo.navStat().getOrNull()
        counts = if (nav != null) {
            MineCounts(nav.dynamicCount.toString(), nav.following.toString(), nav.follower.toString())
        } else {
            val relation = AppGraph.repo.relationStat(state.mid).getOrNull()
            MineCounts(
                dynamic = "--",
                following = relation?.first?.toString() ?: "--",
                follower = relation?.second?.toString() ?: "--",
            )
        }
        AppGraph.repo.favFolderCards()
            .onSuccess { got ->
                folders = got
                folderError = if (got.isEmpty()) "还没有创建收藏夹" else null
            }
            .onFailure { error ->
                if (folders.isEmpty()) folderError = error.message ?: "收藏夹加载失败"
            }
        folderLoading = false
        refreshing = false
    }

    Column(Modifier.fillMaxSize().shellBottomPadding()) {
        BiliPageHeader("我的")
        val profile = user
        if (awaitingProfile && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BiliLoadingState(label = "正在加载个人信息…")
            }
        } else if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BiliEmptyState(
                    title = if (session.loggedIn) "登录状态已过期" else "登录后查看你的主页",
                    message = if (session.loggedIn) {
                        "重新登录后即可同步收藏、历史与硬币经验"
                    } else {
                        "收藏、历史、硬币和经验会同步到这里"
                    },
                    icon = Icons.Filled.Person,
                    action = {
                        Button(onClick = onLogin, modifier = Modifier.padding(top = 6.dp)) {
                            Text(if (session.loggedIn) "重新登录" else "登录哔哩哔哩")
                        }
                    },
                )
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshTick++ },
                modifier = Modifier.fillMaxSize(),
            ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .clickable {
                            if (profile.mid > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSpace(profile.mid)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (profile.face.isNotBlank()) {
                        AsyncImage(
                            profile.face,
                            profile.name,
                            Modifier.size(64.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (profile.level > 0) {
                                BiliBadge(
                                    text = "LV${profile.level}",
                                    modifier = Modifier.padding(start = 8.dp),
                                    tone = BiliBadgeTone.Accent,
                                    style = BiliBadgeStyle.Soft,
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MetricChip(
                                MetricSpec(
                                    Icons.Filled.MonetizationOn,
                                    formatCoins(profile.coins),
                                    "硬币",
                                    MetricTone.Coin,
                                ),
                                size = MetricChipSize.Compact,
                            )
                            val exp = expLabel(profile)
                            if (exp != null) {
                                MetricChip(
                                    MetricSpec(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        exp,
                                        "经验",
                                        MetricTone.Accent,
                                    ),
                                    size = MetricChipSize.Compact,
                                )
                            }
                        }
                        val fraction = expFraction(profile)
                        if (fraction != null) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .width(120.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }

                BiliSurfaceCard(
                    Modifier.fillMaxWidth().padding(top = 20.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        MetricStat(counts.dynamic, "动态", Modifier.weight(1f)) {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onOpenDynamic()
                        }
                        MetricStat(counts.following, "关注", Modifier.weight(1f))
                        MetricStat(counts.follower, "粉丝", Modifier.weight(1f))
                    }
                }

                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    val columns = if (maxWidth >= 520.dp) 4 else 2
                    val entries = listOf(
                        ShortcutSpec(Icons.Filled.Download, "离线缓存", MetricTone.Online, onCache),
                        ShortcutSpec(Icons.Filled.History, "观看记录", MetricTone.Danmaku, onHistory),
                        ShortcutSpec(Icons.Filled.Star, "我的收藏", MetricTone.Coin, onFavorites),
                        ShortcutSpec(Icons.Filled.Schedule, "稍后再看", MetricTone.Accent, onWatchLater),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { spec ->
                                    Shortcut(spec.icon, spec.label, metricToneColor(spec.tone), Modifier.weight(1f)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        spec.action()
                                    }
                                }
                                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onFavorites()
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "我的收藏",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (folders.isNotEmpty()) {
                            BiliBadge(folders.size.toString(), tone = BiliBadgeTone.Accent)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "打开收藏",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.weight(1f))
                    if (folderLoading && folders.isNotEmpty()) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp)
                    } else {
                        BiliHeaderAction(Icons.Filled.Refresh, "刷新收藏夹", onClick = { refreshTick++ })
                    }
                }

                if (folderLoading && folders.isEmpty()) {
                    BiliLoadingState(Modifier.fillMaxWidth().padding(top = 24.dp), label = "正在加载收藏夹…")
                }
                folderError?.let {
                    BiliErrorState(
                        message = it,
                        onRetry = { refreshTick++ },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }
                if (folders.isNotEmpty()) {
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                        val columns = if (maxWidth >= 560.dp) 3 else 2
                        val cardWidth = (maxWidth - 14.dp * (columns - 1)) / columns
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val alone = folders.size == 1
                            folders.forEach { folder ->
                                FolderCard(folder, if (alone) Modifier.fillMaxWidth() else Modifier.width(cardWidth)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onOpenFolder(folder)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

private data class MineCounts(
    val dynamic: String = "--",
    val following: String = "--",
    val follower: String = "--",
)

private data class ShortcutSpec(
    val icon: ImageVector,
    val label: String,
    val tone: MetricTone,
    val action: () -> Unit,
)

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .padding(horizontal = 4.dp)
            .biliPressScale(interaction)
            .height(64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderCard(folder: FavFolder, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .biliPressScale(interaction)
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (folder.cover.isNotBlank()) {
                AsyncImage(
                    folder.cover,
                    folder.title,
                    Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            BiliBadge(
                text = "${folder.count} 个视频",
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                tone = BiliBadgeTone.Dark,
                style = BiliBadgeStyle.Solid,
            )
            if (folder.privateFolder) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "私密",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(4.dp),
                    tint = Color.White,
                )
            }
        }
        Text(
            folder.title.ifBlank { "收藏夹" },
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatCoins(value: Double): String {
    val whole = value.toLong()
    if (abs(value - whole) < 0.05) return whole.toString()
    val scaled = kotlin.math.round(value * 10.0).toLong()
    return "${scaled / 10}.${abs(scaled % 10)}"
}

private fun expLabel(user: UserState): String? = when {
    user.expNext > 0 -> "${user.expCurrent}/${user.expNext}"
    user.expCurrent > 0 -> user.expCurrent.toString()
    else -> null
}

private fun expFraction(user: UserState): Float? = when {
    user.expNext > 0 -> (user.expCurrent.toFloat() / user.expNext.toFloat()).coerceIn(0f, 1f)
    user.expCurrent > 0 -> 1f
    else -> null
}
