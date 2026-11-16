package app.ailiaili.core.data

/**
 * 桌面没有系统级 MP4 合流器；引入 ffmpeg 二进制会显著增重，违背轻量原则。
 * 保留双流（VLC 播放本来就支持外挂音轨），合流能力集中在 Android 主战场。
 */
actual fun muxToMp4(
    videoPath: String,
    audioPath: String?,
    outPath: String,
    videoCodecs: String,
    audioCodecs: String,
): Boolean = false
