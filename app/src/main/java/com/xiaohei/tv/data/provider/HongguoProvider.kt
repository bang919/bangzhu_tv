package com.xiaohei.tv.data.provider

import com.hongguotv.core.ContentRepository
import com.hongguotv.core.Series
import com.hongguotv.core.DramaCategory
import com.hongguotv.core.RankBoard
import com.hongguotv.core.DiscoverySelection
import com.hongguotv.core.DiscoveryCursor
import com.hongguotv.core.DiscoveryGroup
import com.hongguotv.core.RecommendationFeed
import com.xiaohei.tv.data.model.*

class HongguoProvider : MediaProvider {
    override val id = "hongguo"
    override val name = "红果短剧"
    override val baseUrl = "https://hongguoduanju.com"
    override val supportedTypes = setOf(VideoType.SERIES, VideoType.REAL_DRAMA, VideoType.COMIC_DRAMA, VideoType.AI_DRAMA, VideoType.SHORT_RANK, VideoType.SHORT_DISCOVERY, VideoType.SHORT_FEED)
    override val supportsRanking = false
    override val supportsHeroDetail = false
    override val supportsSearchFilters = false
    override val categorySorts = listOf(CategorySort.LATEST)
    override val streamCacheTtlMs = 0L
    private val api = ContentRepository()
    private val cursors = mutableMapOf<Pair<String, Int>, DiscoveryCursor>()
    private val discoveryGroups = mutableMapOf<String, List<DiscoveryGroup>>()
    private val feeds = mutableMapOf<String, RecommendationFeed>()
    private val feedPages = mutableMapOf<Pair<String, Int>, com.hongguotv.core.CatalogPage>()

    private fun loadFeed(href: String?): CategoryPage {
        val parts = href.orEmpty().split('|')
        val kind = parts.firstOrNull()?.takeIf { it in setOf("all", "real", "comic") } ?: "all"
        val page = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val result = synchronized(feeds) {
            feedPages[kind to page] ?: run {
                val feed = feeds.getOrPut(kind) {
                    RecommendationFeed(api.http, category = when (kind) {
                        "real" -> DramaCategory.REAL
                        "comic" -> DramaCategory.COMIC
                        else -> null
                    })
                }
                feed.page(page).also { feedPages[kind to page] = it }
            }
        }
        val filters = listOf(FilterGroup("来源", listOf("all" to "综合", "real" to "真人剧", "comic" to "漫剧").map {
            FilterOption(it.second, "${it.first}|1", it.first == kind)
        }))
        return CategoryPage(0, "推荐流", result.items.map { it.card() }, filters, "$kind|$page", page,
            if (result.hasMore) page + 1 else page, if (page > 1) "$kind|${page - 1}" else null,
            if (result.hasMore) "$kind|${page + 1}" else null, totalPagesKnown = false)
    }

