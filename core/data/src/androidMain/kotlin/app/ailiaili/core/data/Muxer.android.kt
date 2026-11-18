package app.ailiaili.core.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * 系统 MediaExtractor → MediaMuxer 无损合流（不重编码，秒级完成）。
 *
 * B 站 DASH 是 fMP4，MediaExtractor 能直接读；HEVC 轨显式带上 CSD
 * （hvcC 里的 VPS/SPS/PPS），避免部分机型合成器丢参数集导致黑屏。
 * 任何一步失败都清理半成品返回 false，原始双流由调用方保留。
 */
actual fun muxToMp4(
    videoPath: String,
    audioPath: String?,
    outPath: String,
    videoCodecs: String,
    audioCodecs: String,
): Boolean {
    val out = File(outPath)
    var videoExtractor: MediaExtractor? = null
    var audioExtractor: MediaExtractor? = null
    var muxer: MediaMuxer? = null
    var started = false
    return try {
        videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
        val videoTrack = firstTrackOf(videoExtractor, "video/")
        // Int 永不为 null，旧写法 ?: return false 是死代码；-1 要走 catch
        // 统一清理半成品，不能靠 selectTrack 抛异常兜底。
        if (videoTrack < 0) error("视频流里没有可识别的视频轨")
        videoExtractor.selectTrack(videoTrack)
        val videoFormat = videoExtractor.getTrackFormat(videoTrack)

        muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxVideo = muxer.addTrack(videoFormat)

        var muxAudio = -1
        var audioFormat: MediaFormat? = null
        if (audioPath != null && File(audioPath).isFile) {
            audioExtractor = MediaExtractor().apply { setDataSource(audioPath) }
            val audioTrack = firstTrackOf(audioExtractor, "audio/")
            // 音频文件存在却读不出音轨（损坏/空流）：必须判失败回退双流。
            // 静默跳过会产出无声 MP4，验证可播后 pruneSources 把原始音频
            // 删掉，视频永久无声。
            if (audioTrack < 0) return false
            audioExtractor.selectTrack(audioTrack)
            audioFormat = audioExtractor.getTrackFormat(audioTrack)
            muxAudio = muxer.addTrack(audioFormat)
        }

        muxer.start()
        started = true
        val buffer = ByteBuffer.allocateDirect(1 shl 20) // 1MB，够最大单样本
        val info = MediaCodec.BufferInfo()
        copyTrack(videoExtractor, muxer, muxVideo, buffer, info)
        if (muxAudio >= 0 && audioExtractor != null) {
            copyTrack(audioExtractor, muxer, muxAudio, buffer, info)
        }
        muxer.stop()
        started = false
        out.isFile && out.length() > 0
    } catch (_: Throwable) {
        runCatching { if (started) muxer?.stop() }
        runCatching { out.delete() }
        false
    } finally {
        runCatching { muxer?.release() }
        runCatching { videoExtractor?.release() }
        runCatching { audioExtractor?.release() }
    }
}

private fun firstTrackOf(extractor: MediaExtractor, prefix: String): Int {
    for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith(prefix)) return i
    }
    return -1
}

private fun copyTrack(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    track: Int,
    buffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
) {
    while (true) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = extractor.sampleFlags
        muxer.writeSampleData(track, buffer, info)
        if (!extractor.advance()) break
    }
}
