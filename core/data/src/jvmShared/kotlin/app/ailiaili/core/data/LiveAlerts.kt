package app.ailiaili.core.data

import app.ailiaili.core.model.liveStartTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 关注主播开播提醒：应用运行期间每 90 秒查一次关注直播列表，
 * 已知未开播（0/2）→ 直播中（1）的跳变触发系统通知。
 *
 * 边界（诚实标注）：
 * - 只在进程存活时生效：桌面收托盘常驻可覆盖；安卓退后台被杀即停。
 * - 首次快照不通知（避免启动时把正在播的主播全推一遍）。
 * - 官方「预约场次」接口无验证线索，不做服务端预约，纯客户端提醒。
 * - 设置 liveStartNotify 关闭时轮询空转跳过请求。
 */
object LiveAlerts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false
    private var lastStatus: Map<Long, Int>? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // 启动稍等：让登录态/网络先就绪。
            delay(15_000)
            while (true) {
                runCatching { tick() }
                delay(90_000)
            }
        }
    }

    private suspend fun tick() {
        if (!AppGraph.repo.settings.value.liveStartNotify) return
        if (!AppGraph.repo.session.value.loggedIn) return
        val items = AppGraph.repo.followingLive().getOrNull() ?: return
        val now = items.filter { it.roomId > 0 }.associate { it.roomId to it.liveStatus }
        val prev = lastStatus
        lastStatus = now
        if (prev == null) return
        for ((roomId, status) in now) {
            if (liveStartTransition(prev[roomId], status)) {
                val item = items.firstOrNull { it.roomId == roomId } ?: continue
                notifyUser(
                    "你关注的主播开播了",
                    "${item.author.ifBlank { "主播" }}：${item.title.ifBlank { "直播" }}",
                )
            }
        }
    }
}