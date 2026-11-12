package com.xiaohei.tv.data.provider

import com.xiaohei.tv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ProviderRegistryTest {
    private class Stub(override val id: String, override val name: String) : MediaProvider {
        override val baseUrl = "https://example.com"
        override suspend fun loadHome(): HomeData = error("unused")
        override suspend fun loadCategory(type: VideoType, href: String?): CategoryPage = error("unused")
        override suspend fun loadRanking(type: VideoType, sort: RankingSort, href: String?): CategoryPage = error("unused")
        override fun categoryHref(type: VideoType, sort: CategorySort): String = error("unused")
        override suspend fun search(keyword: String): List<VideoCard> = error("unused")
        override suspend fun loadDetail(videoId: String): VideoDetail = error("unused")
        override suspend fun resolveStream(episodeHref: String): StreamInfo = error("unused")
    }

    @Test fun replacementAndRollbackPreserveOtherProviders() {
        val first = Stub("xiaoheimi", "original")
        val other = Stub("hongguo", "original")
        val replacement = Stub("xiaoheimi", "updated")
        val registry = ProviderRegistry(listOf(first, other))
        registry.replace(replacement)
        assertSame(replacement, registry.find("xiaoheimi"))
        assertSame(replacement, registry.default)
        assertSame(other, registry.find("hongguo"))
        registry.restore("xiaoheimi")
        assertSame(first, registry.default)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownProviderCannotBeInstalled() {
        ProviderRegistry(listOf(Stub("hongguo", "original"))).replace(Stub("unknown", "bad"))
    }
}
