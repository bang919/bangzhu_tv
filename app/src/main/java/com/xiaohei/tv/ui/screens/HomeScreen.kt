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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.HomeSection
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.ui.LoadState
import com.xiaohei.tv.ui.MainTab
import com.xiaohei.tv.ui.components.AutoFocus
import com.xiaohei.tv.ui.components.ContinueCard
import com.xiaohei.tv.ui.components.ErrorState
import com.xiaohei.tv.ui.components.LoadingState
import com.xiaohei.tv.ui.components.MetaTag
import com.xiaohei.tv.ui.components.MoreCard
import com.xiaohei.tv.ui.components.PosterCard
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.SectionTitle
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PosterWidth = 116.dp
private val ContinueWidth = 240.dp

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    history: List<HistoryEntry>,
    onOpenDetail: (VideoCard) -> Unit,
    onPlayCard: (VideoCard) -> Unit,
    onResume: (HistoryEntry) -> Unit,
    onOpenTab: (MainTab) -> Unit,
) {
    var state by remember { mutableStateOf<LoadState<HomeData>>(LoadState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        state = LoadState.Loading
        state = try {
            LoadState.Success(viewModel.loadHome(force = reloadKey > 0))
        } catch (error: Exception) {
            LoadState.Failure(error.friendlyMessage())
        }
    }

    when (val current = state) {
        is LoadState.Loading -> LoadingState("正在加载首页推荐…")
        is LoadState.Failure -> ErrorState(current.message) { reloadKey++ }
        is LoadState.Success -> {
            val data = current.data
            val playFocus = remember { FocusRequester() }
            AutoFocus(playFocus, delays = listOf(160L, 700L, 2000L))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (data.hero != null) {
                    item(key = "hero-${data.hero.id}") {
                        HeroPanel(
                            hero = data.hero,
                            playFocus = playFocus,
                            onHeroFocus = {
                                if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            },
                            onPlay = { onPlayCard(data.hero) },
                            onDetail = { onOpenDetail(data.hero) },
                        )
                    }
                }

                if (history.isNotEmpty()) {
                    item(key = "continue") {
                        Column {
                            SectionTitle(
                                title = "继续观看",
                                trailing = "共 ${history.size} 条记录",
                                modifier = Modifier.padding(horizontal = ScreenPad),
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = ScreenPad),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(history.take(12), key = { "${it.providerId}-${it.videoId}" }) { entry ->
                                    ContinueCard(
                                        entry = entry,
                                        onClick = { onResume(entry) },
                                        modifier = Modifier.width(ContinueWidth),
                                    )
                                }
                            }
                        }
                    }
                }

                items(data.sections, key = { it.title }) { section ->
                    HomeSectionRow(
                        section = section,
                        onOpenDetail = onOpenDetail,
                        onMore = { onOpenTab(tabForSection(section.title)) },
                    )
                }

                if (data.sections.isEmpty() && data.hero == null) {
                    item { ErrorState("首页暂时没有内容") { reloadKey++ } }
                }
            }
        }
    }
}

private fun tabForSection(title: String): MainTab = when {
    title.contains("电影") -> MainTab.MOVIE
    title.contains("连续剧") || title.contains("电视剧") -> MainTab.SERIES
    title.contains("动漫") || title.contains("动画") -> MainTab.ANIME
    title.contains("综艺") -> MainTab.VARIETY
    title.contains("纪录") -> MainTab.DOCUMENTARY
    else -> MainTab.MOVIE
}

@Composable
private fun HeroPanel(
    hero: VideoCard,
    playFocus: FocusRequester,
    onHeroFocus: () -> Unit,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
) {
    val heroMinHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.30f).coerceIn(180.dp, 220.dp)
    val detailFocus = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = heroMinHeight)
            .background(Color(0xFF11151B)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(hero.poster)
                .crossfade(true)
                .build(),
            contentDescription = hero.title,
            contentScale = ContentScale.Crop,
            alpha = 0.55f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xF20B0D10),
                            Color(0xCC0B0D10),
                            Color(0x660B0D10),
                        )
                    )
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x330B0D10), BgDark)
                    )
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPad, end = ScreenPad, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(Accent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text("今日推荐", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                if (hero.note.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(hero.note, color = Gold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = hero.title,
                color = TextMain,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(hero.year, hero.area, hero.typeName)
                    .filter { it.isNotBlank() }
                    .forEach { MetaTag(it) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "立即播放",
                    onClick = onPlay,
                    primary = true,
                    icon = Icons.Filled.PlayArrow,
                    focusRequester = playFocus,
                    modifier = Modifier
                        .focusProperties { right = detailFocus }
                        .onFocusChanged { if (it.isFocused) onHeroFocus() },
                )
                TvButton(
                    text = "查看详情",
                    onClick = onDetail,
                    icon = Icons.Filled.Info,
                    focusRequester = detailFocus,
                    modifier = Modifier
                        .focusProperties { left = playFocus }
                        .onFocusChanged { if (it.isFocused) onHeroFocus() },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    onOpenDetail: (VideoCard) -> Unit,
    onMore: () -> Unit,
) {
    Column {
        SectionTitle(
            title = section.title,
            trailing = if (section.items.size > 12) "共 ${section.items.size} 部" else null,
            modifier = Modifier.padding(horizontal = ScreenPad),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.items.take(14), key = { it.id }) { card ->
                PosterCard(
                    card = card,
                    onClick = { onOpenDetail(card) },
                    modifier = Modifier.width(PosterWidth),
                )
            }
            item(key = "more") {
                MoreCard(
                    title = section.title,
                    onClick = onMore,
                    modifier = Modifier.width(PosterWidth),
                )
            }
        }
    }
}
