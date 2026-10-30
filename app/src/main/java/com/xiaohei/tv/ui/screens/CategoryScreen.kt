package com.xiaohei.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.ui.LoadState
import com.xiaohei.tv.ui.components.AutoFocus
import com.xiaohei.tv.ui.components.EmptyState
import com.xiaohei.tv.ui.components.ErrorState
import com.xiaohei.tv.ui.components.LoadingState
import com.xiaohei.tv.ui.components.PosterCard
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.TvFilterBar
import com.xiaohei.tv.ui.components.TvFilterOption
import com.xiaohei.tv.ui.components.TvFilterSpec
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CategoryScreen(
    viewModel: MainViewModel,
    type: VideoType,
    detailOpen: Boolean,
    onOpenDetail: (VideoCard) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providerId = settings.providerId.ifBlank { viewModel.defaultProviderId }
    val supported = viewModel.supportsType(type)
    var href by remember(type, providerId) { mutableStateOf<String?>(null) }
    var state by remember(type, providerId) {
        mutableStateOf<LoadState<CategoryPage>>(LoadState.Loading)
    }
    var reloadKey by remember(type, providerId) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val restoreFocus = remember { FocusRequester() }
    var lastOpenedIndex by remember(type, providerId) { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch {
            delay(60)
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(detailOpen) {
        if (!detailOpen && lastOpenedIndex >= 0) {
            runCatching { restoreFocus.requestFocus() }
        }
    }

    LaunchedEffect(type, providerId, href, reloadKey) {
        if (!supported) return@LaunchedEffect
        state = LoadState.Loading
        state = try {
            LoadState.Success(viewModel.loadCategory(type, href))
        } catch (error: Exception) {
            LoadState.Failure(error.friendlyMessage())
        }
    }

    if (!supported) {
        EmptyState("该视频源不支持此功能")
        return
    }

    when (val current = state) {
        is LoadState.Loading -> LoadingState("正在加载${type.label}…")
        is LoadState.Failure -> ErrorState(current.message) { reloadKey++ }
        is LoadState.Success -> {
            val page = current.data
            val sort = viewModel.categorySortOf(page.currentHref)
            LaunchedEffect(page.currentHref) {
                delay(220)
                runCatching { gridState.scrollToItem(0) }
            }
            if (page.items.isNotEmpty()) AutoFocus(firstFocus)
            CatalogGrid(
                page = page,
                showRank = false,
                gridState = gridState,
                firstFocus = firstFocus,
                onOpenDetail = { card, index ->
                    lastOpenedIndex = index
                    onOpenDetail(card)
                },
                focusRestoreIndex = lastOpenedIndex,
                focusRestore = restoreFocus,
                header = {
                    CatalogHeader(
                        title = page.title.ifBlank { type.label },
                        subtitle = "第 ${page.currentPage} / ${page.totalPages} 页 · 共 ${page.items.size} 部",
                    )
                    Spacer(Modifier.height(10.dp))
                    TvFilterBar(
                        specs = buildList {
                            add(
                                TvFilterSpec(
                                    key = "sort",
                                    label = "排序",
                                    current = sort.label,
                                    options = viewModel.categorySorts.map { option ->
                                        TvFilterOption(option.label, option == sort) {
                                            href = viewModel.categoryHref(type, option)
                                        }
                                    },
                                )
                            )
                            page.filters.filterNot { it.label.contains("排序") }.forEachIndexed { index, group ->
                                add(
                                    TvFilterSpec(
                                        key = "filter-$index-${group.label}",
                                        label = group.label,
                                        current = group.selectedLabel,
                                        options = group.options.map { option ->
                                            TvFilterOption(option.label, option.selected) {
                                                href = option.href
                                            }
                                        },
                                    )
                                )
                            }
                        },
                        onFocus = scrollToTop,
                    )
                },
                pager = {
                    PagerBar(
                        page = page,
                        onPrev = { page.prevHref?.let { href = it } },
                        onNext = { page.nextHref?.let { href = it } },
                    )
                },
            )
        }
    }
}

@Composable
fun RankingScreen(
    viewModel: MainViewModel,
    onOpenDetail: (VideoCard) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val providerId = settings.providerId.ifBlank { viewModel.defaultProviderId }
    val supported = viewModel.supportsRanking
    var type by remember { mutableStateOf(VideoType.MOVIE) }
    var sort by remember { mutableStateOf(RankingSort.POPULAR) }
    var state by remember(providerId) { mutableStateOf<LoadState<CategoryPage>>(LoadState.Loading) }
    var reloadKey by remember(providerId) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch {
            delay(60)
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(type, sort, providerId, reloadKey) {
        if (!supported) return@LaunchedEffect
        state = LoadState.Loading
        state = try {
            LoadState.Success(viewModel.loadRanking(type, sort))
        } catch (error: Exception) {
            LoadState.Failure(error.friendlyMessage())
        }
    }

    if (!supported) {
        EmptyState("该视频源不支持此功能")
        return
    }

    when (val current = state) {
        is LoadState.Loading -> LoadingState("正在加载榜单…")
        is LoadState.Failure -> ErrorState(current.message) { reloadKey++ }
        is LoadState.Success -> {
            val page = current.data
            LaunchedEffect(page.currentHref) {
                delay(220)
                runCatching { gridState.scrollToItem(0) }
            }
            if (page.items.isNotEmpty()) AutoFocus(firstFocus)
            CatalogGrid(
                page = page,
                showRank = true,
                gridState = gridState,
                firstFocus = firstFocus,
                onOpenDetail = { card, _ -> onOpenDetail(card) },
                header = {
                    CatalogHeader(
                        title = "${type.label}${sort.label}",
                        subtitle = "实时榜单 · 共 ${page.items.size} 部",
                    )
                    Spacer(Modifier.height(10.dp))
                    TvFilterBar(
                        specs = listOf(
                            TvFilterSpec(
                                key = "type",
                                label = "类型",
                                current = type.label,
                                options = VideoType.entries.map { option ->
                                    TvFilterOption(option.label, option == type) { type = option }
                                },
                            ),
                            TvFilterSpec(
                                key = "sort",
                                label = "榜单",
                                current = sort.label,
                                options = viewModel.rankingSorts.map { option ->
                                    TvFilterOption(option.label, option == sort) { sort = option }
                                },
                            ),
                        ),
                        onFocus = scrollToTop,
                    )
                },
                pager = null,
            )
        }
    }
}

@Composable
private fun CatalogHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                color = TextMain,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CatalogGrid(
    page: CategoryPage,
    showRank: Boolean,
    gridState: LazyGridState,
    firstFocus: FocusRequester,
    onOpenDetail: (VideoCard, Int) -> Unit,
    header: @Composable () -> Unit,
    pager: (@Composable () -> Unit)?,
    focusRestoreIndex: Int = -1,
    focusRestore: FocusRequester? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(116.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(start = ScreenPad, end = ScreenPad, top = 18.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Column { header() }
        }

        if (page.items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("当前筛选下暂无内容，换个条件试试", color = TextDim, fontSize = 16.sp)
                }
            }
        } else {
            itemsIndexed(page.items, key = { index, card -> "${page.currentHref}#$index#${card.id}" }) { index, card ->
                PosterCard(
                    card = card,
                    onClick = { onOpenDetail(card, index) },
                    rank = if (showRank) index + 1 else null,
                    focusRequester = when {
                        index == focusRestoreIndex -> focusRestore
                        index == 0 -> firstFocus
                        else -> null
                    },
                    subtitle = listOf(card.year, card.area)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { card.typeName },
                )
            }
        }

        if (pager != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "pager") {
                Box(Modifier.padding(top = 4.dp)) { pager() }
            }
        }
    }
}

@Composable
private fun PagerBar(page: CategoryPage, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page.prevHref != null) {
            TvButton("上一页", onClick = onPrev)
        }
        Text(
            text = "第 ${page.currentPage} / ${page.totalPages} 页",
            color = TextMain,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        if (page.nextHref != null) {
            TvButton("下一页", onClick = onNext, primary = true)
        }
    }
}
