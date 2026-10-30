package com.xiaohei.tv.data

import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.HomeSection
import com.xiaohei.tv.data.model.ProviderInfo
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.data.provider.MediaProvider
import com.xiaohei.tv.data.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

class MediaRepository(private val providers: ProviderRegistry) {
    private data class Timed<T>(val value: T, val timestamp: Long)

    private val homeCache = ConcurrentHashMap<String, Timed<HomeData>>()
    private val categoryCache = ConcurrentHashMap<String, Timed<CategoryPage>>()
    private val detailCache = object : LinkedHashMap<String, Timed<VideoDetail>>(48, 0.75f, true) {}
    private val streamCache = object : LinkedHashMap<String, Timed<StreamInfo>>(96, 0.75f, true) {}
    private val searchCache = ConcurrentHashMap<String, Timed<List<VideoCard>>>()
    private val versionCache = ConcurrentHashMap<String, Timed<List<VideoCard>>>()
    private val lock = Any()

    val defaultProviderId: String get() = providers.default.id
    val providerInfos: List<ProviderInfo>
        get() = providers.all.map { ProviderInfo(it.id, it.name, it.baseUrl) }

    fun supportsType(type: VideoType, providerId: String? = null): Boolean =
        type in providers.find(providerId).supportedTypes

    fun supportsRanking(providerId: String? = null): Boolean =
        providers.find(providerId).supportsRanking

    fun supportsSearchFilters(providerId: String? = null): Boolean =
        providers.find(providerId).supportsSearchFilters

    fun categorySorts(providerId: String? = null): List<CategorySort> =
        providers.find(providerId).categorySorts

    fun rankingSorts(providerId: String? = null): List<RankingSort> =
        providers.find(providerId).rankingSorts

    fun categoryHref(type: VideoType, sort: CategorySort, providerId: String? = null): String =
        providers.find(providerId).categoryHref(type, sort)

    fun categorySortOf(href: String?, providerId: String? = null): CategorySort =
        providers.find(providerId).categorySortOf(href)

    fun referer(providerId: String? = null): String = providers.find(providerId).referer

    suspend fun loadHome(providerId: String? = null, force: Boolean = false): HomeData =
        withContext(Dispatchers.IO) {
            val provider = providers.find(providerId)
            val cached = homeCache[provider.id]
            if (!force) {
                cached?.takeIf { fresh(it.timestamp, HOME_TTL) }?.let { return@withContext it.value }
            }
            val fallback = !force && cached != null
            try {
                val parsed = provider.loadHome()
                val (docs, heroDetail) = coroutineScope {
                    val docs = async {
                        if (VideoType.DOCUMENTARY !in provider.supportedTypes) {
                            null
                        } else {
                            runCatching {
                                loadCategory(VideoType.DOCUMENTARY, providerId = provider.id, force = force)
                            }.getOrNull()
                        }
                    }
                    val heroDetail = async {
                        if (!provider.supportsHeroDetail) return@async null
                        parsed.hero?.let { hero ->
                            runCatching { loadDetail(hero.id, provider.id, force = false) }.getOrNull()
                        }
                    }
                    docs.await() to heroDetail.await()
                }

                val sections = parsed.sections.toMutableList()
                if (docs != null && docs.items.isNotEmpty()) {
                    sections += HomeSection("最新纪录片", docs.items.take(16))
                }
                val hero = parsed.hero?.let { base ->
                    val card = base.stamp(provider)
                    if (heroDetail != null) {
                        card.copy(
                            poster = heroDetail.poster.ifBlank { card.poster },
                            year = heroDetail.year.ifBlank { card.year },
                            note = heroDetail.remarks.ifBlank { card.note },
                            typeName = heroDetail.typeName.ifBlank { card.typeName },
                            area = heroDetail.area.ifBlank { card.area },
                            actors = heroDetail.actors.ifBlank { card.actors },
                        )
                    } else card
                }
                val data = HomeData(
                    hero = hero,
                    heroDescription = heroDetail?.description.orEmpty(),
                    sections = sections.map { section ->
                        section.copy(items = section.items.map { it.stamp(provider) })
                    },
                )
                homeCache[provider.id] = Timed(data, System.currentTimeMillis())
                data
            } catch (e: Exception) {
                if (fallback) cached?.let { return@withContext it.value }
                throw e
            }
        }

    suspend fun loadCategory(
        type: VideoType,
        href: String? = null,
        providerId: String? = null,
        force: Boolean = false,
    ): CategoryPage = withContext(Dispatchers.IO) {
        val provider = providers.find(providerId)
        val requestKey = "${provider.id}|${type.name}|${href.orEmpty()}"
        if (!force) {
            categoryCache[requestKey]?.takeIf { fresh(it.timestamp, CATEGORY_TTL) }
                ?.let { return@withContext it.value }
        }
        val page = provider.loadCategory(type, href).stamp(provider)
        val cacheKey = "${provider.id}|${type.name}|${page.currentHref}"
        categoryCache[cacheKey] = Timed(page, System.currentTimeMillis())
        if (cacheKey != requestKey) categoryCache[requestKey] = Timed(page, System.currentTimeMillis())
        trimCache(categoryCache)
        page
    }

