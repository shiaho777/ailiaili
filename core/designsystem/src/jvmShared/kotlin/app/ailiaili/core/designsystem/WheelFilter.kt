package app.ailiaili.core.designsystem

import kotlin.math.abs

/**
 * 滚轮平滑的两级低通滤波（带速度记忆的临界阻尼）。
 *
 * 单级指数衰减（每帧固定吃剩余量的一个比例）起步猛、收尾拖，会有「瞬移感」。
 * 这里把「位置 → 中间目标」和「中间目标 → 实际速度」拆成两级串联，
 * 且反向输入时保留动量自然减速掉头，不会滚过目标再倒回来。
 *
 * 离散形式取临界阻尼（ω=9rad/s，60fps 下约 0.5s 收敛），数学上无过冲。
 * 纯函数、无 Compose 依赖，方便单测收敛性。
 */
internal class WheelFilter(
    private val omega: Float = 9f,
) {
    /** 距目标的剩余距离（内部状态，随滚动递减）。 */
    var remaining = 0f
        private set

    /** 当前速度（像素/秒），反向时保留动量。 */
    var velocity = 0f
        private set

    /** 新的滚轮输入，正值向下。 */
    fun feed(delta: Float) {
        remaining += delta
    }

    /** 丢弃未完成的滚动（反向接管/取消时），保留动量。 */
    fun retarget(delta: Float) {
        remaining = delta
    }

    /** 列表到边界没滚完的部分补回余量，由停滞检测决定何时放行。 */
    fun correct(unconsumed: Float) {
        remaining += unconsumed
    }

    /** 推进 dt 秒，返回这一帧应滚动的像素。调用方需把返回值从 [remaining] 中扣除口径一致。 */
    fun step(dt: Float): Float {
        if (settled()) {
            reset()
            return 0f
        }
        val e = remaining
        val v = velocity
        // 临界阻尼闭式解（e 是剩余距离，v 是消耗速度，de/dt = -v）：
        // e(t) = (e + (ωe - v)t)·exp(-ωt)，v(t) = (v + ω(ωe - v)t)·exp(-ωt)
        val decay = kotlin.math.exp((-omega * dt).toDouble()).toFloat()
        val c = omega * e - v
        val nextE = (e + c * dt) * decay
        val nextV = (v + omega * c * dt) * decay
        val moved = e - nextE
        remaining = nextE
        velocity = nextV
        return moved
    }

    /** 余量与速度都足够小才算停，避免尾巴上抖一两个像素。 */
    fun settled(): Boolean = abs(remaining) < 0.5f && abs(velocity) < 4f

    fun reset() {
        remaining = 0f
        velocity = 0f
    }
}
