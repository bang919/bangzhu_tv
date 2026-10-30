package com.xiaohei.tv.data.provider.maccms

import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.Episode
import com.xiaohei.tv.data.model.FilterGroup
import com.xiaohei.tv.data.model.FilterOption
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.HomeSection
import com.xiaohei.tv.data.model.PlaySource
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MacCmsParser(
    private val baseUrl: String,
    private val providerId: String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val videoIdRegex = Regex("""/vod/(?:detail|play)/id/(\d+)""")
    private val playIdRegex = Regex("""/vod/play/id/(\d+)/sid/(\d+)/nid/(\d+)""")

    fun parseHome(html: String): HomeData {
        val doc = Jsoup.parse(html)
        val sections = mutableListOf<HomeSection>()

        doc.select("div.myui-panel").forEach { panel ->
            val title = panel.selectFirst("h3.title")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@forEach
            if (title.contains("榜单")) return@forEach
            val cards = panel.select("ul.myui-vodlist > li")
                .mapNotNull(::parseCard)
                .distinctBy { it.id }
            if (cards.isNotEmpty()) {
                sections += HomeSection(title, cards)
            }
        }

        val distinctSections = sections.distinctBy { it.title }
        val priority = listOf("大片推荐", "最新电影片", "最热电影片", "最新连续剧", "最热连续剧", "最新动漫片", "最热动漫片", "最新综艺片", "最热综艺片")
        val ordered = distinctSections.sortedBy { section ->
            priority.indexOf(section.title).let { if (it < 0) 99 else it }
        }

        val heroSection = ordered.firstOrNull { it.title.contains("推荐") } ?: ordered.firstOrNull()
        val hero = heroSection?.items?.firstOrNull()

        return HomeData(
            hero = hero,
            sections = ordered,
        )
    }

    fun parseCategory(html: String, typeId: Int, fallbackTitle: String, currentHref: String): CategoryPage {
        val doc = Jsoup.parse(html)
        val title = doc.select(".myui-panel__head h3.title")
            .firstOrNull { it.text().contains("片") || it.text().contains("剧") || it.text().contains("综艺") }
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackTitle

        val cards = doc.select("ul.myui-vodlist > li")
            .mapNotNull(::parseCard)
            .distinctBy { it.id }

        val filters = parseFilters(doc, currentHref)
        val pager = doc.selectFirst("ul.myui-page")
        val fraction = Regex("""(\d+)\s*/\s*(\d+)""").find(pager?.text().orEmpty())
        val hrefPages = pager?.select("a[href]")?.mapNotNull { a ->
            Regex("""/page/(\d+)\.html""").find(a.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.orEmpty()

        val currentPage = fraction?.groupValues?.get(1)?.toIntOrNull()
            ?: hrefPages.firstOrNull()
            ?: 1
        val totalPages = fraction?.groupValues?.get(2)?.toIntOrNull()
            ?: hrefPages.maxOrNull()
            ?: currentPage

        val prevHref = pager?.select("a[href]")
            ?.firstOrNull { it.text().contains("上一页") }
            ?.attr("href")
            ?.let(::absolutize)
            ?.takeIf { it.isNotBlank() }
        val nextHref = pager?.select("a[href]")
            ?.firstOrNull { it.text().contains("下一页") }
            ?.attr("href")
            ?.let(::absolutize)
            ?.takeIf { it.isNotBlank() }

        return CategoryPage(
            typeId = typeId,
            title = title,
            items = cards,
            filters = filters,
            currentHref = currentHref,
            currentPage = currentPage,
            totalPages = totalPages.coerceAtLeast(1),
            prevHref = prevHref,
            nextHref = nextHref,
        )
    }

    fun parseSearch(html: String): List<VideoCard> {
        val doc = Jsoup.parse(html)
        val results = doc.select("#searchList > li").mapNotNull { li ->
            val link = li.selectFirst("h4.title a[href]") ?: li.selectFirst("a.myui-vodlist__thumb[href]") ?: return@mapNotNull null
            val href = link.attr("href")
            val id = extractVideoId(href) ?: return@mapNotNull null
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val thumb = li.selectFirst("a.myui-vodlist__thumb")
            val poster = normalizeImage(thumb?.attr("data-original").orEmpty())
            val note = li.selectFirst(".pic-text")?.text()?.trim().orEmpty()
            val paragraphs = li.select("p").map { it.text() }
            val meta = paragraphs.firstOrNull { it.contains("分类：") }.orEmpty()
            val typeName = Regex("""分类：\s*(.*?)(?=地区：|年份：|$)""").find(meta)?.groupValues?.get(1)?.trim().orEmpty()
            val area = Regex("""地区：\s*(.*?)(?=年份：|分类：|$)""").find(meta)?.groupValues?.get(1)?.trim().orEmpty()
            val year = Regex("""年份：\s*(\d{4})""").find(meta)?.groupValues?.get(1).orEmpty()
            val actors = paragraphs.firstOrNull { it.startsWith("主演：") }
                ?.removePrefix("主演：")
                ?.trim()
                .orEmpty()
            VideoCard(
                id = id,
                title = title,
                poster = poster,
                year = year,
                note = note,
                typeName = typeName,
                area = area,
                actors = actors,
                providerId = providerId,
            )
        }.distinctBy { it.id }

        if (results.isNotEmpty()) return results
        return parseCards(doc.select(".myui-vodlist li"))
    }

    fun searchHasNext(html: String, currentPage: Int): Boolean {
        val pager = Jsoup.parse(html).selectFirst("ul.myui-page") ?: return false
        val fraction = Regex("""(\d+)\s*/\s*(\d+)""").find(pager.text())
        if (fraction != null) return currentPage < (fraction.groupValues[2].toIntOrNull() ?: currentPage)
        val next = pager.select("a[href]").firstOrNull { it.text().contains("下一页") } ?: return false
        val page = Regex("""/page/(\d+)""").find(next.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        return page != null && page > currentPage
    }

    fun parseDetail(html: String, fallbackId: String = ""): VideoDetail {
        val doc = Jsoup.parse(html)
        val detailRoot = doc.selectFirst(".myui-content__detail")
        val title = detailRoot?.selectFirst("h1.title")?.text()?.trim()
            ?: doc.selectFirst("h1.title")?.text()?.trim()
            ?: ""
        val poster = normalizeImage(
            doc.selectFirst(".myui-content__thumb img")?.attr("data-original").orEmpty()
                .ifBlank { doc.selectFirst(".myui-content__thumb a.myui-vodlist__thumb")?.attr("data-original").orEmpty() }
        )
        val dataLines = doc.select(".myui-content__detail p.data").map { it.text() }
        val allMeta = dataLines.joinToString("\n")

        val typeName = Regex("""分类：\s*(.*?)(?=地区：|年份：|语言：|$)""").find(allMeta)?.groupValues?.get(1)?.trim().orEmpty()
        val area = Regex("""地区：\s*(.*?)(?=年份：|语言：|分类：|$)""").find(allMeta)?.groupValues?.get(1)?.trim().orEmpty()
        val year = Regex("""年份：\s*(\d{4})""").find(allMeta)?.groupValues?.get(1).orEmpty()
        val remarks = dataLines.firstOrNull { it.startsWith("更新：") }
            ?.removePrefix("更新：")
            ?.trim()
            .orEmpty()
        val actors = dataLines.firstOrNull { it.startsWith("主演：") }
            ?.removePrefix("主演：")
            ?.trim()
            .orEmpty()
        val director = dataLines.firstOrNull { it.startsWith("导演：") }
            ?.removePrefix("导演：")
            ?.trim()
            .orEmpty()
        val desc = doc.selectFirst("#desc .text-collapse span.data")?.text()?.trim()
            ?.ifBlank { null }
            ?: doc.selectFirst("#desc .text-collapse span.sketch")?.text()?.trim().orEmpty()
        val score = doc.selectFirst("div.score .branch")?.text()?.trim().orEmpty()

        val id = extractVideoId(detailRoot?.selectFirst("a.myui-vodlist__thumb")?.attr("href").orEmpty())
            ?: fallbackId

        val sources = parsePlaySources(doc)
        val related = doc.select("ul.myui-vodlist__bd > li")
            .mapNotNull(::parseCard)
            .distinctBy { it.id }
            .filter { it.id != id }

        return VideoDetail(
            id = id,
            title = title,
            poster = poster,
            year = year,
            area = area,
            typeName = typeName,
            language = detectLanguage(title),
            remarks = remarks,
            score = score,
            actors = actors,
            director = director,
            description = desc,
            sources = sources,
            related = related,
            providerId = providerId,
        )
    }

    fun parsePlay(html: String): StreamInfo? {
        val match = Regex(
            """player_aaaa\s*=\s*(\{.*?\})\s*</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)
        if (match != null) {
            runCatching {
                val obj = json.parseToJsonElement(match.groupValues[1]) as? JsonObject
                    ?: return@runCatching null
                val encrypt = obj["encrypt"]?.jsonPrimitive?.intOrNull ?: 0
                if (encrypt != 0) return@runCatching null
                val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (url.isBlank()) return@runCatching null
                StreamInfo(
                    url = normalizeStreamUrl(url),
                    source = obj["from"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    note = obj["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }.getOrNull()?.let { return it }
        }

        val fallback = Regex("""https?://[^\s"'<>\\]+?\.m3u8[^\s"'<>\\]*""").find(html)?.value
        return fallback?.let { StreamInfo(normalizeStreamUrl(it)) }
    }

    fun extractVideoId(href: String): String? = videoIdRegex.find(href)?.groupValues?.get(1)

    fun extractPlayIndices(href: String): Triple<String, String, String>? {
        val m = playIdRegex.find(href) ?: return null
        return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    fun absolutize(href: String): String {
        val trimmed = href.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> baseUrl + trimmed
            else -> "$baseUrl/$trimmed"
        }
    }

    fun detectLanguage(title: String): String {
        val languages = listOf(
            "粤语", "国语", "普通话", "英语", "日语", "韩语", "法语", "德语",
            "泰语", "俄语", "西班牙语", "意大利语", "葡萄牙语", "印地语", "阿拉伯语"
        )
        return languages.firstOrNull { title.contains(it) }.orEmpty()
    }

    fun baseTitle(title: String): String {
        return title
            .replace(Regex("""[（(【\[].*?[）)】\]]"""), " ")
            .replace(Regex("""(粤语|国语|普通话|国配|粤配|台配|中字|中文字幕|双语|原声|英语|日语|韩语|法语|德语|泰语|俄语|西班牙语|意大利语|葡萄牙语|印地语|阿拉伯语)(版|配音)?"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun parsePlaySources(doc: Document): List<PlaySource> {
        val panel = doc.select("div.myui-panel").firstOrNull {
            it.selectFirst("h3.title")?.text()?.contains("播放地址") == true
        } ?: return emptyList()

        val tabs = panel.select("ul.nav-tabs li a[data-toggle=tab]")
        if (tabs.isNotEmpty()) {
            val sources = tabs.mapNotNull { tab ->
                val paneId = tab.attr("href").removePrefix("#")
                val pane = panel.selectFirst("div[id=$paneId]") ?: return@mapNotNull null
                val episodes = pane.select("li a[href*=/vod/play/]").mapNotNull { a ->
                    val href = absolutize(a.attr("href"))
                    if (href.isBlank()) return@mapNotNull null
                    val name = a.text().trim().ifBlank { a.parent()?.attr("title").orEmpty() }
                    Episode(name = name.ifBlank { "正片" }, href = href)
                }.distinctBy { it.href }
                if (episodes.isEmpty()) null else PlaySource(
                    name = tab.text().trim().ifBlank { "线路${sourcesIndex(tab)}" },
                    episodes = episodes,
                )
            }.distinctBy { it.name }
            if (sources.isNotEmpty()) return sources
        }

        val episodes = panel.select("li a[href*=/vod/play/]").mapNotNull { a ->
            val href = absolutize(a.attr("href"))
            if (href.isBlank()) return@mapNotNull null
            Episode(
                name = a.text().trim().ifBlank { a.parent()?.attr("title").orEmpty() }.ifBlank { "正片" },
                href = href,
            )
        }.distinctBy { it.href }
        return if (episodes.isEmpty()) emptyList() else listOf(PlaySource("默认", episodes))
    }

    private fun sourcesIndex(element: Element): String {
        val index = element.parent()?.parent()?.elementSiblingIndex()?.plus(1) ?: 1
        return index.toString()
    }

    private fun parseFilters(doc: Document, currentHref: String): List<FilterGroup> {
        return doc.select("ul.myui-screen__list").mapNotNull { ul ->
            val label = ul.selectFirst("li:first-child a")?.text()?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            val options = ul.select("li a.btn").mapNotNull { a ->
                val href = a.attr("href").trim()
                val text = a.text().trim()
                if (text.isBlank() || href.isBlank() || href == "javascript:;" || href == "#") return@mapNotNull null
                FilterOption(
                    label = text,
                    href = absolutize(href),
                    selected = a.hasClass("btn-warm"),
                )
            }
            if (options.isEmpty()) null else FilterGroup(label, options)
        }
    }

    private fun parseCard(li: Element): VideoCard? {
        val thumb = li.selectFirst("a.myui-vodlist__thumb[href]") ?: li.selectFirst("a[href*=/vod/detail/]")
            ?: return null
        val href = thumb.attr("href")
        val id = extractVideoId(href) ?: return null
        var title = thumb.attr("title").trim()
        if (title.isBlank()) {
            title = li.selectFirst("h4.title a")?.text()?.trim()
                ?: li.selectFirst("h4.title")?.text()?.trim()
                ?: return null
        }
        val poster = normalizeImage(
            thumb.attr("data-original").ifBlank {
                thumb.selectFirst("img")?.attr("data-original").orEmpty().ifBlank {
                    thumb.attr("data-src")
                }
            }
        )
        val year = li.selectFirst(".pic-tag-top .tag")?.text()?.trim().orEmpty()
        val note = li.selectFirst(".pic-text")?.text()?.trim().orEmpty()
        val actors = li.selectFirst(".myui-vodlist__detail p.text")
            ?.text()
            ?.removePrefix("主演：")
            ?.trim()
            .orEmpty()
        val typeArea = li.selectFirst(".myui-vodlist__detail p")?.text().orEmpty()
        val area = Regex("""地区：([^\s]+)""").find(typeArea)?.groupValues?.get(1).orEmpty()
        return VideoCard(
            id = id,
            title = title,
            poster = poster,
            year = year,
            note = note,
            area = area,
            actors = actors,
            providerId = providerId,
        )
    }

    private fun parseCards(elements: List<Element>): List<VideoCard> =
        elements.mapNotNull(::parseCard).distinctBy { it.id }

    private fun normalizeImage(url: String): String {
        val value = url.trim()
        return when {
            value.isBlank() -> ""
            value.startsWith("//") -> "https:$value"
            value.startsWith("http") -> value
            value.startsWith("/") -> baseUrl + value
            else -> value
        }
    }

    private fun normalizeStreamUrl(url: String): String {
        var value = url.trim().replace("\\/", "/")
        if (value.startsWith("//")) value = "https:$value"
        return value
    }
}
