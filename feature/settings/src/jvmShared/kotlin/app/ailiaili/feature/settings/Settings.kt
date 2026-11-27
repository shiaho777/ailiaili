package app.ailiaili.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clipToBounds
import app.ailiaili.core.designsystem.AliNavigationMotion
import app.ailiaili.core.designsystem.LocalAliReducedMotion
import app.ailiaili.core.designsystem.aliScreenTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import app.ailiaili.core.designsystem.BiliFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.AppFile
import app.ailiaili.core.data.AppRelease
import app.ailiaili.core.data.hideAppWindow
import app.ailiaili.core.data.imageCacheDirs
import app.ailiaili.core.data.chooseDirectory
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.data.latestAppRelease
import app.ailiaili.core.data.saveTextFile
import app.ailiaili.core.data.readTextFile
import app.ailiaili.core.data.SETTINGS_BACKUP_NAME
import app.ailiaili.core.data.traySupported
import app.ailiaili.core.model.SponsorCategories
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import app.ailiaili.core.designsystem.BiliSettingsLabel
import app.ailiaili.core.designsystem.BiliSettingsNote
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.designsystem.BiliSwitchRow
import app.ailiaili.core.designsystem.AliStageBody
import app.ailiaili.core.designsystem.AliStagePageHeader
import app.ailiaili.core.designsystem.AliStagePanel
import app.ailiaili.core.designsystem.AliStageTabs
import app.ailiaili.core.designsystem.SystemUiFontPath
import app.ailiaili.core.designsystem.loadFontFamily
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.shellBottomClearance
import app.ailiaili.core.model.CdnLine
import app.ailiaili.core.model.CdnMode
import app.ailiaili.core.model.RecommendSource
import app.ailiaili.core.model.ShellLayout
import app.ailiaili.core.model.ThemeMode
import app.ailiaili.core.model.qnLabel
import app.ailiaili.core.model.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val qualities = listOf(16, 32, 64, 80, 112, 116, 120)

private val settingsTabs = listOf("通用", "播放", "数据管理", "关于")

/** 关键词防抖落盘用独立作用域：页面离开组合被取消时，最后一次编辑也要写进去。 */
private val keywordSaveScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
)
private var keywordSaveJob: kotlinx.coroutines.Job? = null

