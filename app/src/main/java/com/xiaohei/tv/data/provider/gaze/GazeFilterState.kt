package com.xiaohei.tv.data.provider.gaze

import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.VideoType
import java.net.URLDecoder
import java.net.URLEncoder

data class GazeFilterState(
    val mform: String = ALL,
    val mcountry: String = ALL,
    val genre: String = ALL,
    val years: String = ALL,
    val sort: String = DEFAULT_SORT,
    val search: String = "",
    val page: Int = 1,
) {
    fun href(): String = "$PREFIX?${query(includePage = true)}"

    fun toUrl(baseUrl: String): String = "$baseUrl/filter?${query(includePage = false)}"

    fun withSort(sort: CategorySort): GazeFilterState = copy(
        sort = when (sort) {
            CategorySort.SCORE -> "grade"
            CategorySort.POPULAR -> "default"
            CategorySort.LATEST -> DEFAULT_SORT
        },
        page = 1,
    )

    private fun query(includePage: Boolean): String = buildList {
        add("mform=$mform")
        if (mcountry != ALL) add("mcountry=$mcountry")
        if (genre != ALL) add("genre=$genre")
        if (years != ALL) add("years=$years")
        if (search.isNotBlank()) add("search=${encode(search)}")
        add("sort=$sort")
        if (includePage && page > 1) add("page=$page")
    }.joinToString("&")

    companion object {
        const val ALL = "all"
        const val DEFAULT_SORT = "updatetime"
        private const val PREFIX = "gaze:filter"

        fun forType(type: VideoType): GazeFilterState = GazeFilterState(
            mform = when (type) {
                VideoType.MOVIE -> "1"
                VideoType.SERIES -> "2"
                VideoType.ANIME -> "bangumi"
                VideoType.CHINESE_ANIME -> "chinese_cartoon"
                else -> ALL
            },
        )

        fun withSearch(keyword: String): GazeFilterState = GazeFilterState(search = keyword.trim())

        fun categorySortOf(href: String?): CategorySort? = parse(href)?.let {
            if (it.sort == "grade") CategorySort.SCORE else CategorySort.LATEST
        }

        fun parse(href: String?): GazeFilterState? {
            if (href == null || !href.startsWith("$PREFIX?")) return null
            val params = href.substringAfter('?')
                .split('&')
                .mapNotNull { pair ->
                    val index = pair.indexOf('=')
                    if (index <= 0) null
                    else decode(pair.substring(0, index)) to decode(pair.substring(index + 1))
                }
                .toMap()
            return GazeFilterState(
                mform = params["mform"] ?: ALL,
                mcountry = params["mcountry"] ?: ALL,
                genre = params["genre"] ?: ALL,
                years = params["years"] ?: ALL,
                sort = params["sort"] ?: DEFAULT_SORT,
                search = params["search"].orEmpty(),
                page = params["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            )
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String): String =
            runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}
