package app.ailiaili.core.designsystem

import androidx.compose.runtime.Composable
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

/**
 * 图片加载走流式 fetcher：
 * - 不再把整张图先读进 ByteArray 再交给 Coil；
 * - 保留 B 站图片必需的 Referer / UA；
 * - Coil 自己负责内存/磁盘缓存。
 */
@Composable
fun InstallBiliImages() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(BiliImageFetcher.Factory()) }
            .build()
    }
}

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
