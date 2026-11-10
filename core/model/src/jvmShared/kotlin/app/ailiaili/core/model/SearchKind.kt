package app.ailiaili.core.model

enum class ContentKind { Video, Live, Bangumi, User }

fun ContentKind.searchType(): String = when (this) {
    ContentKind.Video -> "video"
    ContentKind.Live -> "live_room"
    ContentKind.Bangumi -> "media_bangumi"
    ContentKind.User -> "bili_user"
}

fun ContentKind.signed(): Boolean = this != ContentKind.Bangumi

fun ContentKind.searchReferer(): String? =
    if (this == ContentKind.Bangumi) "https://search.bilibili.com/" else null

fun ContentKind.pageSize(): Int = if (this == ContentKind.Live) 40 else 20

fun searchHasMore(got: Int, page: Int, pageSize: Int, numResults: Int): Boolean {
    if (got <= 0) return false
    return if (numResults <= 0) got >= pageSize else page * pageSize < numResults
}

fun searchEmptyMessage(kind: ContentKind): String = when (kind) {
    ContentKind.Video -> "没有找到相关视频"
    ContentKind.Live -> "没有找到相关直播间"
    ContentKind.Bangumi -> "没有找到相关番剧"
    ContentKind.User -> "没有找到相关用户"
}

/**
 * B 站搜索风控码。
 *
 * -352 / -412 / -403 是常见的“请求被拦截”，-404 在搜索接口上也可能表示
 * 被风控而不是真的没内容。命中后应走裸端点 / 换 buvid 的降级链，而不是
 * 直接把“搜索被风控拦截”甩给用户。
 */
fun isSearchRiskCode(code: Int): Boolean =
    code == -352 || code == -412 || code == -403 || code == -404

fun searchPageError(blocked: Boolean): String? =
    if (blocked) "搜索被风控拦截，过一会儿再试" else null

fun searchKindSwitchShouldLoad(keyword: String): Boolean = keyword.trim().isNotBlank()

data class SearchKindSwitchState(
    val loadGen: Int,
    val loading: Boolean,
    val searched: Boolean,
)

fun searchKindSwitch(keyword: String, loadGen: Int, searched: Boolean): SearchKindSwitchState {
    val reload = searched && searchKindSwitchShouldLoad(keyword)
    return SearchKindSwitchState(
        loadGen = loadGen + 1,
        loading = reload,
        searched = reload,
    )
}
