package app.ailiaili.core.player

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.impl.BufferUtil
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * VLC 回调线程把 RV32 拷进可复用 Bitmap，并在同一线程做成 Skia Image。
 * UI 线程只拿已经做好的 Image 去画，不再 makeFromBitmap。
 *
 * 三个槽位轮转：写线程永远挑「既不是最新发布、也不是正在显示」的槽，
 * 所以「正在画的不写不关」由选槽规则保证，不依赖 displayed 的更新时机。
 * 换分辨率/释放要关旧 Image 时，先等 UI 让出正在显示的槽（有界等待），
 * 避免 use-after-close 的 native 崩溃。
 */
internal class FramePump {
    private val slots = 3
    private val bitmaps = arrayOfNulls<Bitmap>(slots)
    private val images = arrayOfNulls<Image>(slots)
    private val published = AtomicInteger(-1)
    private val displayed = AtomicInteger(-1)
    private val generation = AtomicInteger(0)
    private val gate = Any()
    @Volatile private var live = true
    private var width = 0
    private var height = 0

    val frameGeneration: Int get() = generation.get()

    fun sizeFor(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> =
        VideoFrames.fit(sourceWidth, sourceHeight)

    fun publish(source: ByteBuffer, frameWidth: Int, frameHeight: Int, pitch: Int) {
        if (!live || frameWidth <= 0 || frameHeight <= 0) return
        synchronized(gate) {
            if (!live) return
            ensure(frameWidth, frameHeight)
            val pub = published.get()
            val disp = displayed.get()
            // 三槽里排除「最新发布」和「正在显示」，剩下的随便写：
            // 正在画的那块永远不会被写、被关。
            val slot = (0 until slots).firstOrNull { it != pub && it != disp } ?: return
            val bitmap = bitmaps[slot] ?: return
            val pixmap = bitmap.peekPixels() ?: return
            val dest = BufferUtil.getByteBufferFromPointer(pixmap.addr, pixmap.computeByteSize())
            VideoFrames.copyRv32(source, pitch, frameWidth, frameHeight, dest, pixmap.rowBytes)
            bitmap.notifyPixelsChanged()
            val snapshot = Image.makeFromBitmap(bitmap)
            images[slot]?.close()
            images[slot] = snapshot
            published.set(slot)
            generation.incrementAndGet()
        }
    }

    fun peek(): Image? {
        val slot = published.get()
        if (slot < 0) return null
        displayed.set(slot)
        return images[slot]
    }

    /**
     * 截图专用：makeFromBitmap 是拷贝语义，副本与原槽位生命周期无关，
     * IO 线程编码期间播放侧换分辨率/释放都不会把它关掉。
     * 不触碰 displayed——截图不是「正在画」。
     */
    fun snapshotCopy(): Image? = synchronized(gate) {
        val slot = published.get()
        if (slot < 0) return null
        val bitmap = bitmaps[slot] ?: return null
        runCatching { Image.makeFromBitmap(bitmap) }.getOrNull()
    }

    fun halt() {
        live = false
    }

    fun release() {
        halt()
        synchronized(gate) {
            awaitDisplayedClear()
            published.set(-1)
            images.forEach { runCatching { it?.close() } }
            images.fill(null)
            bitmaps.forEach { runCatching { it?.close() } }
            bitmaps.fill(null)
        }
    }

    private fun ensure(w: Int, h: Int) {
        val first = bitmaps[0]
        if (width == w && height == h && first != null && !first.isNull &&
            bitmaps.all { it != null && !it.isNull }
        ) return
        // 要关旧 Image / 重分配 Bitmap：先等 UI 让出正在显示的槽。
        awaitDisplayedClear()
        width = w
        height = h
        published.set(-1)
        images.forEach { runCatching { it?.close() } }
        images.fill(null)
        repeat(slots) { index ->
            val bitmap = bitmaps[index] ?: Bitmap().also { bitmaps[index] = it }
            bitmap.reset()
            bitmap.allocN32Pixels(w, h, true)
        }
    }

    /**
     * 等 displayed 离开已发布槽位（UI 画完这一帧就不再引用旧 Image）。
     * 有界等待：UI 线程卡死时宁可冒极小风险也不能把播放线程永久挂住。
     */
    private fun awaitDisplayedClear() {
        val deadline = System.currentTimeMillis() + 500
        while (System.currentTimeMillis() < deadline) {
            val disp = displayed.get()
            val pub = published.get()
            if (disp < 0 || disp != pub) {
                displayed.set(-1)
                return
            }
            Thread.sleep(2)
        }
        displayed.set(-1)
    }
}