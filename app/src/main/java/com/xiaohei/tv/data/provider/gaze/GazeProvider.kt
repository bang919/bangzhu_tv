package com.xiaohei.tv.data.provider.gaze

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.data.provider.MediaProvider
import com.xiaohei.tv.data.provider.UnsupportedFeatureException
import com.xiaohei.tv.data.remote.SiteHttpClient
import java.io.IOException

class GazeProvider(
    private val http: SiteHttpClient,
    private val session: GazeSession,
) : MediaProvider {
    override val id: String = ID
    override val name: String = "注视影视"
    override val baseUrl: String = GazeSession.BASE_URL

    // 片源 CDN 的 Referer ACL 拒绝 gaze.red，只有不带 Referer 的分片请求才能通过。
    override val referer: String get() = ""

    override val supportedTypes: Set<VideoType> =
        setOf(VideoType.MOVIE, VideoType.SERIES, VideoType.ANIME)
    override val supportsRanking: Boolean = false
    override val supportsHeroDetail: Boolean = false
    override val supportsSearchFilters: Boolean = false
    override val categorySorts: List<CategorySort> =
        listOf(CategorySort.LATEST, CategorySort.SCORE)
    override val streamCacheTtlMs: Long = 3 * 60 * 1000L

    private val parser = GazeParser(baseUrl)

    override suspend fun loadHome(): HomeData {
        val direct = runCatching { parser.parseHome(http.get("$baseUrl/", "$baseUrl/")) }.getOrNull()
        if (direct != null && direct.sections.isNotEmpty()) return direct
        val html = session.loadHtml(
            url = "$baseUrl/",
            readyJs = GazeSession.HOME_READY_JS,
            timeoutMs = 45_000,
            timeoutMessage = "视频源首页加载超时，请重试",
        )
        return parser.parseHome(html)
    }

    override suspend fun loadCategory(type: VideoType, href: String?): CategoryPage {
        if (type !in supportedTypes) throw UnsupportedFeatureException()
        val fallback = GazeFilterState.forType(type)
        val parsed = GazeFilterState.parse(href) ?: fallback
        val state = if (parsed.mform == GazeFilterState.ALL) {
            parsed.copy(mform = fallback.mform)
        } else {
            parsed
        }
        val html = session.loadFilterPage(state.toUrl(baseUrl), state.page)
        return parser.parseCategory(html, state, type.label)
    }

    override suspend fun loadRanking(type: VideoType, sort: RankingSort): CategoryPage =
        throw UnsupportedFeatureException()

    override fun categoryHref(type: VideoType, sort: CategorySort): String =
        GazeFilterState.forType(type).withSort(sort).href()

    override fun categorySortOf(href: String?): CategorySort =
        GazeFilterState.categorySortOf(href) ?: CategorySort.LATEST

    override suspend fun search(keyword: String): List<VideoCard> {
        val url = GazeFilterState.withSearch(keyword).toUrl(baseUrl)
        return parser.parseSearch(session.loadFilterPage(url, 1))
    }

    override suspend fun loadDetail(videoId: String): VideoDetail {
        if (!VIDEO_ID.matches(videoId)) throw IOException("无效的影片地址，请返回重试")
        return parser.parseDetail(session.loadDetailPage(videoId), videoId)
    }

    override fun versionBaseTitle(title: String): String = ""

    override suspend fun resolveStream(episodeHref: String): StreamInfo {
        val parts = episodeHref.split(':')
        if (parts.size != 4 || parts[0] != "gaze") {
            throw IOException("播放地址已失效，请返回重试")
        }
        val group = parts[2].toIntOrNull()
        val index = parts[3].toIntOrNull()
        if (group == null || index == null) throw IOException("播放地址已失效，请返回重试")
        val playlist = session.capturePlaylist(parts[1], group, index)
        val encoded = Base64.encodeToString(playlist.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return StreamInfo(
            url = "data:${MimeTypes.APPLICATION_M3U8};base64,$encoded",
            source = "注视影视",
            note = "HLS",
            mimeType = MimeTypes.APPLICATION_M3U8,
        )
    }

    companion object {
        const val ID = "gaze"
        private val VIDEO_ID = Regex("""[0-9a-fA-F]{16,}""")
    }
}
