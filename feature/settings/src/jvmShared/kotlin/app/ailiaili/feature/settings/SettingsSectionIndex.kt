package app.ailiaili.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.ailiaili.core.designsystem.AliStageDialog
import app.ailiaili.core.designsystem.BiliSearchField
import app.ailiaili.core.designsystem.BiliSectionTitle
import app.ailiaili.core.designsystem.BiliSettingsRow
import app.ailiaili.core.designsystem.LocalWindowForm
import kotlinx.coroutines.delay

internal enum class SettingsSection(val tab: Int, val title: String, val terms: String) {
    Interaction(0, "交互", "滚轮 速度 鼠标"),
    Content(0, "内容", "启动页 启动页面 首页 推荐 收藏显示"),
    Appearance(0, "外观", "字体 MiSans 主题色 深色 导航 标签 动效 快捷键"),
    Sponsor(1, "赞助片段", "SponsorBlock 跳过 广告"),
    Shortcuts(1, "电脑快捷键", "键盘 空格 全屏"),
    Quality(1, "画质与线路", "1080P 4K 代理 CDN 倍速 连播 快进 快退 快进步长 快进和快退步长 跳转 默认画面比例 裁切 拉伸"),
    Buffer(1, "缓冲", "带宽 网络 内存 缓存"),
    Subtitle(1, "字幕", "CC 字号 延迟"),
    Danmaku(1, "弹幕", "屏蔽 透明度 字号 偏移 时间 弹幕时间偏移"),
    Data(2, "离线缓存", "下载 清晰度 下载画质 默认下载画质 保存 下载保存位置 目录 存储 导入 导出 备份 封面 后台下载 在后台继续下载 仅在非计费网络下载 同时缓存字幕 同时缓存弹幕"),
    About(3, "关于", "版本 更新 许可 诊断 反馈"),
}

internal enum class SettingsItem(
    val section: SettingsSection,
    val title: String,
    val terms: String,
    val androidOnly: Boolean = false,
) {
    Theme(SettingsSection.Appearance, "应用主题", "明暗 深色 浅色 跟随系统"),
    ThemeColor(SettingsSection.Appearance, "主题色", "配色 亮蓝 暖橙 烟紫 雾蓝 色值"),
    Motion(SettingsSection.Appearance, "减少界面动效", "动画 过渡"),
    Font(SettingsSection.Appearance, "界面字体", "MiSans 系统字体 自定义字体"),
    Aspect(SettingsSection.Quality, "默认画面比例", "裁切 拉伸 填充"),
    Seek(SettingsSection.Quality, "快进和快退步长", "跳转 间隔 秒 双击"),
    Quality(SettingsSection.Quality, "画质", "清晰度 1080P 4K"),
    SubtitleSize(SettingsSection.Subtitle, "字幕字号", "字幕大小 字体 CC"),
    DanmakuTiming(SettingsSection.Danmaku, "弹幕时间偏移", "同步 延迟 提前 对齐"),
    DanmakuOpacity(SettingsSection.Danmaku, "弹幕不透明度", "透明度 淡化"),
    DownloadQuality(SettingsSection.Data, "默认下载画质", "缓存 清晰度"),
    DownloadNetwork(SettingsSection.Data, "仅在非计费网络下载", "WiFi WLAN 移动数据 流量", androidOnly = true),
    DownloadSubtitles(SettingsSection.Data, "同时缓存字幕", "保存字幕 下载字幕"),
    DownloadDanmaku(SettingsSection.Data, "同时缓存弹幕", "保存弹幕 下载弹幕"),
    DownloadFolder(SettingsSection.Data, "下载保存位置", "文件 目录 路径 存储 导出"),
}

internal data class SettingsSearchTarget(val section: SettingsSection, val item: SettingsItem? = null) {
    val title get() = item?.title ?: section.title
    val terms get() = item?.terms ?: section.terms
    val key get() = item?.let { "item-${it.name}" } ?: "section-${section.name}"
}

@Composable
internal fun SettingsSectionHeading(section: SettingsSection, target: SettingsSearchTarget?, onReached: () -> Unit) {
    BiliSectionTitle(
        section.title,
        settingsJumpAnchor(target == SettingsSearchTarget(section), onReached).fillMaxWidth().padding(horizontal = 20.dp),
    )
}

@Composable
internal fun settingsJumpAnchor(active: Boolean, onReached: () -> Unit): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val top = with(LocalDensity.current) { 60.dp.toPx() }
    val bottom = with(LocalDensity.current) { (LocalWindowForm.current.height * .43f).coerceIn(110.dp, 380.dp).toPx() }
    var highlighted by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            withFrameNanos {}
            requester.bringIntoView(Rect(0f, -top, 1f, bottom))
            highlighted = true
            onReached()
        }
    }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            delay(2_000)
            highlighted = false
        }
    }
    val highlight = if (highlighted) Modifier.background(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .70f), MaterialTheme.shapes.small,
    ) else Modifier
    return Modifier.bringIntoViewRequester(requester).then(highlight)
}

@Composable
internal fun SettingsSectionSearch(
    desktop: Boolean,
    onSelect: (SettingsSearchTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, desktop) {
        val keyword = query.trim()
        val sections = SettingsSection.entries.filter { desktop || it != SettingsSection.Interaction && it != SettingsSection.Shortcuts }
            .map { SettingsSearchTarget(it) }
        if (keyword.isEmpty()) {
            sections
        } else {
            val terms = keyword.split(Regex("\\s+"))
            val items = SettingsItem.entries.filter { !it.androidOnly || !desktop }.map { SettingsSearchTarget(it.section, it) }
            (items + sections).filter { target ->
                val searchable = "${target.title} ${target.terms}"
                terms.all { searchable.contains(it, ignoreCase = true) }
            }.sortedBy { target ->
                when {
                    target.title.equals(keyword, ignoreCase = true) -> 0
                    target.item != null && target.title.startsWith(keyword, ignoreCase = true) -> 1
                    target.item != null -> 2
                    else -> 3
                }
            }
        }
    }
    AliStageDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BiliSearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = { shown.firstOrNull()?.let(onSelect) },
                    placeholder = "例如：快退、字幕字号、保存位置",
                    autoFocus = true,
                    accessibilityLabel = "搜索设置项目",
                    submitLabel = "定位",
                )
                if (shown.isEmpty()) {
                    Text("没有匹配的设置项目", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(shown, key = { it.key }) { target ->
                            val tab = listOf("通用", "播放", "数据管理", "关于")[target.section.tab]
                            BiliSettingsRow(
                                title = target.title,
                                supporting = if (target.item == null) tab else listOf(tab, target.section.title).distinct().joinToString(" · "),
                                modifier = Modifier.clip(MaterialTheme.shapes.small),
                                onClick = { onSelect(target) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
