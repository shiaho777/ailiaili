package app.ailiaili.core.data

import app.ailiaili.core.model.ioDispatcher
import app.ailiaili.core.network.JSONObject
import app.ailiaili.core.network.SharedHttp
import app.ailiaili.core.network.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

const val APP_PROJECT_URL = "https://github.com/shiaho777/ailiaili"
const val APP_RELEASES_URL = "$APP_PROJECT_URL/releases"
const val APP_ISSUES_URL = "$APP_PROJECT_URL/issues"

data class AppRelease(val tag: String, val page: String, val notes: String)

suspend fun latestAppRelease(): Result<AppRelease?> = withContext(ioDispatcher) {
    runCatching {
        val response = SharedHttp.api.get(
            "https://api.github.com/repos/shiaho777/ailiaili/releases/latest",
            mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "ailiaili"),
        )
        if (response.code == 404) return@runCatching null
        check(response.code in 200..299) {
            when (response.code) {
                403, 429 -> "GitHub 请求受限，请稍后重试"
                else -> "GitHub 返回 HTTP ${response.code}"
            }
        }
        val body = JSONObject(response.bodyText)
        val tag = body.optString("tag_name").removePrefix("v")
        check(tag.isNotBlank()) { "发布记录缺少版本号" }
        val page = body.optString("html_url").takeIf { it.startsWith("$APP_RELEASES_URL/") } ?: APP_RELEASES_URL
        AppRelease(tag, page, body.optString("body").trim())
    }.onFailure { if (it is CancellationException) throw it }
}
