package app.ailiaili.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.data.AppFile
import app.ailiaili.core.data.canFollowSystemAccent
import app.ailiaili.core.data.hideAppWindow
import app.ailiaili.core.data.imageCacheDirs
import app.ailiaili.core.data.chooseDirectory
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.data.saveTextFile
import app.ailiaili.core.data.readTextFile
import app.ailiaili.core.data.SETTINGS_BACKUP_NAME
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.data.traySupported
import app.ailiaili.core.model.SponsorCategories
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import app.ailiaili.core.designsystem.BiliSettingsLabel
import app.ailiaili.core.designsystem.BiliSettingsNote
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.designsystem.BiliSwitchRow
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.BiliPillTabs
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.LocalWheelSpeed
import app.ailiaili.core.designsystem.smoothWheelScroll
import app.ailiaili.core.designsystem.BiliSurfaceCard
import app.ailiaili.core.designsystem.MetricChip
import app.ailiaili.core.designsystem.MetricChipSize
import app.ailiaili.core.designsystem.MetricSpec
import app.ailiaili.core.designsystem.MetricTone
import app.ailiaili.core.designsystem.shellBottomPadding
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
import app.ailiaili.core.network.JSONObject
import app.ailiaili.core.network.SharedHttp
import app.ailiaili.core.network.get

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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val platformContext = coil3.compose.LocalPlatformContext.current
    val pageScroll = rememberScrollState()
    var updateText by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var area by remember(settings.danmakuAreaPercent) { mutableFloatStateOf(settings.danmakuAreaPercent.toFloat()) }
    Column(
        Modifier
            .fillMaxSize()
            .shellBottomPadding()
            .smoothWheelScroll(pageScroll, LocalWheelSpeed.current)
            .verticalScroll(pageScroll)
            .padding(bottom = 24.dp),
    ) {
        BiliPageHeader("应用设置")
        Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            BiliPillTabs(settingsTabs, tab, onSelect = { tab = it })
        }
        when (tab) {
            0 -> {
                BiliSectionTitle("交互", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    var wheelSpeed by remember(settings.wheelSpeedPercent) {
                        mutableFloatStateOf(settings.wheelSpeedPercent.toFloat())
                    }
                    BiliSettingsLabel(
                        "滚轮速度 ${wheelSpeed.toInt()}%",
                        supporting = "仅桌面端生效。默认 250%，数值越大滚得越快。",
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
                BiliSectionTitle("内容", Modifier.padding(horizontal = 20.dp))
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
                BiliSectionTitle("外观", Modifier.padding(horizontal = 20.dp))
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
                    BiliSettingsLabel("应用主题")
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
                    BiliSettingsLabel(
                        "主题色",
                        supporting = if (canFollowSystemAccent()) {
                            "默认是 B 站粉。跟随系统用设备强调色，也可以自己填一个 6 位色值。"
                        } else {
                            "默认是 B 站粉。也可以自己填一个 6 位色值。"
                        },
                    )
                    RowChips {
                        if (canFollowSystemAccent()) {
                            BiliFilterChip(
                                selected = settings.accentFollowSystem,
                                onClick = { AppGraph.repo.updateSettings { it.copy(accentFollowSystem = !it.accentFollowSystem) } },
                                label = { Text("跟随系统") },
                            )
                        }
                        listOf("" to "默认粉", "3B82F6" to "蓝", "10B981" to "绿", "F59E0B" to "琥珀").forEach { (hex, label) ->
                            BiliFilterChip(
                                selected = !settings.accentFollowSystem && settings.accentHex.equals(hex, ignoreCase = true),
                                onClick = {
                                    AppGraph.repo.updateSettings { it.copy(accentFollowSystem = false, accentHex = hex) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    var accentDraft by remember(settings.accentHex) { mutableStateOf(settings.accentHex) }
                    OutlinedTextField(
                        value = accentDraft,
                        onValueChange = { accentDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        singleLine = true,
                        label = { Text("自定义色值，例如 FB7299") },
                        trailingIcon = {
                            TextButton(onClick = {
                                AppGraph.repo.updateSettings {
                                    it.copy(accentFollowSystem = false, accentHex = accentDraft.trim().removePrefix("#"))
                                }
                            }) { Text("使用") }
                        },
                    )
                    FontPickerSection(settings.fontPath)
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
                BiliSectionTitle("赞助片段", Modifier.padding(horizontal = 20.dp))
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
                    BiliSectionTitle("电脑快捷键", Modifier.padding(horizontal = 20.dp))
                    SettingsCard {
                        Text(
                            "空格播放或暂停。左右方向键快进快退 5 秒，上下方向键调音量。F 全屏，D 开关弹幕，L 点赞，U 打开 UP 主页，Esc 退出全屏。滚轮调音量，双击画面全屏。",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                BiliSectionTitle("画质与线路", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    BiliSettingsLabel("画质")
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
                        supporting = "播放页简介下方的「相关推荐」。关掉后不请求也不展示",
                        icon = Icons.AutoMirrored.Filled.ViewList,
                    )
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
                }
                BiliSectionTitle("缓冲", Modifier.padding(horizontal = 20.dp))
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
                BiliSectionTitle("字幕", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    BiliSettingsNote("字幕开关、语言与延迟在播放器字幕菜单里调；这里管外观。")
                    var subFont by remember(settings.subtitleFontSp) { mutableFloatStateOf(settings.subtitleFontSp.toFloat()) }
                    BiliSettingsLabel("字幕字号 ${subFont.toInt()}sp")
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
                BiliSectionTitle("弹幕", Modifier.padding(horizontal = 20.dp))
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
                }
            }
            2 -> {
                BiliSectionTitle("离线缓存", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    var cacheCount by remember { mutableStateOf<Int?>(null) }
                    var coverCache by remember { mutableStateOf("计算中…") }
                    LaunchedEffect(Unit) {
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
                                MetricTone.Accent,
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
                        supporting = if (isDesktopHost()) {
                            settings.downloadDir.ifBlank { "应用缓存（默认）" }
                        } else {
                            "Android 固定保存到应用缓存"
                        },
                        icon = Icons.Filled.Folder,
                        tone = MetricTone.Favorite,
                        onClick = if (isDesktopHost()) {
                            {
                                scope.launch {
                                    val dir = chooseDirectory() ?: return@launch
                                    AppGraph.repo.updateSettings { it.copy(downloadDir = dir) }
                                    AppGraph.cache.setExtraRoot(dir)
                                    cacheCount = withContext(ioDispatcher) { AppGraph.cache.list().size }
                                }
                            }
                        } else {
                            null
                        },
                        trailing = if (isDesktopHost() && settings.downloadDir.isNotBlank()) {
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
            else -> {
                BiliSectionTitle("关于", Modifier.padding(horizontal = 20.dp))
                SettingsCard {
                    BiliSettingsRow(
                        title = "ailiaili",
                        supporting = "版本 1.3.0 · Android、Windows、macOS、Linux 共用这一套界面",
                        icon = Icons.Filled.Apps,
                    )
                    BiliSwitchRow(
                        title = "启动时检查更新",
                        checked = settings.autoCheckUpdate,
                        onCheckedChange = { on -> AppGraph.repo.updateSettings { it.copy(autoCheckUpdate = on) } },
                        supporting = "打开应用大约 6 秒后查一次。关掉后仍可手动检查",
                        icon = Icons.Filled.SystemUpdate,
                        tone = MetricTone.Online,
                    )
                    BiliSettingsRow(
                        title = "检查更新",
                        supporting = updateText ?: "当前版本 1.3.0",
                        icon = Icons.Filled.Refresh,
                        tone = MetricTone.Danmaku,
                        onClick = {
                            scope.launch {
                                updateText = "正在检查…"
                                updateText = checkUpdate()
                            }
                        },
                        trailing = {
                            Text(
                                "检查",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                    updateText?.lineSequence()?.firstOrNull { it.startsWith("http") }?.let { page ->
                        BiliSettingsRow(
                            title = "打开发布页",
                            supporting = page,
                            icon = Icons.Filled.OpenInNew,
                            tone = MetricTone.Accent,
                            onClick = { openUrl(page) },
                        )
                    }
                    BiliSettingsRow(
                        title = "复制诊断信息",
                        supporting = "反馈播放/风控问题时贴上它（已脱敏，不含凭据）",
                        icon = Icons.Filled.Info,
                        tone = MetricTone.Neutral,
                        onClick = {
                            val text = app.ailiaili.core.data.Diagnostics.build("1.3.0")
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                            app.ailiaili.core.designsystem.QuickActions.showToast("诊断信息已复制到剪贴板")
                        },
                    )
                    BiliSettingsRow(
                        title = "原作者 ABing",
                        supporting = "打开 B 站主页",
                        icon = Icons.Filled.Person,
                        tone = MetricTone.Like,
                        onClick = onOpenAuthor,
                    )
                    BiliSettingsRow(
                        title = "项目维护 shiaho777",
                        supporting = "github.com/shiaho777/ailiaili",
                        icon = Icons.Filled.Code,
                        tone = MetricTone.Neutral,
                        onClick = { openUrl("https://github.com/shiaho777/ailiaili") },
                    )
                    BiliSettingsRow(
                        title = "非官方声明",
                        supporting = "首次启动时已确认，可以再看一遍",
                        icon = Icons.Filled.Info,
                        tone = MetricTone.Coin,
                        onClick = onOpenDisclaimer,
                    )
                    BiliSettingsNote("非官方客户端。视频版权归哔哩哔哩所有。")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    BiliSurfaceCard(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
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

/**
 * 界面字体：默认 / 系统字体（桌面）/ 导入字体文件。
 * 选中的字体复制进应用字体目录，设置只存路径；弹幕跟随同一字体。
 */
@Composable
private fun FontPickerSection(currentPath: String) {
    val scope = rememberCoroutineScope()
    var systemFonts by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSystem by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val currentName = if (currentPath.isBlank()) "默认" else java.io.File(currentPath).name

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
                if (app.ailiaili.core.designsystem.loadFontFamily(path) != null) {
                    AppGraph.repo.updateSettings { it.copy(fontPath = path) }
                    note = null
                } else {
                    note = "这个字体文件加载失败，换一个是试试"
                }
            }.onFailure { note = it.message ?: "字体导入失败" }
            busy = false
        }
    }

    BiliSettingsLabel(
        "界面字体",
        supporting = "当前：$currentName。全应用与弹幕跟随；导入 ttf/otf 文件。",
    )
    RowChips {
        BiliFilterChip(
            selected = currentPath.isBlank(),
            onClick = { AppGraph.repo.updateSettings { it.copy(fontPath = "") } },
            label = { Text("默认") },
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
                label = { Text("系统字体") },
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
                val name = java.io.File(path).nameWithoutExtension
                BiliFilterChip(
                    selected = currentPath == path || java.io.File(currentPath).name == java.io.File(path).name,
                    onClick = {
                        val file = java.io.File(path)
                        runCatching { useFontFile(file.name, file.readBytes()) }
                    },
                    label = { Text(name) },
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

private suspend fun checkUpdate(): String = withContext(ioDispatcher) {
    runCatching {
        val json = JSONObject(
            SharedHttp.api.get(
                "https://api.github.com/repos/shiaho777/ailiaili/releases/latest",
                mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to "ailiaili",
                ),
            ).bodyText,
        )
        val tag = json.optString("tag_name")
        val page = json.optString("html_url")
        if (tag.isBlank()) "没有读到版本信息" else "最新发布 $tag\n$page"
    }.getOrElse { "检查失败：${it.message}" }
}