@Composable
fun SettingsRoute(
    onLogout: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAuthor: () -> Unit = {},
    onOpenDisclaimer: () -> Unit = {},
) {
    val settings by AppGraph.repo.settings.collectAsState()
    val session by AppGraph.repo.session.collectAsState()
    val scope = rememberCoroutineScope()
    val platformContext = coil3.compose.LocalPlatformContext.current
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<Result<AppRelease?>?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabState = rememberSaveableStateHolder()
    var settingsSearch by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<SettingsSearchTarget?>(null) }
    val reached: () -> Unit = { jumpTo = null }
    val anchor: @Composable (SettingsItem) -> Modifier = { settingsJumpAnchor(jumpTo?.item == it, reached) }
    val downloadsRevision by AppGraph.downloads.completedRevision.collectAsState()
    val reducedMotion = LocalAliReducedMotion.current
    var area by remember(settings.danmakuAreaPercent) { mutableFloatStateOf(settings.danmakuAreaPercent.toFloat()) }
    var tabsHeight by remember { mutableIntStateOf(0) }
    val tabsClearance = with(LocalDensity.current) { tabsHeight.toDp() }
    val navBottom = shellBottomClearance()
    Column(Modifier.fillMaxSize()) {
        AliStagePageHeader("设置") {
            app.ailiaili.core.designsystem.AliStageIconButton(
                androidx.compose.material.icons.Icons.Filled.Search,
                "查找设置",
                onClick = { settingsSearch = true },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AliStageBody(maxWidth = 850.dp) {
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = { aliScreenTransition(AliNavigationMotion.Tab, reducedMotion) },
                    label = "settings-tab",
                ) { section ->
                    tabState.SaveableStateProvider(section) {
                        val pageScroll = rememberScrollState()
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                                .then(if (section != tab) Modifier.clearAndSetSemantics {} else Modifier)
                                .smoothWheelScroll(pageScroll, LocalWheelSpeed.current)
                                .verticalScroll(pageScroll).padding(top = tabsClearance + 6.dp, bottom = navBottom + 24.dp),
                        ) {
        when (section) {
            0 -> {
                if (isDesktopHost()) {
                    SettingsSectionHeading(SettingsSection.Interaction, jumpTo, reached)
                    SettingsCard {
                        var wheelSpeed by remember(settings.wheelSpeedPercent) {
                            mutableFloatStateOf(settings.wheelSpeedPercent.toFloat())
                        }
                        BiliSettingsLabel(
                            "滚轮速度 ${wheelSpeed.toInt()}%",
                            supporting = "默认 250%，数值越大滚得越快。",
                        )
                        Slider(
                            value = wheelSpeed,
                            onValueChange = { wheelSpeed = it },
                            onValueChangeFinished = {
                                AppGraph.repo.updateSettings {
                                    it.copy(wheelSpeedPercent = wheelSpeed.toInt().coerceIn(100, 500))
                                }
                            },
                            valueRange = 100f..500f,
                            steps = 7,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                SettingsSectionHeading(SettingsSection.Content, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel("启动页面", supporting = "下次全新启动时打开所选页面。")
                    RowChips {
                        app.ailiaili.core.model.StartupPage.entries.forEach { page ->
                            BiliFilterChip(
                                selected = settings.startupPage == page,
                                onClick = { AppGraph.repo.updateSettings { it.copy(startupPage = page) } },
                                label = { Text(page.label) },
                            )
                        }
                    }
                    BiliSettingsLabel("收藏显示", supporting = "可在收藏夹中随时切换，并记住上次选择。")
                    RowChips {
                        BiliFilterChip(
                            selected = settings.favoriteGrid,
                            onClick = { AppGraph.repo.updateSettings { it.copy(favoriteGrid = true) } },
                            label = { Text("封面") },
                        )
                        BiliFilterChip(
                            selected = !settings.favoriteGrid,
                            onClick = { AppGraph.repo.updateSettings { it.copy(favoriteGrid = false) } },
                            label = { Text("列表") },
                        )
                    }
                }
                SettingsCard {
                    BiliSettingsLabel(
                        "推荐来源",
                        supporting = "网页版推荐更稳，App 端推荐更接近手机客户端。切换后立即重拉首页推荐。",
                    )
                    RowChips {
                        listOf(
                            RecommendSource.Web to "网页版",
                            RecommendSource.App to "App 端",
                        ).forEach { (source, label) ->
                            BiliFilterChip(
                                selected = settings.recommendSource == source,
                                onClick = {
                                    AppGraph.repo.updateSettings { it.copy(recommendSource = source) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                SettingsSectionHeading(SettingsSection.Appearance, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel("标签栏", supporting = "自动：手机竖屏用底部，平板和电脑窗口用左侧。也可以固定某一种。")
                    RowChips {
                        BiliFilterChip(
                            selected = settings.shellLayout == ShellLayout.Auto,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Auto) } },
                            label = { Text("自动") },
                        )
                        BiliFilterChip(
                            selected = settings.shellLayout == ShellLayout.Bottom,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Bottom) } },
                            label = { Text("底部标签") },
                        )
                        BiliFilterChip(
                            selected = settings.shellLayout == ShellLayout.Rail,
                            onClick = { AppGraph.repo.updateSettings { it.copy(shellLayout = ShellLayout.Rail) } },
                            label = { Text("左侧导航") },
                        )
                    }
                    BiliSettingsLabel("应用主题", modifier = anchor(SettingsItem.Theme))
                    RowChips {
                        ThemeMode.entries.forEach { mode ->
                            BiliFilterChip(
                                selected = settings.theme == mode,
                                onClick = { AppGraph.repo.updateSettings { it.copy(theme = mode) } },
                                label = {
                                    Text(when (mode) {
                                        ThemeMode.System -> "跟随系统"
                                        ThemeMode.Light -> "浅色"
                                        ThemeMode.Dark -> "深色"
                                    })
                                },
                            )
                        }
                    }
                    BiliSwitchRow(
                        title = "减少界面动效",
                        modifier = anchor(SettingsItem.Motion),
                        checked = settings.reduceMotion,
                        onCheckedChange = { enabled -> AppGraph.repo.updateSettings { it.copy(reduceMotion = enabled) } },
                        supporting = "减少页面、按钮和播放控制栏的过渡效果",
                    )
                    ThemeAccentSettings(
                        current = settings.accentHex,
                        followSystem = settings.accentFollowSystem,
                        modifier = anchor(SettingsItem.ThemeColor),
                        onSelect = { hex, system ->
                            AppGraph.repo.updateSettings { it.copy(accentFollowSystem = system, accentHex = hex) }
                        },
                    )
                    FontPickerSection(settings.fontPath, modifier = anchor(SettingsItem.Font))
                    if (isDesktopHost() && traySupported()) {
                        BiliSwitchRow(
                            title = "关闭时最小化到托盘",
                            checked = settings.closeToTray,
                            onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(closeToTray = on) } },
                            supporting = if (settings.closeToTray) {
                                "点右上角关闭键会收进系统托盘，双击托盘图标可恢复"
                            } else {
                                "点右上角关闭键将直接退出程序"
                            },
                            icon = Icons.Filled.Minimize,
                            tone = MetricTone.Danmaku,
                        )
                        if (settings.closeToTray) {
                            BiliSettingsRow(
                                title = "立刻收到托盘",
                                supporting = "主窗口会藏起来，进程继续跑",
                                icon = Icons.Filled.VisibilityOff,
                                tone = MetricTone.Neutral,
                                onClick = { hideAppWindow() },
                            )
                        }
                    }
                    if (session.loggedIn) {
                        BiliSwitchRow(
                            title = "关注主播开播提醒",
                            checked = settings.liveStartNotify,
                            onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(liveStartNotify = on) } },
                            supporting = "应用运行期间每分钟查一次，关注的主播开播时发系统通知；退出应用后不提醒",
                            icon = Icons.Filled.Notifications,
                            tone = MetricTone.Online,
                        )
                        BiliSettingsRow(
                            title = "退出登录",
                            supporting = "清除这台设备上的登录信息",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            tone = MetricTone.Like,
                            onClick = {
                                scope.launch {
                                    AppGraph.repo.logout()
                                    onLogout()
                                }
                            },
                        )
                    }
                }
            }
            1 -> {
                SettingsSectionHeading(SettingsSection.Sponsor, jumpTo, reached)
                SettingsCard {
                    BiliSwitchRow(
                        title = "跳过赞助片段",
                        checked = settings.sponsorBlock,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(sponsorBlock = on) } },
                        supporting = "播到被标注的片段时跳到末尾，可随时撤回。默认关闭",
                        icon = Icons.Filled.FastForward,
                        tone = MetricTone.Coin,
                    )
                    BiliSettingsLabel(
                        "跳过的类别",
                        supporting = "数据来自网友标注，没人标过的视频不会跳。至少保留一类。",
                    )
                    RowChips {
                        val selected = SponsorCategories.sanitize(settings.sponsorCategories).split(',').toSet()
                        SponsorCategories.all.forEach { (id, label) ->
                            BiliFilterChip(
                                selected = id in selected,
                                onClick = {
                                    val next = if (id in selected) selected - id else selected + id
                                    AppGraph.repo.updateSettings {
                                        it.copy(sponsorCategories = SponsorCategories.sanitize(next.joinToString(",")))
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                if (isDesktopHost()) {
                    SettingsSectionHeading(SettingsSection.Shortcuts, jumpTo, reached)
                    SettingsCard {
                        BiliSettingsLabel(
                            "播放与音量",
                            supporting = "空格 播放或暂停 · ← / → 快退或快进 · ↑ / ↓ 调音量 · M 静音或恢复音量",
                        )
                        BiliSettingsLabel(
                            "画面与弹幕",
                            supporting = "F 全屏 · Esc 退出 · G 清屏 · D 开关弹幕 · C 开关字幕",
                        )
                        BiliSettingsLabel(
                            "搜索与互动",
                            supporting = "Ctrl / ⌘ + K 打开搜索 · L 点赞 · U 打开 UP 主页",
                        )
                    }
                }
                SettingsSectionHeading(SettingsSection.Quality, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel("默认画面比例", modifier = anchor(SettingsItem.Aspect), supporting = "可在播放器更多选项中随时调整。")
                    RowChips {
                        app.ailiaili.core.model.PlayerScale.entries.forEach { mode ->
                            BiliFilterChip(
                                selected = settings.playerScale == mode,
                                onClick = { AppGraph.repo.updateSettings { it.copy(playerScale = mode) } },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    BiliSettingsLabel("快进和快退步长", modifier = anchor(SettingsItem.Seek), supporting = "适用于桌面左右方向键与手机两侧双击。")
                    RowChips {
                        listOf(0, 3, 5, 10, 15, 30, 60).forEach { seconds ->
                            BiliFilterChip(
                                selected = settings.seekStepSeconds == seconds,
                                onClick = { AppGraph.repo.updateSettings { it.copy(seekStepSeconds = seconds) } },
                                label = { Text(if (seconds == 0) "设备默认" else "$seconds 秒") },
                            )
                        }
                    }
                }
                SettingsCard {
                    BiliSettingsLabel("画质", modifier = anchor(SettingsItem.Quality))
                    RowChips {
                        qualities.forEach { qn ->
                            BiliFilterChip(
                                selected = settings.defaultQn == qn,
                                onClick = { AppGraph.repo.updateSettings { it.copy(defaultQn = qn) } },
                                label = { Text(qnLabel(qn)) },
                            )
                        }
                    }
                    BiliSwitchRow(
                        title = "免登录 1080P",
                        checked = settings.noLogin1080,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(noLogin1080 = on) } },
                        supporting = "未登录时附带试看参数，最高到 1080P",
                        icon = Icons.Filled.HighQuality,
                    )
                    BiliSwitchRow(
                        title = "显示相关视频",
                        checked = settings.showRelated,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(showRelated = on) } },
                        supporting = "在播放页简介下方展示相关推荐",
                        icon = Icons.AutoMirrored.Filled.ViewList,
                    )
                    BiliSwitchRow(
                        title = "自动连播",
                        checked = settings.autoPlayNext,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(autoPlayNext = on) } },
                        supporting = "续播下一 P、队列或合集。播完后有 5 秒可以取消",
                        icon = Icons.Filled.FastForward,
                        tone = MetricTone.Online,
                    )
                    if (settings.autoPlayNext) {
                        BiliSwitchRow(
                            title = "连播相关视频",
                            checked = settings.autoPlayRelated,
                            onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(autoPlayRelated = on) } },
                            supporting = "分 P、队列和合集播完后，接着播放最初视频的相关推荐",
                            icon = Icons.Filled.FastForward,
                        )
                    }
                    if (isDesktopHost()) {
                        BiliSwitchRow(
                            title = "视频硬件解码",
                            checked = settings.desktopHardwareDecoding,
                            onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(desktopHardwareDecoding = on) } },
                            supporting = "出现有声无画面时可以关闭，下次打开视频生效",
                            icon = Icons.Filled.HighQuality,
                        )
                        if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                            BiliSwitchRow(
                                title = "高质量音频重采样",
                                checked = settings.desktopHighQualityAudio,
                                onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(desktopHighQualityAudio = on) } },
                                supporting = "直连音响可尝试开启。蓝牙输出建议使用默认设置，下次打开播放器生效",
                                icon = Icons.Filled.HighQuality,
                            )
                        }
                    }
                    BiliSettingsLabel("网络线路")
                    RowChips {
                        listOf(CdnMode.Auto to "自动测速", CdnMode.Server to "跟随服务端", CdnMode.Manual to "指定线路").forEach { (mode, label) ->
                            BiliFilterChip(
                                selected = settings.cdnMode == mode,
                                onClick = {
                                    AppGraph.repo.updateSettings { it.copy(cdnMode = mode) }
                                    AppGraph.repo.invalidateCdn()
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    BiliSwitchRow(
                        title = "屏蔽 PCDN",
                        checked = settings.blockPcdn,
                        onCheckedChange = { on ->
                            AppGraph.repo.updateSettings { it.copy(blockPcdn = on) }
                            AppGraph.repo.invalidateCdn()
                        },
                        supporting = "不走点对点分发。关掉后可能用到 mcdn 线路",
                        icon = Icons.Filled.Block,
                        tone = MetricTone.Danmaku,
                    )
                    val cdnReport by AppGraph.repo.cdnReport.collectAsState()
                    BiliSettingsNote(cdnReport)
                    if (settings.cdnMode == CdnMode.Manual) {
                        RowChips {
                            CdnLine.all.forEach { line ->
                                BiliFilterChip(
                                    selected = settings.cdnManualId == line.id,
                                    onClick = {
                                        AppGraph.repo.updateSettings { it.copy(cdnManualId = line.id) }
                                        AppGraph.repo.invalidateCdn()
                                    },
                                    label = { Text(line.name) },
                                )
                            }
                        }
                    }
                    var uaDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }
                    LaunchedEffect(uaDraft) {
                        if (uaDraft == settings.customUserAgent) return@LaunchedEffect
                        keywordSaveJob?.cancel()
                        val draft = uaDraft
                        keywordSaveJob = keywordSaveScope.launch {
                            delay(500)
                            AppGraph.repo.updateSettings { it.copy(customUserAgent = draft.trim()) }
                        }
                    }
                    OutlinedTextField(
                        value = uaDraft,
                        onValueChange = { uaDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("自定义 User-Agent（高级，可留空）") },
                        placeholder = { Text("默认浏览器 UA；代理/校园网频繁风控时可换非浏览器 UA") },
                        singleLine = true,
                    )
                    BiliSettingsNote("UA 影响取流协商，修改后重新播放生效；不确定就留空。")
                    var proxyDraft by remember(settings.httpProxy) { mutableStateOf(settings.httpProxy) }
                    val proxyError = remember(proxyDraft) {
                        if (proxyDraft.isBlank()) null
                        else runCatching { app.ailiaili.core.model.parseProxyAddress(proxyDraft) }
                            .exceptionOrNull()?.message
                    }
                    LaunchedEffect(proxyDraft) {
                        if (proxyDraft == settings.httpProxy) return@LaunchedEffect
                        keywordSaveJob?.cancel()
                        val draft = proxyDraft
                        keywordSaveJob = keywordSaveScope.launch {
                            delay(500)
                            AppGraph.repo.updateSettings { it.copy(httpProxy = draft.trim()) }
                        }
                    }
                    OutlinedTextField(
                        value = proxyDraft,
                        onValueChange = { proxyDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("HTTP 代理（高级，可留空）") },
                        placeholder = { Text("例如 127.0.0.1:7890；留空直连") },
                        singleLine = true,
                        isError = proxyError != null,
                        supportingText = proxyError?.let { { Text(it) } },
                    )
                    BiliSettingsNote("代理作用于接口、图片、下载与直播弹幕，下一次请求即生效；视频流走系统播放器，不受此代理影响。")
                }
                SettingsSectionHeading(SettingsSection.Buffer, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel(
                        "缓冲策略",
                        supporting = if (settings.bufferAuto) {
                            "按最近一次 CDN 测速的带宽自动挑预读深度：弱网更深，快线更省内存。下一条视频生效。"
                        } else {
                            "手动指定网络缓冲，数值越大越抗卡顿，但起播更慢。"
                        },
                    )
                    RowChips {
                        listOf(true to "自动", false to "手动").forEach { (auto, label) ->
                            BiliFilterChip(
                                selected = settings.bufferAuto == auto,
                                onClick = { AppGraph.repo.updateSettings { it.copy(bufferAuto = auto) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (!settings.bufferAuto) {
                        BiliSettingsLabel("网络缓冲 ${settings.networkCachingMs}ms")
                        RowChips {
                            listOf(300, 500, 1000, 2000, 3000).forEach { ms ->
                                BiliFilterChip(
                                    selected = settings.networkCachingMs == ms,
                                    onClick = { AppGraph.repo.updateSettings { it.copy(networkCachingMs = ms) } },
                                    label = { Text("${ms}ms") },
                                )
                            }
                        }
                    }
                    BiliSettingsNote("双端生效：桌面映射 VLC 网络缓冲，Android 映射 Media3 缓冲参数。")
                }
                SettingsSectionHeading(SettingsSection.Subtitle, jumpTo, reached)
                SettingsCard {
                    BiliSettingsNote("字幕开关、语言与延迟在播放器字幕菜单里调；这里管外观。")
                    var subFont by remember(settings.subtitleFontSp) { mutableFloatStateOf(settings.subtitleFontSp.toFloat()) }
                    BiliSettingsLabel("字幕字号 ${subFont.toInt()}sp", modifier = anchor(SettingsItem.SubtitleSize))
                    Slider(
                        value = subFont,
                        onValueChange = { subFont = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(subtitleFontSp = subFont.toInt()) }
                        },
                        valueRange = 12f..36f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    var subBottom by remember(settings.subtitleBottomPercent) { mutableFloatStateOf(settings.subtitleBottomPercent.toFloat()) }
                    BiliSettingsLabel("距底部高度 ${subBottom.toInt()}%")
                    Slider(
                        value = subBottom,
                        onValueChange = { subBottom = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(subtitleBottomPercent = subBottom.toInt()) }
                        },
                        valueRange = 0f..40f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                SettingsSectionHeading(SettingsSection.Danmaku, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel("弹幕时间偏移", modifier = anchor(SettingsItem.DanmakuTiming), supporting = "随视频播放的弹幕可提前或延后，直播使用实时弹幕。")
                    app.ailiaili.core.designsystem.DanmakuDelayControl(
                        settings.danmakuDelayMs,
                        onChange = { value -> AppGraph.repo.updateSettings { it.copy(danmakuDelayMs = value) } },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                SettingsCard {
                    BiliSettingsLabel("显示与过滤")
                    BiliSwitchRow(
                        title = "显示弹幕",
                        checked = settings.danmakuEnabled,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(danmakuEnabled = on) } },
                        icon = Icons.AutoMirrored.Filled.Comment,
                        tone = MetricTone.Danmaku,
                    )
                    BiliSwitchRow(
                        title = "智能屏蔽",
                        checked = settings.danmakuSmartFilter,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(danmakuSmartFilter = on) } },
                        supporting = "过滤超长和刷屏弹幕",
                        icon = Icons.Filled.AutoFixHigh,
                        tone = MetricTone.Online,
                    )
                    BiliSettingsLabel(
                        "按类型过滤",
                        supporting = if (
                            settings.danmakuFilterScroll && settings.danmakuFilterFixed &&
                            settings.danmakuFilterColorful && settings.danmakuFilterAdvanced
                        ) {
                            "当前显示全部类型；关掉某一类即可把它从画面里去掉"
                        } else {
                            "只显示已选中的类型"
                        },
                    )
                    RowChips {
                        BiliFilterChip(
                            selected = settings.danmakuFilterScroll,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterScroll = !it.danmakuFilterScroll) } },
                            label = { Text("滚动") },
                        )
                        BiliFilterChip(
                            selected = settings.danmakuFilterFixed,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterFixed = !it.danmakuFilterFixed) } },
                            label = { Text("固定") },
                        )
                        BiliFilterChip(
                            selected = settings.danmakuFilterColorful,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterColorful = !it.danmakuFilterColorful) } },
                            label = { Text("彩色") },
                        )
                        BiliFilterChip(
                            selected = settings.danmakuFilterAdvanced,
                            onClick = { AppGraph.repo.updateSettings { it.copy(danmakuFilterAdvanced = !it.danmakuFilterAdvanced) } },
                            label = { Text("高级") },
                        )
                    }
                    var keywordDraft by remember(settings.danmakuBlockKeywords) {
                        mutableStateOf(settings.danmakuBlockKeywords)
                    }
                    LaunchedEffect(keywordDraft) {
                        if (keywordDraft == settings.danmakuBlockKeywords) return@LaunchedEffect
                        // 防抖落盘放独立作用域：挂在页面组合上的话，输入后 500ms 内
                        // 返回/切页会取消协程，最后编辑静默丢失。
                        keywordSaveJob?.cancel()
                        val draft = keywordDraft
                        keywordSaveJob = keywordSaveScope.launch {
                            delay(500)
                            AppGraph.repo.updateSettings { it.copy(danmakuBlockKeywords = draft) }
                        }
                    }
                    OutlinedTextField(
                        value = keywordDraft,
                        onValueChange = { keywordDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        label = { Text("弹幕关键词") },
                        placeholder = { Text("逗号、分号或换行分隔，re: 开头是正则") },
                        minLines = 2,
                    )
                    BiliSettingsLabel("外观")
                    BiliSwitchRow(
                        title = "彩色弹幕",
                        checked = settings.danmakuColorful,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(danmakuColorful = on) } },
                        icon = Icons.Filled.Palette,
                        tone = MetricTone.Like,
                    )
                    BiliSwitchRow(
                        title = "胶囊底衬",
                        checked = settings.danmakuPillBackground,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(danmakuPillBackground = on) } },
                        supporting = "半透明圆角背景，亮画面上也能看清白字",
                        icon = Icons.Filled.ClosedCaption,
                        tone = MetricTone.Danmaku,
                    )
                    BiliSettingsLabel("弹幕显示区域 ${area.toInt()}%")
                    Slider(
                        value = area,
                        onValueChange = { area = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(danmakuAreaPercent = area.toInt()) }
                        },
                        valueRange = 25f..100f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    var opacity by remember(settings.danmakuOpacityPercent) {
                        mutableFloatStateOf(settings.danmakuOpacityPercent.toFloat())
                    }
                    BiliSettingsLabel("弹幕不透明度 ${opacity.toInt()}%", modifier = anchor(SettingsItem.DanmakuOpacity))
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(danmakuOpacityPercent = opacity.toInt()) }
                        },
                        valueRange = 30f..100f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    var maxOnScreen by remember(settings.danmakuMaxOnScreen) {
                        mutableFloatStateOf(settings.danmakuMaxOnScreen.toFloat())
                    }
                    BiliSettingsLabel(
                        "同屏弹幕上限 ${maxOnScreen.toInt()} 条",
                        supporting = "弹幕密集时限制同屏条数，防挡画面；过滤后计数，调小立即生效",
                    )
                    Slider(
                        value = maxOnScreen,
                        onValueChange = { maxOnScreen = it },
                        onValueChangeFinished = {
                            AppGraph.repo.updateSettings { it.copy(danmakuMaxOnScreen = maxOnScreen.toInt()) }
                        },
                        valueRange = 10f..80f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            2 -> {
                SettingsSectionHeading(SettingsSection.Data, jumpTo, reached)
                SettingsCard {
                    BiliSettingsLabel("默认下载画质", modifier = anchor(SettingsItem.DownloadQuality), supporting = "添加任务时可改选，保存的规格以实际可用画质为准。")
                    RowChips {
                        (listOf(0) + qualities).forEach { qn ->
                            BiliFilterChip(
                                selected = settings.downloadQuality == qn,
                                onClick = { AppGraph.repo.updateSettings { it.copy(downloadQuality = qn) } },
                                label = { Text(if (qn == 0) "当前播放画质" else app.ailiaili.core.model.qnLabel(qn)) },
                            )
                        }
                    }
                    if (!isDesktopHost()) {
                        BiliSwitchRow(
                            title = "在后台继续下载",
                            supporting = "下载时在系统通知中显示进度，也可以暂停全部。关闭应用后可重新打开继续任务。",
                            checked = settings.backgroundDownloads,
                            onCheckedChange = { AppGraph.repo.updateSettings { value -> value.copy(backgroundDownloads = it) } },
                        )
                        BiliSwitchRow(
                            title = "仅在非计费网络下载",
                            modifier = anchor(SettingsItem.DownloadNetwork),
                            supporting = "切到移动数据等计费网络时等待，连接合适的网络后继续。",
                            checked = settings.unmeteredDownloads,
                            onCheckedChange = { AppGraph.repo.updateSettings { value -> value.copy(unmeteredDownloads = it) } },
                        )
                    }
                    BiliSwitchRow(
                        title = "同时缓存字幕",
                        modifier = anchor(SettingsItem.DownloadSubtitles),
                        supporting = "保存当前视频可用的字幕轨道。",
                        checked = settings.downloadSubtitles,
                        onCheckedChange = { AppGraph.repo.updateSettings { value -> value.copy(downloadSubtitles = it) } },
                    )
                    BiliSwitchRow(
                        title = "同时缓存弹幕",
                        modifier = anchor(SettingsItem.DownloadDanmaku),
                        supporting = "保存已获取的点播弹幕，离线播放时可打开。",
                        checked = settings.downloadDanmaku,
                        onCheckedChange = { AppGraph.repo.updateSettings { value -> value.copy(downloadDanmaku = it) } },
                    )
                }
                SettingsCard {
                    var cacheCount by remember { mutableStateOf<Int?>(null) }
                    var coverCache by remember { mutableStateOf("计算中…") }
                    LaunchedEffect(downloadsRevision, settings.downloadDir) {
                        coverCache = withContext(ioDispatcher) { coverCacheSize() }
                        cacheCount = withContext(ioDispatcher) { AppGraph.cache.list().size }
                    }
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        MetricChip(
                            MetricSpec(
                                Icons.Filled.Download,
                                cacheCount?.toString() ?: "…",
                                "已下载",
                                MetricTone.Neutral,
                            ),
                            size = MetricChipSize.Compact,
                        )
                        MetricChip(
                            MetricSpec(
                                Icons.Filled.Photo,
                                coverCache,
                                "封面缓存",
                                MetricTone.Favorite,
                            ),
                            size = MetricChipSize.Compact,
                        )
                    }
                    BiliSettingsRow(
                        title = "导出设置",
                        supporting = if (isDesktopHost()) {
                            "备份为 JSON 文件（不含登录凭据），换机可导入"
                        } else {
                            "写入备份文件并拉起分享，可发到网盘/聊天保存"
                        },
                        icon = Icons.Filled.Upload,
                        tone = MetricTone.Online,
                        onClick = {
                            scope.launch {
                                val json = AppGraph.repo.exportSettingsJson()
                                val path = saveTextFile(SETTINGS_BACKUP_NAME, json)
                                app.ailiaili.core.designsystem.QuickActions.showToast(
                                    if (path != null) "设置已导出" else "导出失败",
                                )
                            }
                        },
                    )
                    BiliSettingsRow(
                        title = "导入设置",
                        supporting = if (isDesktopHost()) {
                            "从备份 JSON 恢复全部设置（不含登录凭据）"
                        } else {
                            "读取应用存储里的 $SETTINGS_BACKUP_NAME（先把备份放到该位置）"
                        },
                        icon = Icons.Filled.Download,
                        tone = MetricTone.Favorite,
                        onClick = {
                            scope.launch {
                                val text = readTextFile()
                                if (text == null) {
                                    app.ailiaili.core.designsystem.QuickActions.showToast("没有读到备份文件")
                                    return@launch
                                }
                                AppGraph.repo.importSettingsJson(text)
                                    .onSuccess { app.ailiaili.core.designsystem.QuickActions.showToast("设置已导入") }
                                    .onFailure {
                                        app.ailiaili.core.designsystem.QuickActions.showToast(it.message ?: "导入失败")
                                    }
                            }
                        },
                    )
                    BiliSettingsRow(
                        title = "已下载的视频",
                        supporting = "缓存在这台设备上，不上传账号",
                        icon = Icons.Filled.Download,
                        tone = MetricTone.Online,
                        onClick = onOpenCache,
                    )
                    BiliSettingsRow(
                        title = "下载保存位置",
                        modifier = anchor(SettingsItem.DownloadFolder),
                        supporting = when {
                            settings.downloadDir.startsWith("content://") ->
                                "已选导出目录：下载完成后自动复制视频文件过去，应用内仍保留缓存"
                            settings.downloadDir.isNotBlank() -> settings.downloadDir
                            isDesktopHost() -> "应用缓存（默认）"
                            else -> "应用缓存（默认）；可选一个目录，下载完成后自动复制过去"
                        },
                        icon = Icons.Filled.Folder,
                        tone = MetricTone.Favorite,
                        onClick = {
                            scope.launch {
                                val dir = chooseDirectory() ?: return@launch
                                AppGraph.repo.updateSettings { it.copy(downloadDir = dir) }
                                AppGraph.cache.setExtraRoot(dir)
                                cacheCount = withContext(ioDispatcher) { AppGraph.cache.list().size }
                            }
                        },
                        trailing = if (settings.downloadDir.isNotBlank()) {
                            {
                                Text(
                                    "重置",
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            AppGraph.repo.updateSettings { it.copy(downloadDir = "") }
                                            AppGraph.cache.setExtraRoot("")
                                            cacheCount = withContext(ioDispatcher) { AppGraph.cache.list().size }
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        } else {
                            null
                        },
                    )
                    BiliSettingsRow(
                        title = "封面缓存",
                        supporting = coverCache,
                        icon = Icons.Filled.Photo,
                        tone = MetricTone.Favorite,
                        onClick = {
                            scope.launch {
                                withContext(ioDispatcher) { clearCoverCache(platformContext) }
                                coverCache = withContext(ioDispatcher) { coverCacheSize() }
                            }
                        },
                        trailing = {
                            Text(
                                "清除",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                    BiliSettingsNote("短视频由播放器直接合流，不另存一份缓存。要腾出空间，清上面的封面，或打开已下载的视频删掉离线文件。")
                }
            }
            else -> AboutSettings(
                autoCheckUpdate = settings.autoCheckUpdate,
                onAutoCheckUpdateChange = { enabled ->
                    AppGraph.repo.updateSettings { it.copy(autoCheckUpdate = enabled) }
                },
                checkingUpdate = checkingUpdate,
                updateResult = updateResult,
                onCheckUpdate = {
                    if (!checkingUpdate) {
                        checkingUpdate = true
                        scope.launch {
                            try {
                                updateResult = latestAppRelease()
                            } finally {
                                checkingUpdate = false
                            }
                        }
                    }
                },
                onOpenAuthor = onOpenAuthor,
                onOpenDisclaimer = onOpenDisclaimer,
            )
        }
                        }
                    }
                }
            }
            AliStageTabs(
                labels = settingsTabs,
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth()
                    .padding(horizontal = 10.dp).onSizeChanged { tabsHeight = it.height },
                floating = true,
            )
        }
    }
    if (settingsSearch) {
        SettingsSectionSearch(
            desktop = isDesktopHost(),
            onSelect = { target ->
                settingsSearch = false
                tab = target.section.tab
                jumpTo = target.takeUnless { it.section == SettingsSection.About }
            },
            onDismiss = { settingsSearch = false },
        )
    }
}


