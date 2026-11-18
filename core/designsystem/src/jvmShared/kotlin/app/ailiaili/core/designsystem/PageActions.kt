package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.ailiaili.core.data.AppGraph
import app.ailiaili.core.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 列表页共用的轻动作：稍后再看 + 结果提示。
 * 卡片/列表行不用各自接仓库，加入成功后角标立即变选中态（会话内记忆）。
 */
object QuickActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 本会话内已加入稍后再看的稿件，用于卡片选中态。 */
    var laterAids by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** 选中态归属的账号。换号后必须清空，否则新账号误报「已在稍后再看」。 */
    private var ownerUid: String? = null

    init {
        scope.launch {
            AppGraph.repo.session.collect { s ->
                val uid = s.dedeUserId
                if (uid != ownerUid) {
                    ownerUid = uid
                    // mutableState 只在主线程写；后台线程读改写还会并发丢更新。
                    kotlinx.coroutines.withContext(Dispatchers.Main) { laterAids = emptySet() }
                }
            }
        }
    }

    var toast by mutableStateOf<String?>(null)
        private set

    fun dismissToast(message: String) {
        if (toast == message) toast = null
    }

    /** 供页面级操作（设置备份等）显示结果提示。 */
    fun showToast(message: String) {
        toast = message
    }

    fun inLater(item: VideoItem): Boolean = item.aid > 0 && item.aid in laterAids

    fun addWatchLater(item: VideoItem) {
        val aid = item.aid
        if (aid <= 0) {
            toast = "这条内容暂时不能加入稍后再看"
            return
        }
        if (!AppGraph.repo.session.value.loggedIn) {
            toast = "稍后再看需要先登录"
            return
        }
        if (aid in laterAids) {
            toast = "已在稍后再看里"
            return
        }
        scope.launch {
            AppGraph.repo.addWatchLater(aid)
                .onSuccess {
                    // 读-改-写收进主线程串行：Default 线程并发写会互相覆盖丢 aid。
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        laterAids = laterAids + aid
                        toast = "已加入稍后再看"
                    }
                }
                .onFailure { failure ->
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        toast = failure.message ?: "加入稍后再看失败"
                    }
                }
        }
    }
}

/**
 * F5 / 刷新按钮的统一信号。页面视图模型创建时注册自己的刷新动作，
 * 外壳按键只发一次 tick，由「当前在组合里」的页面执行——播放器盖在上面时列表不抢刷新。
 */
object PageRefresh {
    var tick by mutableIntStateOf(0)
        private set

    private val handlers = linkedMapOf<Any, () -> Unit>()

    fun request() {
        tick++
    }

    fun register(token: Any, action: () -> Unit) {
        handlers[token] = action
    }

    fun unregister(token: Any) {
        handlers.remove(token)
    }

    /** 只刷新最后注册（最上层）的页面。 */
    fun refreshCurrent() {
        handlers.values.lastOrNull()?.invoke()
    }
}

/**
 * 页面在组合里时注册刷新动作，离开组合自动注销。
 * 用组合位置而不是 ViewModel 生命周期做判据：tab 页的 ViewModel 会存活到离开外壳，
 * 但页面内容早已不在组合里，不该响应 F5。
 */
@Composable
fun RegisterPageRefresh(onRefresh: () -> Unit) {
    val token = remember { Any() }
    val latest = rememberUpdatedState(onRefresh)
    DisposableEffect(token) {
        PageRefresh.register(token) { latest.value.invoke() }
        onDispose { PageRefresh.unregister(token) }
    }
}
