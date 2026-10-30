package com.xiaohei.tv.data.provider

import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType

/**
 * 站点适配接口。接入新站点时实现该接口，并在 [ProviderRegistry] 中注册即可；
 * 缓存、版本聚合、历史记录与 UI 均不感知具体站点。
 */
interface MediaProvider {
    val id: String
    val name: String
    val baseUrl: String
    val referer: String get() = "$baseUrl/"

    val categorySorts: List<CategorySort> get() = CategorySort.entries
    val rankingSorts: List<RankingSort> get() = RankingSort.entries
    val supportedTypes: Set<VideoType> get() = VideoType.entries.toSet()
    val supportsRanking: Boolean get() = true
    val supportsHeroDetail: Boolean get() = true
    val supportsSearchFilters: Boolean get() = true
    val streamCacheTtlMs: Long get() = 20 * 60 * 1000L

    suspend fun loadHome(): HomeData

    suspend fun loadCategory(type: VideoType, href: String? = null): CategoryPage

    suspend fun loadRanking(type: VideoType, sort: RankingSort): CategoryPage

    fun categoryHref(type: VideoType, sort: CategorySort): String

    fun categorySortOf(href: String?): CategorySort = CategorySort.LATEST

    suspend fun search(keyword: String): List<VideoCard>

    suspend fun loadDetail(videoId: String): VideoDetail

    fun versionBaseTitle(title: String): String = title

    suspend fun resolveStream(episodeHref: String): StreamInfo
}
