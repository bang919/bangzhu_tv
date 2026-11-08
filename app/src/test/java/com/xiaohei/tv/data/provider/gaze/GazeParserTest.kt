package com.xiaohei.tv.data.provider.gaze

import org.junit.Assert.assertEquals
import org.junit.Test

class GazeParserTest {
    private val parser = GazeParser("https://gaze.red")
    private val card = """<article class="new"><img src="https://example.com/a.jpg" alt="测试影片"><a href="/play/0123456789abcdef"></a><h3 title="测试影片"><span>测试影片</span></h3></article>"""

    @Test fun `home uses structure rather than generated class names`() {
        val home = parser.parseHome("<div><h2>热门推荐</h2>$card</div>")
        assertEquals("测试影片", home.sections.single().items.single().title)
    }

    @Test fun `category reads cards filters and pagination with changed classes`() {
        val html = """<div aria-label="地区"><a data-filter="all" class="active">全部地区</a><a data-filter="1">中国</a></div><section id="mains">$card</section><nav id="pagination2" aria-label="影片列表分页，当前第 1 页，共 37 页"></nav>"""
        val page = parser.parseCategory(html, GazeFilterState.forType(com.xiaohei.tv.data.model.VideoType.MOVIE), "电影")
        assertEquals("测试影片", page.items.single().title)
        assertEquals(37, page.totalPages)
        assertEquals("地区", page.filters.single().label)
    }

    @Test fun `detail recognizes stable metadata and source attributes`() {
        val html = """<h5 itemprop="name">测试影片</h5><p itemprop="description">简介</p><button id="play-source-tab-g1">默认</button><div id="play-source-panel-g1" data-source-count="2"></div>"""
        val detail = parser.parseDetail(html, "0123456789abcdef")
        assertEquals("测试影片", detail.title)
        assertEquals("简介", detail.description)
        assertEquals(2, detail.sources.single().episodes.size)
    }
    @Test fun `detail handles generated source identifiers and episode classes`() {
        val html = """<h5 itemprop="name">测试影片</h5><div role="tablist" aria-label="播放线路"><button role="tab" id="gabc-g1" data-source-group="1">默认</button><button role="tab" id="gabc-g5" data-source-group="5">备用</button></div><div role="tabpanel" id="gdef-g1" data-source-group="1"><button class="random" data-path="0">第1集</button><button class="random" data-path="1">第2集</button></div><div role="tabpanel" id="gdef-g5" data-source-group="5"><button class="other" data-path="0">正片</button></div>"""
        val detail = parser.parseDetail(html, "0123456789abcdef")
        assertEquals(listOf("默认", "备用"), detail.sources.map { it.name })
        assertEquals(listOf(2, 1), detail.sources.map { it.episodes.size })
        assertEquals("gaze:0123456789abcdef:5:0", detail.sources[1].episodes[0].href)
    }
}
