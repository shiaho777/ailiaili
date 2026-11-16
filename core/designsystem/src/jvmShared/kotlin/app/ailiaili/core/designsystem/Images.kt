package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.ailiaili.core.model.USER_AGENT
import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.network.SharedHttp
import coil3.ImageLoader
import coil3.Uri
import coil3.compose.setSingletonImageLoaderFactory
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

/** 封面磁盘缓存上限：Coil 的 LRU 会自动把总量压在这条线以下，不再无限膨胀。 */
private const val COVER_DISK_MAX_BYTES = 256L * 1024 * 1024

/**
 * 图片加载走流式 fetcher：
 * - 不再把整张图先读进 ByteArray 再交给 Coil；
 * - 保留 B 站图片必需的 Referer / UA；
 * - 磁盘缓存有硬上限（LRU 自动淘汰），内存缓存按进程预算收紧；
 * - 启动时按「最后访问时间」修剪历史遗留的无主缓存目录。
 */
@Composable
fun InstallBiliImages() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(BiliImageFetcher.Factory()) }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(
                        app.ailiaili.core.data.imageCacheDirs().first().absolutePath.toPath(),
                    )
                    .maxSizeBytes(COVER_DISK_MAX_BYTES)
                    .build()
            }
            .build()
    }
    LaunchedEffect(Unit) {
        withContext(ioDispatcher) { trimCoverCaches() }
    }
}

/**
 * 启动时的一次性修剪：
 * - 当前磁盘缓存目录超上限时按最久未访问淘汰（正常交给 Coil LRU，这里兜底旧版本遗留）；
 * - 其余历史缓存目录（旧版 Coil 默认目录）整体清掉，避免双倍占用。
 */
private fun trimCoverCaches() {
    runCatching {
        val dirs = app.ailiaili.core.data.imageCacheDirs()
        val active = dirs.firstOrNull() ?: return
        val files = if (active.exists()) active.walkFiles().filter { it.isFile() } else emptyList()
        var total = files.sumOf { it.length() }
        if (total > COVER_DISK_MAX_BYTES) {
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= COVER_DISK_MAX_BYTES * 9 / 10) return@forEach
                val size = file.length()
                runCatching { file.deleteRecursively() }
                total -= size
            }
        }
        dirs.drop(1).forEach { legacy ->
            if (legacy.exists()) runCatching { legacy.deleteRecursively() }
        }
    }
}

private fun String.toPath(): okio.Path = with(okio.Path.Companion) { this@toPath.toPath() }

private class BiliImageFetcher(
    private val url: String,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioDispatcher) {
        try {
            val stream = SharedHttp.default.openStream(
                url,
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://www.bilibili.com/",
                ),
            )
            try {
                SourceFetchResult(
                    source = ImageSource(source = stream.source().buffer(), fileSystem = options.fileSystem),
                    mimeType = null,
                    dataSource = DataSource.NETWORK,
                )
            } catch (t: Throwable) {
                stream.close()
                throw t
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            null
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme ?: return null
            if (scheme != "http" && scheme != "https") return null
            return BiliImageFetcher(data.toString(), options)
        }
    }
}
