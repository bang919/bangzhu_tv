package com.xiaohei.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaohei.tv.data.local.EpisodeProgress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.ui.LoadState
import com.xiaohei.tv.ui.components.AutoFocus
import com.xiaohei.tv.ui.components.Chip
import com.xiaohei.tv.ui.components.ErrorState
import com.xiaohei.tv.ui.components.LoadingState
import com.xiaohei.tv.ui.components.MetaTag
import com.xiaohei.tv.ui.components.PosterCard
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.SectionTitle
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.TvIconButton
import com.xiaohei.tv.ui.components.formatDuration
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.Divider
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    viewModel: MainViewModel,
    videoId: String,
    providerId: String,
    resumeEntry: HistoryEntry?,
    onOpenDetail: (VideoCard) -> Unit,
    onPlay: (VideoDetail, Int, Int, Long) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(videoId, providerId) { mutableStateOf<LoadState<VideoDetail>>(LoadState.Loading) }
    var reloadKey by remember(videoId, providerId) { mutableIntStateOf(0) }
    var versions by remember(videoId, providerId) { mutableStateOf<List<VideoCard>>(emptyList()) }
    val playFocus = remember { FocusRequester() }
    val episodeFocus = remember { FocusRequester() }

    LaunchedEffect(videoId, providerId, reloadKey) {
        state = LoadState.Loading
        state = try {
            LoadState.Success(viewModel.loadDetail(videoId, providerId))
        } catch (error: Exception) {
            LoadState.Failure(error.friendlyMessage())
        }
    }

    when (val current = state) {
        is LoadState.Loading -> LoadingState("正在加载影片详情…")
        is LoadState.Failure -> ErrorState(current.message) { reloadKey++ }
        is LoadState.Success -> {
            val detail = current.data
            var sourceIndex by remember(detail.id) { mutableIntStateOf(0) }
            var episodeIndex by remember(detail.id) { mutableIntStateOf(0) }
            val entry = resumeEntry?.takeIf {
                it.videoId == detail.id && (it.providerId == detail.providerId || it.providerId.isBlank())
            }

            LaunchedEffect(detail.id) {
                versions = runCatching { viewModel.loadVersions(detail) }.getOrDefault(emptyList())
            }
            LaunchedEffect(detail.id, entry?.episodeHref) {
                if (entry == null) return@LaunchedEffect
                detail.sources.forEachIndexed { si, source ->
                    val ei = source.episodes.indexOfFirst { it.href == entry.episodeHref }
                    if (ei >= 0) {
                        sourceIndex = si
                        episodeIndex = ei
                        return@LaunchedEffect
                    }
                }
                sourceIndex = entry.sourceIndex.coerceIn(0, (detail.sources.size - 1).coerceAtLeast(0))
                episodeIndex = entry.episodeIndex.coerceIn(
                    0,
                    (detail.sources.getOrNull(sourceIndex)?.episodes?.lastIndex ?: 0).coerceAtLeast(0),
                )
            }
            val selectedSource = detail.sources.getOrNull(sourceIndex)
            val selectedEpisode = selectedSource?.episodes?.getOrNull(episodeIndex)
            val listState = rememberLazyListState()
            var episodesOpen by remember(detail.id) { mutableStateOf(false) }
            val episodeRecords by remember(detail.id, detail.providerId) { viewModel.episodeProgress(detail.providerId, detail.id) }.collectAsState(initial = emptyList())

            LaunchedEffect(detail.id) {
                delay(220)
                listState.scrollToItem(0)
            }
            AutoFocus(playFocus)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        item(key = "header") {
                            DetailHeader(
                                detail = detail,
                                entry = entry,
                                playFocus = playFocus,
                                episodeFocus = episodeFocus,
                                onBack = onBack,
                                episodeCount = selectedSource?.episodes?.size ?: 0,
                                onOpenEpisodes = { episodesOpen = true },
                                onPlayResume = {
                                    if (selectedEpisode != null) {
                                        val position = entry
                                            ?.takeIf { it.episodeHref == selectedEpisode.href }
                                            ?.positionMs ?: 0L
                                        onPlay(detail, sourceIndex, episodeIndex, position)
                                    } else if (entry != null) {
                                        onPlay(detail, entry.sourceIndex, entry.episodeIndex, entry.positionMs)
                                    }
                                },
                                onPlayFirst = {
                                    onPlay(detail, 0, 0, 0)
                                },
                            )
                        }

                        if (versions.isNotEmpty()) {
                            item(key = "versions") {
                                Column {
                                    SectionTitle(
                                        title = "版本 / 语言",
                                        trailing = "共 ${versions.size} 个版本",
                                        modifier = Modifier.padding(horizontal = ScreenPad),
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = ScreenPad),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(versions, key = { it.id }) { version ->
                                            Chip(
                                                text = version.title,
                                                onClick = { onOpenDetail(version) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (detail.related.isNotEmpty()) {
                            item(key = "related") {
                                Column {
                                    SectionTitle(
                                        title = "相关推荐",
                                        modifier = Modifier.padding(horizontal = ScreenPad),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = ScreenPad),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        items(detail.related, key = { "${it.providerId}-${it.id}" }) { card ->
                                            PosterCard(
                                                card = card,
                                                onClick = { onOpenDetail(card) },
                                                modifier = Modifier.width(116.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }


                }
            }
            if (episodesOpen) {
                Dialog(onDismissRequest = { episodesOpen = false; runCatching { episodeFocus.requestFocus() } }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    EpisodePanel(detail, sourceIndex, episodeIndex,
                        onSelectSource = { sourceIndex = it; episodeIndex = 0 },
                        onSelectEpisode = { index ->
                            episodesOpen = false
                            val href = detail.sources[sourceIndex].episodes[index].href
                            onPlay(detail, sourceIndex, index, episodeRecords.firstOrNull { it.episodeHref == href }?.positionMs ?: 0L)
                        },
                        selectedFocus = remember { FocusRequester() },
                        records = episodeRecords,
                        modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f))
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    detail: VideoDetail,
    entry: HistoryEntry?,
    playFocus: FocusRequester,
    episodeFocus: FocusRequester?,
    onBack: (() -> Unit)?,
    episodeCount: Int,
    onOpenEpisodes: () -> Unit,
    onPlayResume: () -> Unit,
    onPlayFirst: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(detail.poster)
                .crossfade(true)
                .build(),
            contentDescription = detail.title,
            contentScale = ContentScale.Crop,
            alpha = 0.4f,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFA0B0D10), Color(0xDD0B0D10), Color(0x990B0D10))
                    )
                ),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x660B0D10), Color(0x330B0D10), BgDark)
                    )
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPad, end = ScreenPad, top = 28.dp, bottom = 24.dp),
        ) {
            if (onBack != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(172.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B2029)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(detail.poster).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(26.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title,
                        color = TextMain,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 39.sp,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(detail.typeName, detail.area, detail.year, detail.language)
                            .filter { it.isNotBlank() }
                            .forEach { MetaTag(it) }
                        if (detail.remarks.isNotBlank()) MetaTag(detail.remarks)
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (detail.score.isNotBlank()) {
                            Text(
                                text = detail.score,
                                color = Gold,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        Text(
                            text = listOf(
                                detail.director.takeIf { it.isNotBlank() }?.let { "导演：$it" },
                                detail.actors.takeIf { it.isNotBlank() }?.let { "主演：$it" },
                            ).filterNotNull().joinToString("   "),
                            color = TextDim,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (detail.description.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = detail.description,
                            color = TextDim,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton(
                            text = if (entry != null) {
                                "继续观看 ${entry.episodeName.ifBlank { "正片" }} · ${formatDuration(entry.positionMs)}"
                            } else {
                                "立即播放"
                            },
                            onClick = onPlayResume,
                            primary = true,
                            icon = Icons.Filled.PlayArrow,
                            focusRequester = playFocus,
                            modifier = if (entry == null && episodeFocus != null) {
                                Modifier.focusProperties { right = episodeFocus }
                            } else {
                                Modifier
                            },
                        )
                        if (entry != null) {
                            TvButton(
                                text = "从头播放",
                                onClick = onPlayFirst,
                                modifier = if (episodeFocus != null) {
                                    Modifier.focusProperties { right = episodeFocus }
                                } else {
                                    Modifier
                                },
                            )
                        }
                        if (episodeCount > 0) {
                            TvButton(text = "选集：共${episodeCount}集", onClick = onOpenEpisodes, focusRequester = episodeFocus)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineEpisodes(
    detail: VideoDetail,
    sourceIndex: Int,
    episodeIndex: Int,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    selectedFocus: FocusRequester,
) {
    Column(modifier = Modifier.padding(horizontal = ScreenPad)) {
        SectionTitle(
            title = "播放列表",
            trailing = detail.sources.getOrNull(sourceIndex)?.let { "共 ${it.episodes.size} 集" },
        )
        if (detail.sources.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(detail.sources) { index, source ->
                    Chip(
                        text = source.name.ifBlank { "线路${index + 1}" },
                        selected = index == sourceIndex,
                        onClick = { onSelectSource(index) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 12,
        ) {
            detail.sources[sourceIndex].episodes.forEachIndexed { index, episode ->
                Chip(
                    text = episode.name,
                    selected = index == episodeIndex,
                    onClick = { onSelectEpisode(index) },
                    focusRequester = if (index == episodeIndex) selectedFocus else null,
                )
            }
        }
    }
}

@Composable
private fun EpisodePanel(
    detail: VideoDetail,
    sourceIndex: Int,
    episodeIndex: Int,
    onSelectSource: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    selectedFocus: FocusRequester,
    records: List<EpisodeProgress> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val source = detail.sources.getOrNull(sourceIndex)
    val episodes = source?.episodes.orEmpty()
    val gridState = rememberLazyGridState()

    LaunchedEffect(sourceIndex, episodeIndex) {
        if (episodes.isNotEmpty()) {
            runCatching { gridState.scrollToItem(episodeIndex.coerceIn(0, episodes.lastIndex)) }
            delay(500)
            runCatching { selectedFocus.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF10141A))
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "选集",
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (episodes.isEmpty()) "暂无播放源" else "共 ${episodes.size} 集",
                color = TextDim,
                fontSize = 13.sp,
            )
        }
        if (detail.sources.size > 1) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(detail.sources) { index, item ->
                    Chip(
                        text = item.name.ifBlank { "线路${index + 1}" },
                        selected = index == sourceIndex,
                        onClick = { onSelectSource(index) },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = episodes.size,
                key = { index -> "$index-${episodes[index].href}" },
            ) { index ->
                val episode = episodes[index]
                val record = records.firstOrNull { it.episodeHref == episode.href }
                Chip(
                    text = episode.name,
                    selected = index == episodeIndex,
                    onClick = { onSelectEpisode(index) },
                    focusRequester = if (index == episodeIndex) selectedFocus else null,
                    progress = record?.progress,
                    completed = record != null && if (index == episodes.lastIndex) {
                        record.progress > 0.85f
                    } else {
                        record.progress > 0.75f && (records.firstOrNull { it.episodeHref == episodes[index + 1].href }?.positionMs ?: 0L) > 0L
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                )
            }
        }
    }
}
