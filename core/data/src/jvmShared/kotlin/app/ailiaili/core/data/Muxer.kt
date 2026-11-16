package app.ailiaili.core.data

/**
 * 把分离的音视频流无损合流为单个 MP4（不重编码）。
 *
 * Android 用系统 MediaExtractor + MediaMuxer，零第三方依赖；
 * 桌面无系统合流器，返回 false，调用方保留双流并诚实提示。
 *
 * 返回 true 表示 [outPath] 已生成完整可播的 MP4；任何失败返回 false，
 * 调用方必须保留原始双流文件，绝不因合流失败丢下载内容。
 */
expect fun muxToMp4(
    videoPath: String,
    audioPath: String?,
    outPath: String,
    videoCodecs: String,
    audioCodecs: String,
): Boolean
