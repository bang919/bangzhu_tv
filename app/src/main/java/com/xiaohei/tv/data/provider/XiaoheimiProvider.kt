package com.xiaohei.tv.data.provider

import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.data.provider.maccms.MacCmsParser
import com.xiaohei.tv.data.remote.SiteHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

class XiaoheimiProvider(
    private val http: SiteHttpClient = SiteHttpClient(),
    override val baseUrl: String = DEFAULT_BASE_URL,
) : MediaProvider {
    override val id: String = ID
    override val name: String = "小黑咪"

    private val parser = MacCmsParser(baseUrl, id)

    override suspend fun loadHome(): HomeData =
        parser.parseHome(http.get("$baseUrl/", referer))

    override suspend fun loadCategory(type: VideoType, href: String?): CategoryPage {
        val url = href ?: "$baseUrl/index.php/vod/show/id/${type.siteId()}.html"
        val html = http.get(url, referer)
        return parser.parseCategory(html, type.siteId(), type.label, url)
    }

    override suspend fun loadRanking(type: VideoType, sort: RankingSort): CategoryPage =
        loadCategory(type, "$baseUrl/index.php/vod/show/by/${sort.path()}/id/${type.siteId()}.html")

    override fun categoryHref(type: VideoType, sort: CategorySort): String =
        "$baseUrl/index.php/vod/show/by/${sort.path()}/id/${type.siteId()}.html"

    override fun categorySortOf(href: String?): CategorySort = when {
        href == null -> CategorySort.LATEST
        href.contains("/by/hits/") -> CategorySort.POPULAR
        href.contains("/by/score/") -> CategorySort.SCORE
        else -> CategorySort.LATEST
    }

    override suspend fun search(keyword: String): List<VideoCard> {
        val url = "$baseUrl/index.php/vod/search.html"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("wd", keyword)
            .build()
            .toString()
        return parser.parseSearch(http.get(url, referer))
    }

    override suspend fun loadDetail(videoId: String): VideoDetail {
        val html = http.get("$baseUrl/index.php/vod/detail/id/$videoId.html", referer)
        return parser.parseDetail(html, videoId)
    }

    override fun versionBaseTitle(title: String): String = parser.baseTitle(title)

    override suspend fun resolveStream(episodeHref: String): StreamInfo {
        val html = http.get(episodeHref, referer)
        return parser.parsePlay(html)
            ?: throw IOException("解析播放地址失败，请切换线路重试")
    }

    private fun VideoType.siteId(): Int = when (this) {
        VideoType.MOVIE -> 1
        VideoType.SERIES -> 2
        VideoType.VARIETY -> 3
        VideoType.ANIME -> 4
        VideoType.DOCUMENTARY -> 21
    }

    private fun RankingSort.path(): String = when (this) {
        RankingSort.POPULAR -> "hits"
        RankingSort.SCORE -> "score"
    }

    private fun CategorySort.path(): String = when (this) {
        CategorySort.LATEST -> "time"
        CategorySort.POPULAR -> "hits"
        CategorySort.SCORE -> "score"
    }

    companion object {
        const val ID = "xiaoheimi"
        const val DEFAULT_BASE_URL = "https://xiaoheimi.cc"
    }
}
