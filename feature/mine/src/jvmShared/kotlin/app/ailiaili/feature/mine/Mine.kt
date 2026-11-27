package app.ailiaili.feature.mine

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
import app.ailiaili.core.designsystem.BiliErrorState
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.AliStageIconButton
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.BiliSectionHeader
import app.ailiaili.core.designsystem.biliPressScale
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import app.ailiaili.core.designsystem.BiliPrimaryButton
import androidx.compose.material3.CircularProgressIndicator
import app.ailiaili.core.designsystem.AliStageMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
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
import app.ailiaili.core.designsystem.BiliLoadingState
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricStat
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.shellBottomClearance
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
    onRelations: (Long, Boolean) -> Unit = { _, _ -> },
    onFollowedSeasons: () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val session by AppGraph.repo.session.collectAsState()
    val savedAccounts by AppGraph.repo.accounts.collectAsState()
    var accountMenuOpen by remember { mutableStateOf(false) }
    var switchingAccount by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf<UserState?>(null) }
    var counts by remember { mutableStateOf(MineCounts()) }
    var folders by remember { mutableStateOf<List<FavFolder>>(emptyList()) }
    var folderLoading by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var createFolder by remember(session.dedeUserId) { mutableStateOf(false) }
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
        if (state != null) {
            // 归档当前账号并补齐昵称/头像（老版本升级上来的会话也进列表）。
            AppGraph.repo.rememberCurrentAccount()
            AppGraph.accounts.updateProfile(state.mid.toString(), state.name, state.face)
        }
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

    if (createFolder) {
        app.ailiaili.core.designsystem.CreateFavoriteFolderDialog(onDismiss = { createFolder = false }) {
            createFolder = false
            refreshTick++
        }
    }
    val navBottom = shellBottomClearance()
    Column(Modifier.fillMaxSize()) {
        AliStagePageHeader("我的")
        val profile = user
        if (awaitingProfile && profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BiliLoadingState(label = "正在加载个人信息…")
            }
        } else if (profile == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val scroll = rememberScrollState()
                Column(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(scroll)
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp + navBottom),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AliStagePanel(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Box(
                                Modifier.size(54.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (session.loggedIn) "登录状态已过期" else "你的账号",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "登录后同步收藏、观看历史和稍后再看。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        BiliPrimaryButton(onClick = onLogin, modifier = Modifier.align(Alignment.End).padding(top = 14.dp)) {
                            Text(if (session.loggedIn) "重新登录" else "登录哔哩哔哩")
                        }
                    }
                    AliStagePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        BiliSectionHeader("资料库")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Shortcut(Icons.Filled.History, "观看历史", Modifier.weight(1f), onClick = onHistory)
                            Shortcut(Icons.Filled.Star, "收藏", Modifier.weight(1f), onClick = onFavorites)
                            Shortcut(Icons.Filled.Download, "离线缓存", Modifier.weight(1f), onClick = onCache)
                        }
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshTick++ },
                modifier = Modifier.fillMaxSize(),
            ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val mineScroll = rememberScrollState()
            Column(
                Modifier
                    .widthIn(max = 940.dp)
                    .fillMaxSize()
                    .verticalScroll(mineScroll)
                    .smoothWheelScroll(mineScroll, app.ailiaili.core.designsystem.LocalWheelSpeed.current)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp + navBottom),
            ) {
                AliStagePanel(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Row(
                    Modifier
                        .weight(1f)
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
                            app.ailiaili.core.model.cdnThumb(profile.face, 130),
                            profile.name,
                            Modifier.size(64.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .padding(4.dp).clip(CircleShape),
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
                                        MetricTone.Neutral,
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
                Box {
                    IconButton(
                        enabled = !switchingAccount,
                        onClick = { accountMenuOpen = true },
                    ) {
                        if (switchingAccount) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = "切换账号",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AliStageMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                        savedAccounts.forEach { account ->
                            val isCurrent = account.uid == session.dedeUserId
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (account.face.isNotBlank()) {
                                            AsyncImage(
                                                app.ailiaili.core.model.cdnThumb(account.face, 48),
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                            )
                                            Box(Modifier.width(8.dp))
                                        }
                                        Text(
                                            account.name.ifBlank { "账号 ${account.uid}" },
                                            modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isCurrent) {
                                            Text(
                                                " ✓",
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "删除这个账号",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable {
                                                accountMenuOpen = false
                                                scope.launch { AppGraph.repo.removeAccount(account.uid) }
                                            },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    accountMenuOpen = false
                                    if (!isCurrent && !switchingAccount) {
                                        switchingAccount = true
                                        scope.launch {
                                            AppGraph.repo.switchAccount(account.uid)
                                                .onSuccess {
                                                    app.ailiaili.core.designsystem.QuickActions.showToast(
                                                        "已切换到 ${account.name.ifBlank { account.uid }}",
                                                    )
                                                }
                                                .onFailure {
                                                    app.ailiaili.core.designsystem.QuickActions.showToast(
                                                        it.message ?: "切换失败",
                                                    )
                                                }
                                            switchingAccount = false
                                        }
                                    }
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("登录新账号") },
                            onClick = {
                                accountMenuOpen = false
                                onLogin()
                            },
                        )
                    }
                }
                }

                HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth()) {
                    MetricStat(counts.dynamic, "动态", Modifier.weight(1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onOpenDynamic()
                    }
                    MetricStat(counts.following, "关注", Modifier.weight(1f)) { onRelations(profile.mid, false) }
                    MetricStat(counts.follower, "粉丝", Modifier.weight(1f)) { onRelations(profile.mid, true) }
                }
                }

                AliStagePanel(Modifier.fillMaxWidth().padding(top = 14.dp), contentPadding = PaddingValues(12.dp)) {
                    BiliSectionHeader("常用")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 530.dp) 5 else if (maxWidth >= 270.dp) 3 else 2
                        val entries = listOf(
                            ShortcutSpec(Icons.Filled.Download, "离线缓存", onCache),
                            ShortcutSpec(Icons.Filled.History, "观看记录", onHistory),
                            ShortcutSpec(Icons.Filled.Star, "我的收藏", onFavorites),
                            ShortcutSpec(Icons.Filled.Schedule, "稍后再看", onWatchLater),
                            ShortcutSpec(Icons.Filled.Star, "我的追番", onFollowedSeasons),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { spec ->
                                        Shortcut(spec.icon, spec.label, Modifier.weight(1f)) {
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            spec.action()
                                        }
                                    }
                                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                                }
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
                            BiliBadge(folders.size.toString())
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "打开收藏",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.weight(1f))
                    AliStageIconButton(Icons.Filled.Add, "新建收藏夹", onClick = { createFolder = true })
                    if (folderLoading && folders.isNotEmpty()) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp).size(18.dp), strokeWidth = 2.dp)
                    } else {
                        AliStageIconButton(Icons.Filled.Refresh, "刷新收藏夹", onClick = { refreshTick++ })
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
}

private data class MineCounts(
    val dynamic: String = "--",
    val following: String = "--",
    val follower: String = "--",
)

private data class ShortcutSpec(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit,
)

@Composable
private fun Shortcut(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.biliPressScale(interaction).heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer).border(1.dp, colors.outlineVariant.copy(alpha = .64f), RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(32.dp).background(colors.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.onPrimaryContainer)
        }
        Text(
            label, color = colors.onSurface, style = MaterialTheme.typography.labelMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
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
                    app.ailiaili.core.model.cdnThumb(folder.cover, 640, 220),
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
