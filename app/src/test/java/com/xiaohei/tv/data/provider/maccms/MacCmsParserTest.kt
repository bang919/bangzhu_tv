package com.xiaohei.tv.data.provider.maccms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacCmsParserTest {
    private val baseUrl = "https://xiaoheimi.cc"
    private val parser = MacCmsParser(baseUrl, "xiaoheimi")

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$name")) { "missing fixture $name" }
            .readText()

    @Test
    fun `parseHome extracts ordered sections and hero`() {
        val home = parser.parseHome(fixture("home.html"))

        assertNotNull(home.hero)
        assertEquals("30246", home.hero?.id)
        assertEquals("猫和老鼠：西部大冒险", home.hero?.title)
        assertTrue(
            "sections=${home.sections.joinToString { "${it.title}:${it.items.size}" }}",
            home.sections.size >= 5,
        )
        assertEquals("大片推荐", home.sections.first().title)
        assertTrue(home.sections.any { it.title == "最新电影片" })
        assertTrue(home.sections.any { it.title == "最新连续剧" })
        assertTrue(home.sections.any { it.title == "最新动漫片" })
        assertTrue(home.sections.none { it.title.contains("榜单") })
        home.sections.forEach { section ->
            assertTrue(section.items.isNotEmpty())
            assertEquals(section.items.size, section.items.distinctBy { it.id }.size)
            assertTrue(section.items.all { it.id.isNotBlank() && it.title.isNotBlank() })
        }
    }

    @Test
    fun `search pager reports next page and stops at last page`() {
        val html = """<ul class="myui-page"><li><a>1/350</a></li><li><a href="/index.php/vod/search/page/2/wd/test.html">下一页</a></li></ul>"""
        assertTrue(parser.searchHasNext(html, 1))
        assertTrue(!parser.searchHasNext(html.replace("1/350", "350/350"), 350))
    }

    @Test
    fun `parseCategory reads title filters and pagination`() {
        val page = parser.parseCategory(
            fixture("category_movie.html"),
            typeId = 1,
            fallbackTitle = "电影片",
            currentHref = "$baseUrl/index.php/vod/show/id/1.html",
        )

        assertEquals("电影片", page.title)
        assertEquals(1, page.currentPage)
        assertEquals(477, page.totalPages)
        assertTrue(page.items.isNotEmpty())
        assertEquals("80076", page.items.first().id)
        assertEquals("2026", page.items.first().year)
        assertTrue(page.filters.size >= 2)
        assertEquals("类型", page.filters.first().label)
        assertEquals("全部", page.filters.first().selectedLabel)
        assertEquals("2", page.nextHref?.let { page2 -> Regex("""/page/(\d+)\.html""").find(page2)?.groupValues?.get(1) })
        assertTrue(page.prevHref?.contains("/page/1.html") == true)
    }

    @Test
    fun `parseCategory advances to second page`() {
        val page = parser.parseCategory(
            fixture("category_page2.html"),
            typeId = 1,
            fallbackTitle = "电影片",
            currentHref = "$baseUrl/index.php/vod/type/id/1/page/2.html",
        )

        assertEquals(2, page.currentPage)
        assertEquals(477, page.totalPages)
        assertEquals("1", page.prevHref?.let { Regex("""/page/(\d+)\.html""").find(it)?.groupValues?.get(1) })
        assertEquals("3", page.nextHref?.let { Regex("""/page/(\d+)\.html""").find(it)?.groupValues?.get(1) })
    }

    @Test
    fun `parseCategory ranks by hits and score`() {
        val hits = parser.parseCategory(fixture("ranking_hits.html"), 1, "电影片", "ranking-hits")
        val score = parser.parseCategory(fixture("ranking_score.html"), 1, "电影片", "ranking-score")

        assertTrue(hits.items.isNotEmpty())
        assertTrue(score.items.isNotEmpty())
        assertEquals(hits.items.size, hits.items.distinctBy { it.id }.size)
    }

    @Test
    fun `parseSearch extracts cards with metadata`() {
        val results = parser.parseSearch(fixture("search.html"))

        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.title == "一路繁花" })
        assertTrue(results.all { it.id.isNotBlank() && it.title.isNotBlank() })
        val first = results.first()
        assertEquals("大陆综艺", first.typeName)
        assertEquals("中国大陆", first.area)
        assertEquals("2025", first.year)
    }

    @Test
    fun `parseDetail reads movie metadata and episodes`() {
        val detail = parser.parseDetail(fixture("detail_movie.html"), fallbackId = "80076")

        assertEquals("80076", detail.id)
        assertEquals("南洋秘术2", detail.title)
        assertEquals("恐怖片", detail.typeName)
        assertEquals("泰国", detail.area)
        assertEquals("2026", detail.year)
        assertTrue(detail.remarks.contains("HD"))
        assertTrue(detail.actors.contains("约萨瓦特"))
        assertTrue(detail.description.isNotBlank())
        assertEquals(1, detail.sources.size)
        assertEquals(1, detail.sources.first().episodes.size)
        assertEquals(
            "https://xiaoheimi.cc/index.php/vod/play/id/80076/sid/1/nid/1.html",
            detail.sources.first().episodes.first().href,
        )
        assertTrue(detail.related.none { it.id == detail.id })
    }

    @Test
    fun `parseDetail reads series language and all episodes`() {
        val detail = parser.parseDetail(fixture("detail_series.html"), fallbackId = "79849")

        assertEquals("79849", detail.id)
        assertEquals("璀璨之城国语", detail.title)
        assertEquals("国语", detail.language)
        assertEquals(1, detail.sources.size)
        assertEquals("小宝影院", detail.sources.first().name)
        assertEquals(10, detail.sources.first().episodes.size)
        assertEquals("第01集", detail.sources.first().episodes.first().name)
    }

    @Test
    fun `parsePlay resolves player json`() {
        val stream = parser.parsePlay(fixture("play.html"))

        assertNotNull(stream)
        assertEquals("https://svip.ryiplay18.com/20261023/14119_98f0cf57/index.m3u8", stream?.url)
        assertEquals("rym3u8", stream?.source)
    }

    @Test
    fun `parsePlay falls back to m3u8 in markup`() {
        val html = """<script>var x = "https://cdn.example.com/live/index.m3u8";</script>"""
        val stream = parser.parsePlay(html)
        assertEquals("https://cdn.example.com/live/index.m3u8", stream?.url)
    }

    @Test
    fun `extract ids and play indices`() {
        assertEquals("123", parser.extractVideoId("/index.php/vod/detail/id/123.html"))
        assertEquals("456", parser.extractVideoId("https://xiaoheimi.cc/index.php/vod/play/id/456/sid/1/nid/2.html"))
        assertNull(parser.extractVideoId("/index.php/vod/show/id/1.html"))
        assertEquals(
            Triple("456", "1", "2"),
            parser.extractPlayIndices("/index.php/vod/play/id/456/sid/1/nid/2.html"),
        )
    }

    @Test
    fun `absolutize builds absolute urls`() {
        assertEquals("https://xiaoheimi.cc/index.php/vod/detail/id/1.html", parser.absolutize("/index.php/vod/detail/id/1.html"))
        assertEquals("https://cdn.test/a.jpg", parser.absolutize("//cdn.test/a.jpg"))
        assertEquals("https://a.test/x", parser.absolutize("https://a.test/x"))
        assertEquals("", parser.absolutize("  "))
    }

    @Test
    fun `detect language and base title`() {
        assertEquals("粤语", parser.detectLanguage("无间道（粤语版）"))
        assertEquals("国语", parser.detectLanguage("璀璨之城国语"))
        assertEquals("", parser.detectLanguage("南洋秘术2"))
        assertEquals("璀璨之城", parser.baseTitle("璀璨之城国语"))
        assertEquals("英雄", parser.baseTitle("英雄 (2002)"))
    }
}
