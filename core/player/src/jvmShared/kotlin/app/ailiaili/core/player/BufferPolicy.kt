package app.ailiaili.core.player

import app.ailiaili.core.model.NETWORK_CACHING_MAX_MS
import app.ailiaili.core.model.NETWORK_CACHING_MIN_MS

enum class BufferMediaKind { VodDash, VodSingle, Live }

/**
 * 网络缓冲（VLC `:network-caching`）的统一决策点。
 *
 * 手动档完全尊重用户设置；自动档按最近一次 CDN 实测吞吐与流码率的比值
 * 挑选预读深度：快线更省内存，弱网更抗抖动。极短片（≤60 秒）封顶 1 秒，
 * 因为还没缓冲满就播完了。
 *
 * 同一条 CDN 线路短时间内反复卡顿时，调用方传入 [stallBoostMs]，只影响
 * 后续媒体，不在播放中途换源。
 */
object BufferPolicy {
    private const val ASSUMED_DASH_BITRATE_BPS = 2_500_000.0
    private const val ASSUMED_SINGLE_BITRATE_BPS = 2_000_000.0
    private const val LIVE_AUTO_MS = 2_000
    private const val LIVE_MANUAL_FLOOR_MS = 1_000
    private const val SHORT_VIDEO_CAP_MS = 1_000

    fun resolve(
        kind: BufferMediaKind,
        shortVideo: Boolean,
        userMs: Int,
        auto: Boolean,
        measuredBytesPerSecond: Long,
        stallBoostMs: Int = 0,
    ): Int {
        val clampedUser = userMs.coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS)
        if (!auto) {
            return if (kind == BufferMediaKind.Live) {
                maxOf(clampedUser, LIVE_MANUAL_FLOOR_MS)
                    .coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS)
            } else {
                clampedUser
            }
        }
        if (kind == BufferMediaKind.Live) {
            return (LIVE_AUTO_MS + stallBoostMs)
                .coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS)
        }
        val base = if (measuredBytesPerSecond <= 0) {
            // 没测过：与旧默认一致，别瞎猜。
            1_000
        } else {
            val bitrate = if (kind == BufferMediaKind.VodDash) {
                ASSUMED_DASH_BITRATE_BPS
            } else {
                ASSUMED_SINGLE_BITRATE_BPS
            }
            val ratio = measuredBytesPerSecond * 8.0 / bitrate
            when {
                ratio >= 10 -> 500
                ratio >= 3 -> 1_000
                ratio >= 1.5 -> 2_000
                else -> 3_000
            }
        }
        val shortCapped = if (shortVideo) minOf(base, SHORT_VIDEO_CAP_MS) else base
        return (shortCapped + stallBoostMs)
            .coerceIn(NETWORK_CACHING_MIN_MS, NETWORK_CACHING_MAX_MS)
    }
}
