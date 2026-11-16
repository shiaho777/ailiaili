package app.ailiaili.core.model

/**
 * Android 用 Media3 + 系统解码器，杜比视界 / HDR / 8K 是否有硬解因机型而异，
 * 不在模型层一刀切；由取流侧按 MediaCodecList 能力过滤。
 */
actual val unplayableQns: Set<Int> = emptySet()
