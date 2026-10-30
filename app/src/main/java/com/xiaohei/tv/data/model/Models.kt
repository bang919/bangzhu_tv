package com.xiaohei.tv.data.model

data class VideoCard(
    val id: String,
    val title: String,
    val poster: String = "",
    val year: String = "",
    val note: String = "",
    val typeName: String = "",
    val area: String = "",
    val actors: String = "",
    val providerId: String = "",
)

data class HomeSection(
    val title: String,
    val items: List<VideoCard>,
)

data class HomeData(
    val hero: VideoCard?,
    val heroDescription: String = "",
    val sections: List<HomeSection>,
)

data class FilterOption(
    val label: String,
    val href: String,
    val selected: Boolean,
)

data class FilterGroup(
    val label: String,
    val options: List<FilterOption>,
) {
    val selectedLabel: String
        get() = options.firstOrNull { it.selected }?.label ?: "全部"
}

data class CategoryPage(
    val typeId: Int,
    val title: String,
    val items: List<VideoCard>,
    val filters: List<FilterGroup>,
    val currentHref: String,
    val currentPage: Int,
    val totalPages: Int,
    val prevHref: String?,
    val nextHref: String?,
    val totalPagesKnown: Boolean = true,
)

data class Episode(
    val name: String,
    val href: String,
)

data class PlaySource(
    val name: String,
    val episodes: List<Episode>,
)

data class VideoDetail(
    val id: String,
    val title: String,
    val poster: String = "",
    val year: String = "",
    val area: String = "",
    val typeName: String = "",
    val language: String = "",
    val remarks: String = "",
    val score: String = "",
    val actors: String = "",
    val director: String = "",
    val description: String = "",
    val sources: List<PlaySource> = emptyList(),
    val related: List<VideoCard> = emptyList(),
    val versions: List<VideoCard> = emptyList(),
    val providerId: String = "",
) {
    val firstEpisode: Episode?
        get() = sources.firstOrNull()?.episodes?.firstOrNull()
}

data class StreamInfo(
    val url: String,
    val source: String = "",
    val note: String = "",
    val mimeType: String? = null,
    val hongguoKey: ByteArray? = null,
    val hongguo: Boolean = false,
)

data class ProviderInfo(
    val id: String,
    val name: String,
    val baseUrl: String,
)

data class SearchFilterState(
    val typeName: String? = null,
    val year: String? = null,
    val area: String? = null,
)

enum class VideoType(val label: String) {
    MOVIE("电影片"),
    SERIES("连续剧"),
    ANIME("动漫片"),
    VARIETY("综艺片"),
    DOCUMENTARY("纪录片"),
    REAL_DRAMA("真人剧"),
    COMIC_DRAMA("漫剧"),
    AI_DRAMA("AI剧"),
    SHORT_RANK("短剧榜单"),
    SHORT_DISCOVERY("找剧"),
    SHORT_FEED("推荐流"),
    GAZE_ALL("全部影视"),
    CHINESE_ANIME("国漫"),
}

data class SearchPage(val items: List<VideoCard>, val hasMore: Boolean)

enum class CategorySort(val label: String) {
    LATEST("最新"),
    POPULAR("最热"),
    SCORE("评分"),
}

enum class RankingSort(val label: String) {
    POPULAR("人气榜"),
    SCORE("评分榜"),
}
