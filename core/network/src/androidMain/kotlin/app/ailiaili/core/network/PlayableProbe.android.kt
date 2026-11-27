package app.ailiaili.core.network

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * 用 MediaCodecList 的 VideoCapabilities 判断该档位是否有解码器能接：
 * 分辨率超出能力范围、或 HDR/杜比这类编码没有任何解码器声明支持时剔除。
 * 查询失败时不剔除（宁可让用户试，也不误杀能播的档位）。
 */
internal actual fun unsupportedStreamQns(streams: List<StreamProbe>): Set<Int> {
    if (streams.isEmpty()) return emptySet()
    val videoDecoders = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/", ignoreCase = true) } }
    }.getOrElse { return emptySet() }
    if (videoDecoders.isEmpty()) return emptySet()

    fun mimeFor(codecs: String): String? = when {
        codecs.startsWith("avc", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_AVC
        codecs.startsWith("hev", ignoreCase = true) || codecs.startsWith("hvc", ignoreCase = true) ->
            MediaFormat.MIMETYPE_VIDEO_HEVC
        codecs.startsWith("av01", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_AV1
        codecs.startsWith("dvh", ignoreCase = true) -> MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION
        else -> null
    }

    val unsupported = mutableSetOf<Int>()
    for (stream in streams) {
        val mime = mimeFor(stream.codecs) ?: continue
        val decoders = videoDecoders.filter { info ->
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
        if (decoders.isEmpty()) {
            unsupported += stream.qn
            continue
        }
        if (stream.width <= 0 || stream.height <= 0) continue
        val sizeOk = decoders.any { info ->
            runCatching {
                info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(stream.width, stream.height) == true
            }.getOrDefault(false)
        }
        if (!sizeOk) unsupported += stream.qn
    }
    return unsupported
}

internal actual val usesLibVlc: Boolean = false
