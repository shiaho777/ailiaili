package app.ailiaili.core.network

/** DASH 视频流的可播性探测输入：档位、编码串、分辨率。 */
internal data class StreamProbe(
    val qn: Int,
    val codecs: String,
    val width: Int,
    val height: Int,
)

/**
 * 返回当前设备解码不了的档位集合。
 * 桌面按固定清单（VLC 不支持杜比视界/HDR/8K）；
 * Android 按 MediaCodecList 的实际解码能力判断，不一刀切。
 */
internal expect fun unsupportedStreamQns(streams: List<StreamProbe>): Set<Int>

internal expect val usesLibVlc: Boolean
