package app.ailiaili.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BiliRailItem(val label: String, val icon: ImageVector, val inactiveIcon: ImageVector = icon)

enum class BiliChrome { None, Rail, Bottom }

val LocalShellDockClearance = compositionLocalOf<Dp?> { null }

@Composable
fun shellBottomClearance(): Dp =
    LocalShellDockClearance.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun BiliShell(
    chrome: BiliChrome,
    rail: @Composable (wide: Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
    overlay: @Composable () -> Unit = {},
    /**
     * 覆盖层置底：播放器保活（用户从播放页进了别的页面）时，播放层要画在
     * 内容下面。zIndex 只在同一 Box 的直接子级间生效，跨子布局无效，
     * 所以这里直接调整绘制顺序，而不是靠 zIndex。
     */
    overlayBelowContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val latest = rememberUpdatedState(content)
    val latestOverlay = rememberUpdatedState(overlay)
    val page = remember { movableContentOf { latest.value.invoke() } }
    val layer = remember { movableContentOf { latestOverlay.value.invoke() } }
    Box(
        Modifier
            .fillMaxSize()
            .aliAmbient(),
    ) {
        when (chrome) {
            BiliChrome.None -> {
                if (overlayBelowContent) {
                    layer()
                    page()
                } else {
                    page()
                    layer()
                }
            }
            BiliChrome.Rail -> RailFrame(rail) {
                if (overlayBelowContent) {
                    layer()
                    page()
                } else {
                    page()
                    layer()
                }
            }
            BiliChrome.Bottom -> {
                if (overlayBelowContent) {
                    layer()
                    BottomFrame(bottomBar, page)
                } else {
                    BottomFrame(bottomBar, page)
                    layer()
                }
            }
        }
    }
}

@Composable
private fun RailFrame(
    rail: @Composable (wide: Boolean) -> Unit,
    page: @Composable () -> Unit,
) {
    // 不要在这里用 BoxWithConstraints：它会把页面放进 SubcomposeLayout。
    // 页面用 movableContentOf 在 Rail / None 之间移动时，从子组合移出后
    // 内容会停止重组，AnimatedContent 的 target 更新丢失（侧边栏消失、新页面打不开）。
    val wide = LocalWindowForm.current.width >= 920.dp
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(if (wide) 156.dp else 72.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            rail(wide)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { page() }
    }
}

@Composable
private fun BottomFrame(
    bottomBar: @Composable () -> Unit,
    page: @Composable () -> Unit,
) {
    var dockHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val systemBottom = WindowInsets.navigationBars.getBottom(density)
    val clearance = with(density) { maxOf(dockHeight, systemBottom).toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)) {
            CompositionLocalProvider(LocalShellDockClearance provides clearance) {
                page()
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { dockHeight = it.height }) {
            bottomBar()
        }
    }
}

@Composable
fun BiliBottomBar(
    items: List<BiliRailItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .aliMaterial(AliMaterial.Floating, RoundedCornerShape(32.dp))
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { index, item ->
                BiliNavigationItem(item, index == selected, { onSelect(index) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BiliRail(
    items: List<BiliRailItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 12.dp else 7.dp, vertical = 16.dp),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Image(
            appIconPainter(),
            contentDescription = null,
            modifier = Modifier.padding(start = if (wide) 8.dp else 0.dp, bottom = 16.dp).size(42.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 3.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items.forEachIndexed { index, item ->
                BiliNavigationItem(item, index == selected, { onSelect(index) }, Modifier.fillMaxWidth(), horizontal = wide)
            }
        }
    }
}

@Composable
private fun BiliNavigationItem(
    item: BiliRailItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    horizontal: Boolean = false,
) {
    val palette = MaterialTheme.colorScheme
    val activeInk = palette.onPrimaryContainer
    val inactiveInk = palette.onSurfaceVariant
    val selection = palette.primaryContainer
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill = animateColorAsState(
        targetValue = when {
            selected -> selection
            hovered -> palette.surfaceContainerHigh.copy(alpha = .70f)
            else -> selection.copy(alpha = 0f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "navigation-selection",
    )
    val itemModifier = modifier
        .heightIn(min = if (horizontal) 48.dp else 50.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(fill.value)
        .selectable(
            selected = selected,
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Tab,
            onClick = {
                if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
        )
    val content: @Composable () -> Unit = {
        val ink = if (selected) activeInk else inactiveInk
        Icon(
            if (selected) item.icon else item.inactiveIcon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(21.dp).biliPressScale(interaction, .96f),
        )
        Text(
            item.label,
            color = ink,
            style = if (horizontal) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (horizontal) {
        Row(
            itemModifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    } else {
        Column(
            itemModifier.padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) { content() }
    }
}

@Composable
fun BiliPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 一级页面顶栏：大标题 + 右侧操作。所有 tab 页统一用它，不要再各自拼 Row。 */
@Composable
fun BiliPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    AliStagePageHeader(title, modifier, subtitle = subtitle, actions = actions)
}

internal val BiliToolbarControlHeight = 44.dp

@Composable
fun BiliHeaderAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLabel: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val activate = {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onClick()
    }
    if (!showLabel) {
        AliStageIconButton(icon, label, activate, modifier, enabled = enabled)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier.heightIn(min = BiliToolbarControlHeight)
            .biliPressScale(interaction)
            .aliMaterial(AliMaterial.Floating, MaterialTheme.shapes.small)
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = activate)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
        Text(label, color = ink, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BiliSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 12.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun BiliBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    AliStagePageHeader(title, modifier, onBack = onBack, actions = actions)
}

/** 播放器信息区：简介 / 评论是文字页签，合集是紧挨在右边的动作，不进同一组。 */
@Composable
fun BiliContentTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionOn: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val hasAction = actionLabel != null && onAction != null
    AliStageTabs(
        labels = if (hasAction) labels + requireNotNull(actionLabel) else labels,
        selected = if (hasAction && actionOn) labels.size else selected,
        onSelect = { index -> if (hasAction && index == labels.size) onAction?.invoke() else onSelect(index) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun BiliFloatButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "float",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .size(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 4.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}