    private fun selectionKey(values: Map<String, String>) = values.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
    private fun discoveryHref(values: Map<String, String>, page: Int) = "${selectionKey(values)}|$page"
    private suspend fun loadDiscovery(href: String?): CategoryPage {
        val pieces = href.orEmpty().split('|')
        val values = pieces[0].split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (pair.size == 2 && pair[0] in DiscoverySelection.labels && pair[1].matches(Regex("[a-zA-Z0-9_-]{1,80}"))) pair[0] to pair[1] else null
        }.toMap()
        val selection = DiscoverySelection(values)
        val key = selectionKey(values)
        val page = pieces.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val cursor = if (page == 1) null else synchronized(cursors) { cursors[key to page] }
            ?: if (page == 1) null else throw java.io.IOException("分页已过期，请返回第一页")
        val result = api.filteredDiscovery(selection, cursor)
        val groups = synchronized(discoveryGroups) {
            if (result.groups.isNotEmpty()) discoveryGroups[key] = result.groups
            discoveryGroups[key].orEmpty()
        }
        result.next?.let { synchronized(cursors) { cursors[key to it.page] = it } }
        val filters = groups.map { group ->
            FilterGroup(group.label, listOf(FilterOption("全部", discoveryHref(selection.choose(group.type, "").values, 1), group.type !in values)) +
                group.options.filter { it.id.isNotEmpty() }.map { option ->
                    FilterOption(option.label, discoveryHref(selection.choose(group.type, option.id).values, 1), values[group.type] == option.id)
                })
        }
        return CategoryPage(0, "找剧", result.items.map { it.card() }, filters, discoveryHref(values, page), page,
            if (result.next != null) page + 1 else page, if (page > 1) discoveryHref(values, page - 1) else null,
            result.next?.let { discoveryHref(values, it.page) }, totalPagesKnown = false)
    }

    private fun Series.card() = VideoCard(id = id, title = title, poster = cover, note = badge, typeName = "短剧")
    override suspend fun loadHome(): HomeData {
        val items = api.home().items.map { it.card() }
        return HomeData(items.firstOrNull(), sections = listOf(HomeSection("推荐短剧", items)))
    }
    override fun categoryHref(type: VideoType, sort: CategorySort) = "all|1"
    override suspend fun loadCategory(type: VideoType, href: String?): CategoryPage {
        if (type == VideoType.SHORT_DISCOVERY) return loadDiscovery(href)
        if (type == VideoType.SHORT_FEED) return loadFeed(href)
        val parts = href.orEmpty().split('|')
        val choice = parts.firstOrNull()?.takeIf { it.matches(Regex("[a-z0-9-]+")) } ?: "all"
        val page = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val data = if (type == VideoType.SERIES) {
            api.home(page)
        } else if (type == VideoType.SHORT_RANK) {
            val board = RankBoard.entries.firstOrNull { it.key == choice } ?: RankBoard.ALL
            api.rankPage(board, page)
        } else {
            val category = when (type) {
                VideoType.REAL_DRAMA -> DramaCategory.REAL
                VideoType.COMIC_DRAMA -> DramaCategory.COMIC
                VideoType.AI_DRAMA -> DramaCategory.AI
                else -> throw UnsupportedFeatureException()
            }
            api.discover(category, if (choice == "all") "" else choice, page)
        }
        val totalPages = data.discovery?.totalPages ?: data.ranking?.totalPages ?: if (data.hasMore) page + 1 else page
        val filters = if (type == VideoType.SERIES) {
            emptyList()
        } else if (type == VideoType.SHORT_RANK) {
            listOf(FilterGroup("榜单", RankBoard.entries.map { FilterOption(it.label, "${it.key}|1", it.key == choice || (choice == "all" && it == RankBoard.ALL)) }))
        } else {
            listOf(FilterGroup("题材", listOf(FilterOption("全部", "all|1", choice == "all")) +
                data.discovery?.topics.orEmpty().map { FilterOption(it.label, "${it.id}|1", choice == it.id) }))
        }
        return CategoryPage(0, if (type == VideoType.SERIES) "推荐短剧" else type.label, data.items.map { it.card() }, filters, "$choice|$page", page,
            totalPages, if (page > 1) "$choice|${page - 1}" else null,
            if (data.hasMore) "$choice|${page + 1}" else null, totalPagesKnown = type != VideoType.SERIES)
    }
    override suspend fun loadRanking(type: VideoType, sort: RankingSort, href: String?): CategoryPage = throw UnsupportedFeatureException()
    override suspend fun search(keyword: String) = api.searchAll(keyword).items.map { it.card() }
    override suspend fun searchPage(keyword: String, page: Int): SearchPage {
        val data = api.searchAll(keyword, page)
        return SearchPage(data.items.map { it.card() }, data.hasMore)
    }
    override suspend fun loadDetail(videoId: String): VideoDetail {
        val detail = api.detail(videoId)
        val series = detail.series
        val related = runCatching { api.related(videoId) }.getOrDefault(emptyList())
            .map { it.series.card().copy(note = it.label) }
        return VideoDetail(id = series.id, title = series.title, poster = series.cover,
            remarks = series.badge, typeName = "短剧", description = series.description,
            sources = listOf(PlaySource("红果短剧", detail.episodes.mapIndexed { i, episode -> Episode("第${i + 1}集", episode) })),
            related = related)
    }
    override suspend fun resolveStream(episodeHref: String): StreamInfo {
        val stream = api.stream(episodeHref, 720)
        return StreamInfo(stream.url, name, stream.quality, hongguoKey = stream.key, hongguo = true)
    }
}
