package com.xiaohei.tv.data.provider.gaze

import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.Episode
import com.xiaohei.tv.data.model.FilterGroup
import com.xiaohei.tv.data.model.FilterOption
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.HomeSection
import com.xiaohei.tv.data.model.PlaySource
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GazeParser(private val baseUrl: String) {
    fun parseHome(html: String): HomeData {
        val doc = Jsoup.parse(html, baseUrl)
        val sections = mutableListOf<HomeSection>()
        doc.select("h2").forEach { heading ->
            val section = heading.parents().firstOrNull { parent ->
                parent.selectFirst("article a[href*=play/]") != null && parent.select("h2").size == 1
            } ?: return@forEach
            val title = heading.select("a[href*=/filter]")
                .firstOrNull { !it.text().contains("更多") }
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: heading.text().trim().takeIf { it.isNotBlank() }
                ?: return@forEach
            val cards = section.select("article")
                .mapNotNull(::parseHomeCard)
                .distinctBy { it.id }
            if (cards.isNotEmpty()) {
                sections += HomeSection(title, cards)
            }
        }
        val distinct = sections.distinctBy { it.title }
        val hero = distinct.firstOrNull { it.title.contains("推荐") }?.items?.firstOrNull()
            ?: distinct.firstOrNull()?.items?.firstOrNull()
        return HomeData(hero = hero, sections = distinct)
    }

    fun parseCategory(html: String, state: GazeFilterState, typeLabel: String): CategoryPage {
        val doc = Jsoup.parse(html, baseUrl)
        val items = listItems(doc)
        val filters = buildList {
            filterGroup(doc, "[aria-label=地区], .hJ", "地区") { state.copy(mcountry = it, page = 1).href() }?.let(::add)
            filterGroup(doc, "[aria-label=类型], .ji", "类型") { state.copy(genre = it, page = 1).href() }?.let(::add)
            filterGroup(doc, "#years", "年份") {
                state.copy(years = it, page = 1).href()
            }?.let(::add)
        }
        val label = doc.selectFirst("#pagination2")?.attr("aria-label").orEmpty()
        val totalPages = Regex("""共\s*(\d+)\s*页""")
            .find(label)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        val page = state.page.coerceIn(1, totalPages)
        return CategoryPage(
            typeId = 0,
            title = typeLabel,
            items = items,
            filters = filters,
            currentHref = state.copy(page = page).href(),
            currentPage = page,
            totalPages = totalPages,
            prevHref = if (page > 1) state.copy(page = page - 1).href() else null,
            nextHref = if (page < totalPages) state.copy(page = page + 1).href() else null,
        )
    }

    fun parseSearch(html: String): List<VideoCard> {
        val doc = Jsoup.parse(html, baseUrl)
        return listItems(doc)
    }

    fun parseDetail(html: String, videoId: String): VideoDetail {
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.selectFirst("h5[itemprop=name], .eV h5.lb")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?.substringBefore(" 在线播放")
                ?.trim()
                .orEmpty()
        val score = doc.selectFirst("h5[itemprop=name]")?.parent()?.select("h5")?.getOrNull(1)?.text()
            ?.let { Regex("""(\d+(?:\.\d+)?)""").find(it)?.value }
            .orEmpty()
        val poster = doc.selectFirst(".bD img.jM")?.let { img ->
            img.attr("data-src").takeIf { it.isNotBlank() } ?: img.attr("src")
        } ?: doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val tags = doc.select("a[href*=/filter]")
        val formHref = tags.firstOrNull { it.attr("href").contains("mform=") }
        val area = tags.firstOrNull { it.attr("href").contains("mcountry=") }?.text().orEmpty()
        val year = tags.firstOrNull { it.attr("href").contains("years=") }?.text().orEmpty()
        val description = doc.selectFirst("p[itemprop=description], p.ev")?.text().orEmpty()
        val typeName = when {
            formHref == null -> ""
            formHref.attr("href").contains("mform=1") -> "电影片"
            formHref.attr("href").contains("mform=2") -> "连续剧"
            formHref.attr("href").contains("mform=bangumi") -> "动漫片"
            formHref.attr("href").contains("mform=chinese_cartoon") -> "动漫片"
            else -> formHref.text()
        }
        val sources = doc.select("[role=tablist][aria-label=播放线路] button[data-source-group], button[id^=play-source-tab-g]").distinctBy { it.attr("data-source-group").ifBlank { it.id() } }.mapNotNull { tab ->
            val group = tab.attr("data-source-group").toIntOrNull() ?: GROUP_ID.find(tab.id())?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val panel = doc.selectFirst("[role=tabpanel][data-source-group=$group], #play-source-panel-g$group")
            val buttons = panel?.select("button[data-path], button.playbtn").orEmpty()
            val offset = panel?.attr("data-source-offset")?.toIntOrNull() ?: 0
            val count = panel?.attr("data-source-count")?.toIntOrNull() ?: 0
            val episodes = when {
                buttons.isNotEmpty() -> buttons.mapIndexed { index, button ->
                    Episode(
                        name = button.text().trim().ifBlank { "第 ${offset + index + 1} 集" },
                        href = "gaze:$videoId:$group:$index",
                    )
                }
                count > 0 -> (0 until count).map { index ->
                    Episode(
                        name = if (count == 1) "正片" else "第 ${offset + index + 1} 集",
                        href = "gaze:$videoId:$group:$index",
                    )
                }
                else -> emptyList()
            }
            if (episodes.isEmpty()) return@mapNotNull null
            PlaySource(
                name = tab.text().trim().ifBlank { "线路 $group" },
                episodes = episodes,
            )
        }
        return VideoDetail(
            id = videoId,
            title = title,
            poster = absolute(poster),
            year = year,
            area = area,
            typeName = typeName,
            score = score,
            description = description,
            sources = sources,
        )
    }

    private fun listItems(doc: Document): List<VideoCard> {
        val root = doc.selectFirst("#mains") ?: doc
        return root.select("article")
            .mapNotNull(::parseListCard)
            .distinctBy { it.id }
    }

    private fun parseHomeCard(article: Element): VideoCard? {
        val link = article.selectFirst("a[href*=play/]") ?: return null
        val id = PLAY_ID.find(link.attr("href"))?.groupValues?.get(1) ?: return null
        val title = article.selectFirst("h3")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: article.selectFirst("img[data-src]")?.attr("alt")?.trim()
            ?: link.attr("aria-label").removePrefix("播放").trim()
        if (title.isBlank()) return null
        val poster = article.selectFirst("img[data-src]")?.attr("data-src")
            ?: article.selectFirst("img[src]")?.attr("src")
            ?: ""
        val note = article.selectFirst(".badge-default")?.text()?.trim().orEmpty()
        val type = article.selectFirst(".badge-danger")?.text()?.trim()
            ?.let(::normalizedType)
            .orEmpty()
        return VideoCard(
            id = id,
            title = title,
            poster = absolute(poster),
            note = note,
            typeName = type,
        )
    }

    private fun parseListCard(article: Element): VideoCard? {
        val link = article.selectFirst("a[href*=play/]") ?: return null
        val id = PLAY_ID.find(link.attr("href"))?.groupValues?.get(1) ?: return null
        val title = article.selectFirst("h3")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: article.selectFirst("h3")?.attr("title")?.trim()
            ?: article.selectFirst("img")?.attr("alt")?.trim()
            ?: return null
        val poster = article.selectFirst("img[data-src]")?.attr("data-src")
            ?.takeIf { it.isNotBlank() }
            ?: article.selectFirst("img")?.attr("src")
            ?: ""
        val note = article.selectFirst("span.bl.iB")?.text()?.trim().orEmpty()
        return VideoCard(
            id = id,
            title = title,
            poster = absolute(poster),
            note = note,
        )
    }

    private fun filterGroup(
        doc: Document,
        selector: String,
        label: String,
        hrefFor: (String) -> String,
    ): FilterGroup? {
        val root = doc.selectFirst(selector) ?: return null
        val options = root.select("a[data-filter]")
            .map { anchor ->
                val value = anchor.attr("data-filter")
                FilterOption(
                    label = anchor.text().trim().ifBlank {
                        if (value == "all") "全部$label" else value
                    },
                    href = hrefFor(value),
                    selected = anchor.hasClass("active"),
                )
            }
            .distinctBy { it.href }
        if (options.isEmpty()) return null
        return FilterGroup(label = label, options = options)
    }

    private fun absolute(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> baseUrl + url
        else -> "$baseUrl/$url"
    }

    private fun normalizedType(raw: String): String = when (raw) {
        "电影", "动画电影" -> "电影片"
        "电视剧", "剧集" -> "连续剧"
        "番剧", "国漫", "动画", "动漫" -> "动漫片"
        "综艺" -> "综艺片"
        "纪录", "纪录片" -> "纪录片"
        else -> raw
    }

    companion object {
        private val PLAY_ID = Regex("""(?:^|/)play/([0-9a-fA-F]{16,})""")
        private val GROUP_ID = Regex("""-g(\d+)$""")
    }
}
