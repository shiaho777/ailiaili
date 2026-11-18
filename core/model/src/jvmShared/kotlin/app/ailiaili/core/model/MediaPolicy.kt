package app.ailiaili.core.model

/**
 * 播放源白名单（借鉴 FocuBili media_url_policy）：
 * 只允许 B 站官方媒体域名的 HTTPS 地址；拒绝 userinfo/fragment、IP 直连、
 * 本地与非 HTTP 协议。取流响应里的 baseUrl/backupUrl 一律先过这道闸，
 * 防止异常/被篡改的响应把播放器指向第三方服务器。
 */
private val MEDIA_HOST_SUFFIXES = listOf(
    ".bilivideo.com",
    ".bilivideo.cn",
    ".biliapi.net",
    ".hdslb.com",
    ".akamaized.net",
)

private val MEDIA_HOST_EXACT = setOf(
    "bilivideo.com",
    "bilivideo.cn",
    "biliapi.net",
    "hdslb.com",
    "akamaized.net",
)

fun isAllowedMediaUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    // userinfo（user:pass@host）常用于钓鱼/凭据注入；fragment 对媒体无意义。
    if (uri.rawUserInfo != null || uri.rawFragment != null) return false
    val host = uri.host?.lowercase() ?: return false
    // 拒绝 IP 直连（含 IPv6 字面量）：官方 CDN 一律域名。
    if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) return false
    if (host in MEDIA_HOST_EXACT) return true
    return MEDIA_HOST_SUFFIXES.any { host.endsWith(it) }
}

/** 过滤出白名单内的地址；全部不合法时返回空列表，由上层走失败提示。 */
fun filterAllowedMediaUrls(urls: List<String>): List<String> = urls.filter { isAllowedMediaUrl(it) }
