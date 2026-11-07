package com.xiaohei.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.ui.LoadState
import com.xiaohei.tv.ui.components.Chip
import com.xiaohei.tv.ui.components.ErrorState
import com.xiaohei.tv.ui.components.InputTicker
import com.xiaohei.tv.ui.components.LoadingState
import com.xiaohei.tv.ui.components.PosterCard
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.TvFilterBar
import com.xiaohei.tv.ui.components.TvFilterOption
import com.xiaohei.tv.ui.components.TvFilterSpec
import com.xiaohei.tv.ui.components.TvIconButton
import com.xiaohei.tv.ui.components.TvTextField
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HOT_KEYWORDS = listOf(
    "猫和老鼠", "繁花", "狂飙", "庆余年", "与凤行", "甄嬛传",
    "斗罗大陆", "海贼王", "三体", "流浪地球", "长津湖", "好东西",
)
private val TYPE_OPTIONS = listOf("全部", "电影片", "连续剧", "动漫片", "综艺片", "纪录片")
private val YEAR_OPTIONS = listOf("全部", "2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018")
private val AREA_OPTIONS = listOf(
    "全部", "内地", "香港", "台湾", "美国", "日本", "韩国",
    "英国", "法国", "泰国", "印度", "其他",
)

class SearchUiState {
    var query by mutableStateOf("")
    var activeKeyword by mutableStateOf("")
    var typeFilter by mutableStateOf("全部")
    var yearFilter by mutableStateOf("全部")
    var areaFilter by mutableStateOf("全部")
    var state by mutableStateOf<LoadState<List<VideoCard>>?>(null)
    var reloadKey by mutableIntStateOf(0)
    var focusTick by mutableIntStateOf(0)
    var loadedKeyword by mutableStateOf("")
    var page by mutableIntStateOf(1)
    var hasMore by mutableStateOf(false)
    val recentSearches = mutableStateListOf<String>()

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        query = keyword
        if (trimmed == activeKeyword && state is LoadState.Success) {
            focusTick++
            return
        }
        activeKeyword = trimmed
        page = 1
        hasMore = false
        loadedKeyword = ""
        state = null
        if (!recentSearches.contains(trimmed)) {
            recentSearches.add(0, trimmed)
            while (recentSearches.size > 8) {
                recentSearches.removeAt(recentSearches.lastIndex)
            }
        }
    }

    fun reset() {
        query = ""
        activeKeyword = ""
        typeFilter = "全部"
        yearFilter = "全部"
        areaFilter = "全部"
        state = null
        loadedKeyword = ""
        page = 1
        hasMore = false
        reloadKey = 0
    }
}

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    ui: SearchUiState,
    onOpenDetail: (VideoCard) -> Unit,
    onClose: () -> Unit,
) {
    val inputFocus = remember { FocusRequester() }
    val firstKeywordFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch {
            delay(60)
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(ui.activeKeyword, ui.reloadKey, ui.page) {
        val keyword = ui.activeKeyword
        if (keyword.isBlank()) {
            ui.state = null
            ui.loadedKeyword = ""
            return@LaunchedEffect
        }
        ui.state = LoadState.Loading
        ui.state = try {
            val page = viewModel.searchPage(keyword, ui.page)
            ui.hasMore = page.hasMore
            LoadState.Success(page.items)
        } catch (error: Exception) {
            LoadState.Failure(error.friendlyMessage())
        }
        ui.loadedKeyword = keyword
        ui.focusTick++
    }

    LaunchedEffect(Unit) {
        val hasResults = ui.state is LoadState.Success &&
            (ui.filteredResults()?.isNotEmpty() == true)
        val startTick = InputTicker.tick
        listOf(120L, 450L, 1000L).forEach { wait ->
            delay(wait)
            if (InputTicker.tick == startTick) {
                runCatching {
                    if (hasResults) firstResultFocus.requestFocus() else inputFocus.requestFocus()
                }
            }
        }
    }

    LaunchedEffect(ui.focusTick) {
        if (ui.focusTick == 0) return@LaunchedEffect
        val startTick = InputTicker.tick
        delay(160)
        if (InputTicker.tick == startTick) {
            runCatching { gridState.animateScrollToItem(0) }
            runCatching { firstResultFocus.requestFocus() }
        }
    }

    val allResults = (ui.state as? LoadState.Success)?.data.orEmpty()
    val filtered = allResults.filter { card ->
        matchesType(card.typeName, ui.typeFilter) &&
            (ui.yearFilter == "全部" || card.year == ui.yearFilter) &&
            matchesArea(card.area, ui.areaFilter)
    }
    val hasActiveFilter = ui.typeFilter != "全部" || ui.yearFilter != "全部" || ui.areaFilter != "全部"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPad, end = ScreenPad, top = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onClose,
            )
            Spacer(Modifier.width(16.dp))
            if (LocalConfiguration.current.screenWidthDp >= 600) Text(
                text = "搜索",
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(if (LocalConfiguration.current.screenWidthDp < 600) 0.dp else 24.dp))
            TvTextField(
                value = ui.query,
                onValueChange = { ui.query = it },
                onSearch = { ui.submit(ui.query) },
                placeholder = "输入影片名称，如：猫和老鼠",
                modifier = Modifier
                    .weight(1f)
                    .focusProperties { down = firstKeywordFocus },
                focusRequester = inputFocus,
            )
            Spacer(Modifier.width(14.dp))
            TvButton(
                text = "搜索",
                icon = if (LocalConfiguration.current.screenWidthDp < 600) null else Icons.Filled.Search,
                onClick = { ui.submit(ui.query) },
                primary = true,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(116.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(start = ScreenPad, end = ScreenPad, top = 6.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    KeywordRow(
                        label = "热门",
                        keywords = HOT_KEYWORDS,
                        onPick = { ui.submit(it) },
                        firstFocus = firstKeywordFocus,
                        onFocus = scrollToTop,
                    )
                    if (ui.recentSearches.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        KeywordRow(
                            label = "最近",
                            keywords = ui.recentSearches.toList(),
                            onPick = { ui.submit(it) },
                            onFocus = scrollToTop,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (viewModel.supportsSearchFilters) {
                        TvFilterBar(
                            specs = listOf(
                                TvFilterSpec(
                                    key = "type",
                                    label = "类型",
                                    current = ui.typeFilter,
                                    options = TYPE_OPTIONS.map { option ->
                                        TvFilterOption(option, ui.typeFilter == option) {
                                            ui.typeFilter = option
                                        }
                                    },
                                ),
                                TvFilterSpec(
                                    key = "year",
                                    label = "年份",
                                    current = ui.yearFilter,
                                    options = YEAR_OPTIONS.map { option ->
                                        TvFilterOption(option, ui.yearFilter == option) {
                                            ui.yearFilter = option
                                        }
                                    },
                                ),
                                TvFilterSpec(
                                    key = "area",
                                    label = "地区",
                                    current = ui.areaFilter,
                                    options = AREA_OPTIONS.map { option ->
                                        TvFilterOption(option, ui.areaFilter == option) {
                                            ui.areaFilter = option
                                        }
                                    },
                                ),
                            ),
                            onFocus = scrollToTop,
                        )
                    } else {
                        Text(
                            text = "该视频源不支持此功能",
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (ui.activeKeyword.isNotBlank() && ui.state is LoadState.Success) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "「${ui.activeKeyword}」本页 ${filtered.size} 个结果" +
                                    if (filtered.size != allResults.size) "（本页全部 ${allResults.size} 个）" else "",
                                color = TextDim,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (hasActiveFilter) {
                                TvButton(
                                    text = "清空筛选",
                                    onClick = {
                                        ui.typeFilter = "全部"
                                        ui.yearFilter = "全部"
                                        ui.areaFilter = "全部"
                                    },
                                )
                            }
                        }
                    }
                }
            }

            when (val current = ui.state) {
                null -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(SurfaceDark.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0xFF232A35), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFF232A35), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = TextDim,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "输入片名，或从热门、最近里选一个",
                                    color = TextMain,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "支持电影、连续剧、动漫、综艺、纪录片",
                                    color = TextDim,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                is LoadState.Loading -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            LoadingState("正在搜索「${ui.activeKeyword}」…")
                        }
                    }
                }
                is LoadState.Failure -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            ErrorState(current.message) { ui.reloadKey++ }
                        }
                    }
                }
                is LoadState.Success -> {
                    if (filtered.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (allResults.isEmpty()) {
                                        "没有找到「${ui.activeKeyword}」，换个关键字试试"
                                    } else {
                                        "当前筛选下没有结果"
                                    },
                                    color = TextDim,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { "${it.providerId}-${it.id}" }) { card ->
                            PosterCard(
                                card = card,
                                onClick = { onOpenDetail(card) },
                                subtitle = listOf(card.typeName, card.year, card.area)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                focusRequester = if (card == filtered.first()) firstResultFocus else null,
                            )
                        }
                    }
                    if (ui.page > 1 || ui.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (ui.page > 1) TvButton(text = "上一页", onClick = { ui.page-- })
                                Text("第 ${ui.page} 页", color = TextDim)
                                if (ui.hasMore) TvButton(text = "下一页", onClick = { ui.page++ })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun SearchUiState.filteredResults(): List<VideoCard>? {
    val data = (state as? LoadState.Success)?.data ?: return null
    return data.filter { card ->
        matchesType(card.typeName, typeFilter) &&
            (yearFilter == "全部" || card.year == yearFilter) &&
            matchesArea(card.area, areaFilter)
    }
}

@Composable
private fun KeywordRow(
    label: String,
    keywords: List<String>,
    onPick: (String) -> Unit,
    firstFocus: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextDim,
            fontSize = 14.sp,
            modifier = Modifier.width(46.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lazyRowItems(keywords, key = { it }) { keyword ->
                Chip(
                    text = keyword,
                    onClick = { onPick(keyword) },
                    focusRequester = if (firstFocus != null && keyword == keywords.first()) firstFocus else null,
                    modifier = if (onFocus != null) {
                        Modifier.onFocusChanged { if (it.isFocused) onFocus() }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private fun matchesType(typeName: String, option: String): Boolean {
    if (option == "全部") return true
    val name = typeName.trim()
    return when (option) {
        "电影片" -> name == "动画电影" ||
            (name.endsWith("片") && !name.contains("动漫") && !name.contains("综艺") && !name.contains("纪录"))
        "连续剧" -> name.contains("剧") && !name.contains("片") && !name.contains("综艺")
        "动漫片" -> name.contains("动漫") || name.contains("动画")
        "综艺片" -> name.contains("综艺")
        "纪录片" -> name.contains("纪录")
        else -> name == option
    }
}

private val KNOWN_AREAS = listOf("大陆", "内地", "香港", "台湾", "美国", "日本", "韩国", "英国", "法国", "泰国", "印度")

private fun matchesArea(area: String, option: String): Boolean {
    if (option == "全部") return true
    if (option == "其他") return KNOWN_AREAS.none { area.contains(it) }
    if (option == "大陆" || option == "内地") return area.contains("大陆") || area.contains("内地")
    return area.contains(option)
}
