package app.ailiaili.core.designsystem

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class WheelFilterTest {
    private val dt = 1f / 60f

    @Test
    fun startsSoftThenAccelerates() {
        val filter = WheelFilter()
        filter.feed(240f)
        val first = filter.step(dt)
        val second = filter.step(dt)
        val third = filter.step(dt)
        // S 形起步：前几帧逐渐加速，而不是一步吃掉大半（单级衰减的瞬移感）。
        assertTrue(first < second && second < third, "expected ramp-up: $first $second $third")
        assertTrue(first < 240f * 0.2f, "first frame too aggressive: $first")
    }

    @Test
    fun convergesWithoutOvershoot() {
        val filter = WheelFilter()
        filter.feed(300f)
        var total = 0f
        repeat(300) {
            val step = filter.step(dt)
            assertTrue(step >= -0.001f, "critically damped must not overshoot: $step")
            total += step
            if (filter.settled()) return@repeat
        }
        assertTrue(filter.settled(), "should settle: remaining=${filter.remaining}")
        assertTrue(abs(total - 300f) < 1.5f, "should land near target: $total")
    }

    @Test
    fun reverseInputKeepsMomentumAndSettles() {
        val filter = WheelFilter()
        filter.feed(200f)
        repeat(8) { filter.step(dt) }
        val momentum = filter.velocity
        assertTrue(momentum > 0f)
        filter.feed(-350f)
        val immediate = filter.step(dt)
        // 反向瞬间仍带着旧动量向前减速，而不是立刻倒转（消除瞬移感）。
        assertTrue(immediate > -350f * 0.5f, "should not teleport backwards: $immediate")
        var sawNegative = false
        repeat(600) {
            val step = filter.step(dt)
            if (step < 0f) sawNegative = true
        }
        assertTrue(sawNegative, "must respond to reversal")
        assertTrue(filter.settled(), "should settle after reversal: ${filter.remaining}")
    }

    @Test
    fun boundaryCorrectionAccumulatesThenResets() {
        val filter = WheelFilter()
        filter.feed(120f)
        val step = filter.step(dt)
        filter.correct(step) // 列表一点没滚：全部补回
        assertTrue(abs(filter.remaining - 120f) < 0.01f)
        filter.reset()
        assertTrue(filter.settled())
    }
}
