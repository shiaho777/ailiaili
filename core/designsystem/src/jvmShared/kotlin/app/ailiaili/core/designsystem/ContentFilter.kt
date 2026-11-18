package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.AppSettings
import app.ailiaili.core.model.BlockedUp
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全站视频列表共用的屏蔽。
 *
 * 两类规则叠加生效：
 * · 关键词：标题 / UP 名包含任意一个词（首页面板手输）；
 * · 精确规则：屏蔽某个 UP（优先按 mid，没有 mid 时按完全同名）或某个标题（完全相同）。
 *
 * 精确规则会写回设置，重启不丢。列表页只负责用 [allows] 过滤。
 */
object ContentFilter {
    var title by mutableStateOf("")
    var up by mutableStateOf("")

    var blockedUps by mutableStateOf<List<BlockedUp>>(emptyList())
        private set
    var blockedTitles by mutableStateOf<List<String>>(emptyList())
        private set

    val active: Boolean
        get() = titleTerms.isNotEmpty() || upTerms.isNotEmpty() ||
            blockedUps.isNotEmpty() || blockedTitles.isNotEmpty()

    // 分词结果做派生缓存：allows() 在长列表里逐项调用，
    // 不能每条视频都重新 split 一遍关键词字符串。
    private val titleTerms: List<String> by androidx.compose.runtime.derivedStateOf { split(title) }
    private val upTerms: List<String> by androidx.compose.runtime.derivedStateOf { split(up) }

    /** 启动时从设置恢复一次。不要在设置变化时反复调用，否则会打断正在输入的关键词。 */
    fun load(settings: AppSettings) {
        title = settings.filterTitleKeyword
        up = settings.filterUpKeyword
        blockedUps = settings.blockedUps
        blockedTitles = settings.blockedTitles
    }

    fun allows(item: VideoItem): Boolean {
        if (!active) return true
        if (titleTerms.any { item.title.contains(it, ignoreCase = true) }) return false
        if (upTerms.any { item.author.contains(it, ignoreCase = true) }) return false
        if (blockedUps.any { rule -> matchesUp(rule, item) }) return false
        if (blockedTitles.any { it == item.title }) return false
        return true
    }

    fun blockUp(mid: Long, name: String) {
        val safeMid = mid.coerceAtLeast(0)
        val safeName = name.trim().take(80)
        if (safeMid <= 0 && safeName.isBlank()) return
        if (blockedUps.any { rule -> if (safeMid > 0) rule.mid == safeMid else rule.name == safeName }) return
        blockedUps = blockedUps + BlockedUp(safeMid, safeName)
        save()
    }

    fun blockTitle(title: String) {
        val safe = title.trim().take(200)
        if (safe.isBlank() || safe in blockedTitles) return
        blockedTitles = blockedTitles + safe
        save()
    }

    fun removeBlockedUp(rule: BlockedUp) {
        blockedUps = blockedUps - rule
        save()
    }

    fun removeBlockedTitle(title: String) {
        blockedTitles = blockedTitles - title
        save()
    }

    /**
     * 关键词输入防抖落盘。防抖协程必须活在这里（独立作用域），不能挂在页面组合上：
     * 页面切走会取消 LaunchedEffect，输入后 300ms 内切页就静默丢字。
     */
    fun persist() {
        persistJob?.cancel()
        persistJob = persistScope.launch {
            kotlinx.coroutines.delay(300)
            save()
        }
    }

    private val persistScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )
    private var persistJob: kotlinx.coroutines.Job? = null

    fun clear() {
        title = ""
        up = ""
        blockedUps = emptyList()
        blockedTitles = emptyList()
        save()
    }

    fun summary(): String {
        if (!active) return "未屏蔽任何内容"
        val parts = buildList {
            if (titleTerms.isNotEmpty()) add("标题含「${titleTerms.joinToString(" / ")}」")
            if (upTerms.isNotEmpty()) add("UP 含「${upTerms.joinToString(" / ")}」")
            if (blockedUps.isNotEmpty()) add("${blockedUps.size} 个 UP")
            if (blockedTitles.isNotEmpty()) add("${blockedTitles.size} 个标题")
        }
        return "已屏蔽 " + parts.joinToString(" · ")
    }

    private fun matchesUp(rule: BlockedUp, item: VideoItem): Boolean {
        if (rule.mid > 0 && item.ownerMid > 0 && rule.mid == item.ownerMid) return true
        return rule.name.isNotBlank() && rule.name == item.author
    }

    private fun save() {
        runCatching {
            AppGraph.repo.updateSettings {
                it.copy(
                    filterTitleKeyword = title,
                    filterUpKeyword = up,
                    blockedUps = blockedUps,
                    blockedTitles = blockedTitles,
                )
            }
        }
    }

    private fun split(raw: String): List<String> =
        raw.split(' ', ',', '，', '、', ';', '；', '\t', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}

/**
 * 列表过滤记忆化：只在源列表或屏蔽规则变化时重算。
 * 滚动、展开评论等无关重组不再对整页视频逐条跑 contains。
 * 无规则时直接返回原列表引用，零拷贝。
 */
@Composable
fun rememberFilteredVideos(items: List<VideoItem>): List<VideoItem> {
    val rulesKey = listOf(ContentFilter.title, ContentFilter.up, ContentFilter.blockedUps, ContentFilter.blockedTitles)
    return androidx.compose.runtime.remember(items, rulesKey) {
        if (!ContentFilter.active) items else items.filter { ContentFilter.allows(it) }
    }
}

/** 综合动态条目版（图文按作者/正文匹配同一套规则）。 */
@Composable
fun <T> rememberFilteredEntries(
    items: List<T>,
    allows: (T) -> Boolean,
): List<T> {
    val rulesKey = listOf(ContentFilter.title, ContentFilter.up, ContentFilter.blockedUps, ContentFilter.blockedTitles)
    return androidx.compose.runtime.remember(items, rulesKey) {
        if (!ContentFilter.active) items else items.filter(allows)
    }
}

/**
 * 当前页被屏蔽规则筛空、但还有下一页时，自动补一页。
 * 调用方传当前列表的 [shownEmpty]、分页状态和加载函数即可。
 */
@Composable
fun FilterAutoLoad(
    shownEmpty: Boolean,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(shownEmpty, hasMore, loading) {
        if (shownEmpty && hasMore && !loading) onLoadMore()
    }
}