@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    AliStagePanel(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowChips(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun FontPickerSection(currentPath: String, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var systemFonts by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSystem by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val selectedPath = remember(currentPath) {
        currentPath.takeIf { it.isBlank() || it == SystemUiFontPath || loadFontFamily(it) != null }.orEmpty()
    }
    val currentName = when {
        selectedPath.isBlank() -> "MiSans"
        selectedPath == SystemUiFontPath -> "系统默认"
        else -> java.io.File(selectedPath).name
    }

    fun useFontFile(name: String, bytes: ByteArray) {
        scope.launch {
            busy = true
            val result = withContext(ioDispatcher) {
                runCatching {
                    val dir = app.ailiaili.core.data.appFontDir()
                    val safe = name.replace(Regex("""[\\/:*?"<>|]"""), "_")
                    val target = AppFile(dir, safe)
                    target.writeBytes(bytes)
                    target.absolutePath
                }
            }
            result.onSuccess { path ->
                // 先验证能加载，再写设置；坏字体不让整个界面变成豆腐块。
                if (loadFontFamily(path) != null) {
                    AppGraph.repo.updateSettings { it.copy(fontPath = path) }
                    note = null
                } else {
                    note = "这个字体文件加载失败，请换一个试试"
                }
            }.onFailure { note = it.message ?: "字体导入失败" }
            busy = false
        }
    }

    BiliSettingsLabel(
        "界面字体",
        modifier = modifier,
        supporting = "当前使用 $currentName，弹幕也会跟随。",
    )
    RowChips {
        BiliFilterChip(
            selected = selectedPath.isBlank(),
            onClick = { AppGraph.repo.updateSettings { it.copy(fontPath = "") } },
            label = { Text("MiSans") },
        )
        BiliFilterChip(
            selected = selectedPath == SystemUiFontPath,
            onClick = { AppGraph.repo.updateSettings { it.copy(fontPath = SystemUiFontPath) } },
            label = { Text("系统默认") },
        )
        if (isDesktopHost()) {
            BiliFilterChip(
                selected = showSystem,
                onClick = {
                    showSystem = !showSystem
                    if (showSystem && systemFonts.isEmpty()) {
                        scope.launch {
                            systemFonts = withContext(ioDispatcher) { app.ailiaili.core.data.systemFontFiles() }
                        }
                    }
                },
                label = { Text("选择系统字体") },
            )
        }
        BiliFilterChip(
            selected = false,
            onClick = {
                if (!busy) scope.launch {
                    busy = true
                    val picked = runCatching { app.ailiaili.core.data.pickFontFile() }.getOrNull()
                    busy = false
                    if (picked != null) useFontFile(picked.name, picked.bytes)
                }
            },
            label = { Text(if (busy) "处理中…" else "导入字体文件") },
        )
    }
    note?.let {
        BiliSettingsNote(it)
    }
    if (showSystem && systemFonts.isNotEmpty()) {
        var filter by remember { mutableStateOf("") }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("搜字体名，如 PingFang / 思源 / Microsoft YaHei") },
        )
        val shown = systemFonts.filter {
            filter.isBlank() || java.io.File(it).name.contains(filter, ignoreCase = true)
        }.take(24)
        RowChips {
            shown.forEach { path ->
                val file = java.io.File(path)
                BiliFilterChip(
                    selected = selectedPath == path || java.io.File(selectedPath).name == file.name,
                    onClick = {
                        runCatching { useFontFile(file.name, file.readBytes()) }
                    },
                    label = { Text(file.nameWithoutExtension) },
                )
            }
        }
        if (shown.isEmpty()) {
            BiliSettingsNote("没有匹配的字体。")
        }
    }
}

private fun coverCacheSize(): String {
    val bytes = imageCacheDirs().sumOf { dir ->
        if (!dir.exists()) 0L else dir.walkFiles().sumOf { it.length() }
    }
    return when {
        bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0)).let { v -> (kotlin.math.round(v * 10) / 10.0) }} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

private fun clearCoverCache(context: coil3.PlatformContext) {
    // 先清运行中的内存/磁盘缓存句柄，再删目录；只删目录会让 Coil 握着失效句柄继续写。
    runCatching {
        val loader = coil3.SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }
    imageCacheDirs().forEach { it.deleteRecursively() }
}