    suspend fun loadRanking(
        type: VideoType,
        sort: RankingSort,
        providerId: String? = null,
        href: String? = null,
    ): CategoryPage = withContext(Dispatchers.IO) {
        val provider = providers.find(providerId)
        val cacheKey = "${provider.id}|ranking:${type.name}:${sort.name}:${href.orEmpty()}"
        categoryCache[cacheKey]?.takeIf { fresh(it.timestamp, CATEGORY_TTL) }
            ?.let { return@withContext it.value }
        val page = provider.loadRanking(type, sort, href).stamp(provider)
        categoryCache[cacheKey] = Timed(page, System.currentTimeMillis())
        trimCache(categoryCache)
        page
    }

    suspend fun search(
        keyword: String,
        providerId: String? = null,
        force: Boolean = false,
    ): List<VideoCard> = withContext(Dispatchers.IO) {
        val query = keyword.trim()
        if (query.isBlank()) return@withContext emptyList()
        val provider = providers.find(providerId)
        val cacheKey = "${provider.id}|$query"
        if (!force) {
            searchCache[cacheKey]?.takeIf { fresh(it.timestamp, SEARCH_TTL) }
                ?.let { return@withContext it.value }
        }
        val results = provider.search(query).map { it.stamp(provider) }
        searchCache[cacheKey] = Timed(results, System.currentTimeMillis())
        trimCache(searchCache, 50)
        results
    }

    suspend fun searchPage(keyword: String, page: Int, providerId: String? = null): com.xiaohei.tv.data.model.SearchPage =
        withContext(Dispatchers.IO) {
            val provider = providers.find(providerId)
            val result = provider.searchPage(keyword.trim(), page)
            result.copy(items = result.items.map { it.stamp(provider) })
        }

    suspend fun loadDetail(
        videoId: String,
        providerId: String? = null,
        force: Boolean = false,
    ): VideoDetail = withContext(Dispatchers.IO) {
        val provider = providers.find(providerId)
        val cacheKey = "${provider.id}|$videoId"
        synchronized(lock) {
            if (!force) {
                detailCache[cacheKey]?.takeIf { fresh(it.timestamp, DETAIL_TTL) }
                    ?.let { return@withContext it.value }
            }
        }
        val detail = provider.loadDetail(videoId).stamp(provider).let {
            if (it.sources.isEmpty()) throw IOException("该影片暂时没有可播放的地址")
            it
        }
        synchronized(lock) { detailCache[cacheKey] = Timed(detail, System.currentTimeMillis()) }
        detail
    }

    suspend fun loadVersions(detail: VideoDetail): List<VideoCard> = withContext(Dispatchers.IO) {
        val provider = providers.find(detail.providerId)
        val base = provider.versionBaseTitle(detail.title)
        if (base.isBlank() || base == detail.title || base.length < 2) return@withContext emptyList()
        val cacheKey = "${provider.id}|${detail.id}"
        versionCache[cacheKey]?.takeIf { fresh(it.timestamp, VERSION_TTL) }
            ?.let { return@withContext it.value }
        val results = runCatching { search(base, detail.providerId) }.getOrDefault(emptyList())
        val normalizedBase = normalizeTitle(base)
        val versions = results
            .filter { it.id != detail.id }
            .filter { normalizeTitle(it.title).startsWith(normalizedBase) }
            .filter { detail.typeName.isBlank() || it.typeName.isBlank() || it.typeName == detail.typeName }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<VideoCard> {
                    when {
                        it.title.contains("国语") || it.title.contains("普通话") -> 0
                        it.title.contains("粤语") -> 1
                        else -> 2
                    }
                }.thenBy { it.title }
            )
            .take(8)
        versionCache[cacheKey] = Timed(versions, System.currentTimeMillis())
        versions
    }

    suspend fun resolveStream(
        episodeHref: String,
        providerId: String? = null,
    ): StreamInfo = withContext(Dispatchers.IO) {
        val provider = providers.find(providerId)
        val cacheKey = "${provider.id}|$episodeHref"
        synchronized(lock) {
            streamCache[cacheKey]?.takeIf { fresh(it.timestamp, provider.streamCacheTtlMs) }
                ?.let { return@withContext it.value }
        }
        val stream = provider.resolveStream(episodeHref)
        synchronized(lock) { streamCache[cacheKey] = Timed(stream, System.currentTimeMillis()) }
        stream
    }

    private fun VideoCard.stamp(provider: MediaProvider): VideoCard =
        if (providerId == provider.id) this else copy(providerId = provider.id)

    private fun VideoDetail.stamp(provider: MediaProvider): VideoDetail =
        copy(
            providerId = provider.id,
            related = related.map { it.stamp(provider) },
        )

    private fun CategoryPage.stamp(provider: MediaProvider): CategoryPage =
        copy(items = items.map { it.stamp(provider) })

    private fun normalizeTitle(title: String): String =
        title.replace(Regex("""[\s·:：\-—_（）()【】\[\]]"""), "").lowercase()

    private fun fresh(timestamp: Long, ttl: Long): Boolean =
        System.currentTimeMillis() - timestamp in 0..ttl

    private fun <K, V> trimCache(cache: ConcurrentHashMap<K, Timed<V>>, max: Int = 40) {
        if (cache.size <= max) return
        val oldest = cache.entries.minByOrNull { it.value.timestamp } ?: return
        cache.remove(oldest.key)
    }

    companion object {
        private const val HOME_TTL = 3 * 60 * 1000L
        private const val CATEGORY_TTL = 2 * 60 * 1000L
        private const val SEARCH_TTL = 2 * 60 * 1000L
        private const val DETAIL_TTL = 15 * 60 * 1000L
        private const val VERSION_TTL = 30 * 60 * 1000L
        private const val STREAM_TTL = 20 * 60 * 1000L
    }
}
