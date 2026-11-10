package app.ailiaili.core.network

import app.ailiaili.core.model.BangumiItem
import app.ailiaili.core.model.ContentKind
import app.ailiaili.core.model.SearchPage
import app.ailiaili.core.model.SearchUser
import app.ailiaili.core.model.VideoItem
import app.ailiaili.core.model.normalizeUrl
import app.ailiaili.core.model.pageSize
import app.ailiaili.core.model.searchHasMore

fun parseBangumiItem(e: JSONObject): BangumiItem? {
    val seasonId = e.lng("season_id")
    if (seasonId <= 0) return null
    val indexShow = e.str("index_show").ifBlank {
        e.obj("new_ep")?.str("index_show").orEmpty()
    }.ifBlank {
        val n = listOf(e.int("ep_size"), e.int("total_count"), e.int("formal_ep_count")).firstOrNull { it > 0 } ?: 0
        if (n > 0) "全${n}话" else ""
    }
    val badge = e.str("badge").ifBlank { e.obj("badge_info")?.str("text").orEmpty() }
    return BangumiItem(
        seasonId = seasonId,
        mediaId = e.lng("media_id"),
        title = e.str("title").stripHtml(),
        cover = normalizeUrl(e.str("cover")),
        badge = badge,
        indexShow = indexShow,
        seasonTypeName = e.str("season_type_name"),
    )
}

fun mergeBangumi(primary: List<BangumiItem>, extra: List<BangumiItem>): List<BangumiItem> {
    val seen = mutableSetOf<Long>()
    return buildList {
        for (item in primary + extra) {
            if (seen.add(item.seasonId)) add(item)
        }
    }
}

fun parseSearchUser(e: JSONObject): SearchUser? {
    val mid = e.lng("mid")
    if (mid <= 0) return null
    return SearchUser(
        mid = mid,
        name = e.str("uname").stripHtml(),
        face = normalizeUrl(e.str("upic")),
        sign = e.str("usign").stripHtml(),
        fans = e.lng("fans"),
        videos = e.lng("videos"),
        level = e.int("level"),
        official = e.obj("official_verify")?.str("desc").orEmpty(),
    )
}

fun decodeSearchSuccess(
    kind: ContentKind,
    page: Int,
    data: JSONObject?,
    extraFt: List<BangumiItem> = emptyList(),
): SearchPage {
    val results = data?.arr("result")?.objects().orEmpty()
    val items: List<VideoItem>
    val bangumiItems: List<BangumiItem>
    val userItems: List<SearchUser>
    when (kind) {
        ContentKind.Video -> {
            items = results.mapNotNull(::parseSearch)
            bangumiItems = emptyList()
            userItems = emptyList()
        }
        ContentKind.Live -> {
            items = results.mapNotNull(::parseLive)
            bangumiItems = emptyList()
            userItems = emptyList()
        }
        ContentKind.Bangumi -> {
            items = emptyList()
            bangumiItems = mergeBangumi(results.mapNotNull(::parseBangumiItem), extraFt)
            userItems = emptyList()
        }
        ContentKind.User -> {
            items = emptyList()
            bangumiItems = emptyList()
            userItems = results.mapNotNull(::parseSearchUser)
        }
    }
    val numResults = data?.int("numResults") ?: 0
    val got = when (kind) {
        ContentKind.Bangumi -> bangumiItems.size
        ContentKind.User -> userItems.size
        else -> items.size
    }
    return SearchPage(
        items = items,
        bangumiItems = bangumiItems,
        hasMore = searchHasMore(got, page, kind.pageSize(), numResults),
        blocked = false,
        userItems = userItems,
    )
}
