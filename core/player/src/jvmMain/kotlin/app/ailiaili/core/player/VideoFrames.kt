package app.ailiaili.core.player

import java.nio.ByteBuffer
import kotlin.math.min

internal object VideoFrames {
    const val MAX_EDGE = 1920

    /** 行拷贝复用缓冲：只在 VLC 回调线程使用，避免每帧分配 rowBytes 数组。 */
    private var rowBuffer = ByteArray(0)

    fun fit(srcW: Int, srcH: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        if (srcW <= 0 || srcH <= 0) return 2 to 2
        if (srcW <= maxEdge && srcH <= maxEdge) {
            return even(srcW) to even(srcH)
        }
        val scale = min(maxEdge.toDouble() / srcW, maxEdge.toDouble() / srcH)
        return even((srcW * scale).toInt()) to even((srcH * scale).toInt())
    }

    fun copyRv32(
        source: ByteBuffer,
        srcPitch: Int,
        width: Int,
        height: Int,
        dest: ByteBuffer,
        destPitch: Int,
    ) {
        val rowBytes = width * 4
        val src = source.duplicate()
        src.clear()
        dest.clear()
        val total = rowBytes * height
        if (srcPitch == rowBytes && destPitch == rowBytes && src.remaining() >= total && dest.remaining() >= total) {
            val srcLimit = src.limit()
            val destLimit = dest.limit()
            src.limit(src.position() + total)
            dest.limit(dest.position() + total)
            dest.put(src)
            src.limit(srcLimit)
            dest.limit(destLimit)
            dest.position(0)
            return
        }
        if (rowBuffer.size < rowBytes) rowBuffer = ByteArray(rowBytes)
        val row = rowBuffer
        for (y in 0 until height) {
            if (src.remaining() < srcPitch) break
            src.get(row)
            if (srcPitch > rowBytes) src.position(src.position() + (srcPitch - rowBytes))
            if (dest.remaining() < destPitch) break
            dest.put(row)
            if (destPitch > rowBytes) dest.position(dest.position() + (destPitch - rowBytes))
        }
        dest.position(0)
    }

    private fun even(value: Int): Int = value.and(1.inv()).coerceAtLeast(2)
}
