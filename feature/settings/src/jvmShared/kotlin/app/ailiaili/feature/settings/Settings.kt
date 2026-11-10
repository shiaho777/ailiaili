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
import app.ailiaili.core.data.canFollowSystemAccent
import app.ailiaili.core.data.hideAppWindow
import app.ailiaili.core.data.imageCacheDirs
import app.ailiaili.core.data.isDesktopHost
import app.ailiaili.core.data.openUrl
import app.ailiaili.core.data.traySupported
import app.ailiaili.core.model.SponsorCategories
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import app.ailiaili.core.designsystem.BiliSettingsLabel
import app.ailiaili.core.designsystem.BiliSettingsNote
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.designsystem.BiliSwitchRow
import app.ailiaili.core.designsystem.BiliPageHeader
import app.ailiaili.core.designsystem.BiliPillTabs
import app.ailiaili.core.designsystem.BiliSectionTitle
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
    var updateText by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var area by remember(settings.danmakuAreaPercent) { mutableFloatStateOf(settings.danmakuAreaPercent.toFloat()) }
    Column(
        Modifier
            .fillMaxSize()
            .shellBottomPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        BiliPageHeader("应用设置")
        Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            BiliPillTabs(settingsTabs, tab, onSelect = { tab = it })
        }
        when (tab) {
            0 -> {
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
                    BiliSettingsNote("桌面端生效；Android 由 Media3 自行管理缓冲。")
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
                        delay(500)
                        AppGraph.repo.updateSettings { it.copy(danmakuBlockKeywords = keywordDraft) }
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
                        title = "已下载的视频",
                        supporting = "缓存在这台设备上，不上传账号",
                        icon = Icons.Filled.Download,
                        tone = MetricTone.Online,
                        onClick = onOpenCache,
                    )
                    BiliSettingsRow(
                        title = "封面缓存",
                        supporting = coverCache,
                        icon = Icons.Filled.Photo,
                        tone = MetricTone.Favorite,
                        onClick = {
                            scope.launch {
                                withContext(ioDispatcher) { clearCoverCache() }
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

private fun clearCoverCache() {
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
