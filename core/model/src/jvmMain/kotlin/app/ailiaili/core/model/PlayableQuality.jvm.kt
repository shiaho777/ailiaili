package app.ailiaili.core.model

/**
 * 桌面 LibVLC 软解/硬解都啃不动的档位：
 * - 127 8K HEVC：解码开销远超桌面实时能力；
 * - 126 杜比视界：B 站用 Profile 8.4（HLG 兼容层），VLC 不认，选了黑屏；
 * - 125 HDR10：VLC 无色调映射管线，画面灰白。
 * Android Media3 走系统解码器，支持与否交给取流侧的能力探测，这里不预禁。
 */
actual val unplayableQns: Set<Int> = setOf(125, 126, 127)
