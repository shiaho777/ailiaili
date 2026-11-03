package app.ailiaili.core.player

import kotlin.math.abs

/**
 * 判断一次 seek 是否已经到位。
 *
 * VLC 对 DASH 双流的远距离 seek 可能失败并停在原地，也可能直接甩过目标。
 * 这里给一个宽窗口：落在目标 ±1.5 秒内，或者已经越过目标 0.75 秒以上，
 * 都算到位；否则由看门狗重发 seek / 重建 Media。
 */
internal fun isSeekSettled(newTimeMs: Long, targetMs: Long): Boolean {
    if (targetMs < 0) return true
    if (abs(newTimeMs - targetMs) <= 1_500) return true
    return targetMs > 0 && newTimeMs > targetMs + 750
}
