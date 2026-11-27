package app.ailiaili.core.player

import kotlin.math.abs

internal fun isSeekSettled(newTimeMs: Long, targetMs: Long): Boolean =
    targetMs < 0 || newTimeMs >= 0 && abs(newTimeMs - targetMs) <= 1_500

internal fun isCrediblePlaybackEnd(positionMs: Long, durationMs: Long): Boolean =
    durationMs <= 0 || positionMs >= durationMs - minOf(3_500L, durationMs / 10)
