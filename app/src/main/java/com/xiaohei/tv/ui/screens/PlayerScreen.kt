package com.xiaohei.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.SkipTimes
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.net.TlsCompat
import com.xiaohei.tv.ui.LoadState
import com.xiaohei.tv.ui.components.Chip
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.TvIconButton
import com.xiaohei.tv.ui.components.formatDuration
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.Divider
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.SurfaceHigh
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private enum class PlayerPanel { None, Episodes, Sources, Versions, Tracks }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    initialDetail: VideoDetail,
    initialSourceIndex: Int,
    initialEpisodeIndex: Int,
    initialPosition: Long,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf(initialDetail) }
    var sourceIndex by remember {
        mutableIntStateOf(initialSourceIndex.coerceIn(0, (initialDetail.sources.size - 1).coerceAtLeast(0)))
    }
    var episodeIndex by remember {
        mutableIntStateOf(initialEpisodeIndex.coerceAtLeast(0))
    }
    var versions by remember { mutableStateOf<List<VideoCard>>(emptyList()) }
    var streamState by remember { mutableStateOf<LoadState<StreamInfo>>(LoadState.Loading) }
    var panel by remember { mutableStateOf(PlayerPanel.None) }
    var controlsVisible by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var position by remember { mutableLongStateOf(initialPosition) }
    var duration by remember { mutableLongStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var hasStarted by remember { mutableStateOf(false) }
    var seekHintText by remember { mutableStateOf<String?>(null) }
    var seekHintTick by remember { mutableIntStateOf(0) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var selectedVideoKey by remember { mutableStateOf<String?>(null) }
    var selectedAudioKey by remember { mutableStateOf<String?>(null) }
    var selectedTextKey by remember { mutableStateOf<String?>(null) }
    var skipTimes by remember(detail.id) { mutableStateOf(viewModel.skipTimesFor(detail.id)) }

    val rootFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true))
        }
    }

    val exo = remember {
        val client = TlsCompat.configure(OkHttpClient.Builder()).build()
        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .setDefaultRequestProperties(
                mapOf("Referer" to viewModel.refererFor(initialDetail.providerId))
            )
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context, mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply { playWhenReady = true }
    }

    val videoChoices = remember(tracks) { videoChoicesOf(tracks) }
    val audioChoices = remember(tracks) { audioChoicesOf(tracks) }
    val textChoices = remember(tracks) { textChoicesOf(tracks) }

    val currentSource = detail.sources.getOrNull(sourceIndex)
    val currentEpisode = currentSource?.episodes?.getOrNull(episodeIndex)
    val hasNext = currentSource?.episodes?.let { episodeIndex + 1 < it.size } == true

    val latestSave = rememberUpdatedState(
        SaveSnapshot(detail, sourceIndex, episodeIndex, position, duration)
    )

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    duration = exo.duration.coerceAtLeast(0)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(playerTracks: Tracks) {
                tracks = playerTracks
            }

            override fun onPlayerError(playerError: PlaybackException) {
                error = "播放失败：${playerError.errorCodeName}"
                controlsVisible = true
            }
        }
        exo.addListener(listener)
        onDispose {
            val snapshot = latestSave.value
            viewModel.saveProgress(
                snapshot.detail,
                snapshot.sourceIndex,
                snapshot.episodeIndex,
                snapshot.position,
                snapshot.duration,
            )
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(detail.id, detail.providerId) {
        versions = runCatching { viewModel.loadVersions(detail) }.getOrDefault(emptyList())
    }

    LaunchedEffect(detail.id, detail.providerId, sourceIndex, episodeIndex, reloadKey) {
        error = null
        val episode = detail.sources.getOrNull(sourceIndex)?.episodes?.getOrNull(episodeIndex)
        if (episode == null) {
            streamState = LoadState.Failure("没有可播放的剧集")
            return@LaunchedEffect
        }
        streamState = LoadState.Loading
        tracks = null
        selectedVideoKey = null
        selectedAudioKey = null
        selectedTextKey = null
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        )
        exo.stop()
        exo.clearMediaItems()
        try {
            val info = viewModel.resolveStream(episode.href, detail.providerId)
            streamState = LoadState.Success(info)
            exo.setMediaItem(MediaItem.fromUri(info.url))
            exo.prepare()
            val resumeAt = if (!hasStarted) initialPosition else 0L
            hasStarted = true
            val startAt = maxOf(resumeAt, skipTimes.introMs).coerceAtLeast(0)
            if (startAt > 0) exo.seekTo(startAt)
            exo.playWhenReady = true
        } catch (loadError: Exception) {
            streamState = LoadState.Failure(loadError.friendlyMessage())
        }
    }

    LaunchedEffect(detail.id, sourceIndex, episodeIndex) {
        while (true) {
            if (exo.isPlaying || exo.playbackState == Player.STATE_READY) {
                val currentPosition = exo.currentPosition.coerceAtLeast(0)
                val currentDuration = exo.duration.coerceAtLeast(0)
                position = currentPosition
                duration = currentDuration
                val outro = skipTimes.outroMs
                if (!controlsVisible && hasNext && outro > 0 && currentDuration > outro &&
                    currentPosition >= currentDuration - outro
                ) {
                    episodeIndex += 1
                    return@LaunchedEffect
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(detail.id, sourceIndex, episodeIndex) {
        while (true) {
            delay(5000)
            if (position > 0) {
                viewModel.saveProgress(detail, sourceIndex, episodeIndex, position, duration)
            }
        }
    }

    LaunchedEffect(playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            if (hasNext) {
                episodeIndex += 1
            } else {
                controlsVisible = true
                runCatching { playPauseFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(controlsVisible, panel, isPlaying, interactionTick, error) {
        if (controlsVisible && panel == PlayerPanel.None && isPlaying && error == null) {
            delay(7000)
            controlsVisible = false
            runCatching { rootFocus.requestFocus() }
        }
    }

    LaunchedEffect(controlsVisible, panel, error, streamState) {
        if (error != null || streamState is LoadState.Failure) {
            controlsVisible = true
            delay(120)
            runCatching { retryFocus.requestFocus() }
        } else if (controlsVisible && panel == PlayerPanel.None) {
            delay(120)
            runCatching { playPauseFocus.requestFocus() }
        }
    }

    LaunchedEffect(seekHintTick) {
        if (seekHintTick == 0) return@LaunchedEffect
        delay(1400)
        seekHintText = null
    }

    LaunchedEffect(controlsVisible, panel) {
        if (!controlsVisible || panel != PlayerPanel.None) seekBarFocused = false
    }

    fun showControls() {
        controlsVisible = true
        interactionTick++
    }

    fun closePanel() {
        panel = PlayerPanel.None
        interactionTick++
    }

    fun seekRelative(deltaMs: Long, label: String) {
        val max = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (exo.currentPosition + deltaMs).coerceIn(0, max)
        exo.seekTo(target)
        position = target
        seekHintText = "$label · ${formatDuration(target)}"
        seekHintTick++
    }

    fun updateSkipTimes(next: SkipTimes) {
        if (next == skipTimes) return
        skipTimes = next
        viewModel.saveSkipTimes(detail.id, next)
        showControls()
    }

    fun applyVideoChoice(choice: TrackChoice?) {
        selectedVideoKey = choice?.key
        val params = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (choice != null) {
            params.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        }
        trackSelector.setParameters(params.build())
    }

    fun applyAudioChoice(choice: TrackChoice?) {
        selectedAudioKey = choice?.key
        val params = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        if (choice != null) {
            params.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        }
        trackSelector.setParameters(params.build())
    }

    fun applyTextChoice(choice: TrackChoice?) {
        selectedTextKey = choice?.key
        val params = trackSelector.buildUponParameters()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, choice == null)
        if (choice != null) {
            params.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        }
        trackSelector.setParameters(params.build())
    }

    BackHandler(enabled = panel != PlayerPanel.None) { closePanel() }
    BackHandler(enabled = panel == PlayerPanel.None && controlsVisible) {
        controlsVisible = false
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                interactionTick++
                if (!controlsVisible) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            seekRelative(-10_000, "快退 10 秒")
                            true
                        }
                        Key.DirectionRight -> {
                            seekRelative(10_000, "快进 10 秒")
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            showControls()
                            true
                        }
                        Key.Back -> false
                        else -> {
                            showControls()
                            true
                        }
                    }
                } else {
                    false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = exo
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (streamState is LoadState.Loading || (isPlaying.not() && playbackState == Player.STATE_BUFFERING)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (streamState is LoadState.Loading) "正在解析播放地址…" else "缓冲中…",
                        color = TextDim,
                        fontSize = 16.sp,
                    )
                    currentEpisode?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it.name, color = TextMain, fontSize = 15.sp)
                    }
                }
            }
        }

        if (error != null) {
            PlayerErrorOverlay(
                message = error!!,
                retryFocus = retryFocus,
                onRetry = {
                    error = null
                    reloadKey++
                },
                onClose = onClose,
            )
        } else if (streamState is LoadState.Failure && error == null) {
            PlayerErrorOverlay(
                message = (streamState as LoadState.Failure).message,
                retryFocus = retryFocus,
                onRetry = { reloadKey++ },
                onClose = onClose,
            )
        }

        if (controlsVisible && panel == PlayerPanel.None) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent))
                    )
                    .padding(start = ScreenPad, end = ScreenPad, top = 24.dp, bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onClose,
                )
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.title,
                        color = TextMain,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOf(
                            currentEpisode?.name,
                            currentSource?.name,
                            detail.language.takeIf { it.isNotBlank() },
                        ).filterNotNull().joinToString(" · "),
                        color = TextDim,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                    )
                    .padding(start = ScreenPad, end = ScreenPad, top = 40.dp, bottom = 36.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatDuration(position), color = TextMain, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp)
                            .height(if (seekBarFocused) 38.dp else 24.dp)
                            .focusRequester(seekFocus)
                            .onFocusChanged { seekBarFocused = it.isFocused }
                            .focusProperties { down = playPauseFocus }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        seekRelative(-10_000, "快退 10 秒")
                                        showControls()
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        seekRelative(10_000, "快进 10 秒")
                                        showControls()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .focusable()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (seekBarFocused) Color(0xB312161D) else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = if (seekBarFocused) Gold else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .height(if (seekBarFocused) 6.dp else 4.dp),
                        ) {
                            val progress = if (duration > 0) {
                                (position.toFloat() / duration).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0x55FFFFFF)),
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Accent),
                            )
                            if (seekBarFocused) {
                                Box(
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .offset(x = (maxWidth - 14.dp) * progress)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Gold)
                                        .border(2.dp, Color.White, CircleShape),
                                )
                            }
                        }
                    }
                    Text(formatDuration(duration), color = TextDim, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(
                        icon = Icons.Filled.Replay10,
                        contentDescription = "快退 10 秒",
                        onClick = {
                            seekRelative(-10_000, "快退 10 秒")
                            showControls()
                        },
                    )
                    PlayerIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        onClick = {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            showControls()
                        },
                        primary = true,
                        focusRequester = playPauseFocus,
                        modifier = Modifier.focusProperties { up = seekFocus },
                    )
                    PlayerIconButton(
                        icon = Icons.Filled.Forward10,
                        contentDescription = "快进 10 秒",
                        onClick = {
                            seekRelative(10_000, "快进 10 秒")
                            showControls()
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    TvButton(
                        text = "选集",
                        onClick = {
                            panel = PlayerPanel.Episodes
                            showControls()
                        },
                    )
                    if (detail.sources.size > 1) {
                        TvButton(
                            text = "线路",
                            onClick = {
                                panel = PlayerPanel.Sources
                                showControls()
                            },
                        )
                    }
                    if (versions.isNotEmpty()) {
                        TvButton(
                            text = "版本",
                            onClick = {
                                panel = PlayerPanel.Versions
                                showControls()
                            },
                        )
                    }
                    TvButton(
                        text = "画质字幕",
                        onClick = {
                            panel = PlayerPanel.Tracks
                            showControls()
                        },
                    )
                    if (hasNext) {
                        TvButton(
                            text = "下一集",
                            onClick = {
                                episodeIndex += 1
                                panel = PlayerPanel.None
                            },
                        )
                    }
                    SkipTimeButton(
                        label = "设置片头",
                        valueMs = skipTimes.introMs,
                        onCapture = {
                            val max = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            val current = (exo.currentPosition.coerceAtLeast(0) / 1000 * 1000)
                            updateSkipTimes(
                                skipTimes.copy(
                                    introMs = current.coerceAtMost(max),
                                )
                            )
                        },
                        onClear = {
                            updateSkipTimes(skipTimes.copy(introMs = 0))
                        },
                        onAdjust = { delta ->
                            val max = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            updateSkipTimes(
                                skipTimes.copy(
                                    introMs = (skipTimes.introMs + delta).coerceIn(0, max),
                                )
                            )
                        },
                    )
                    SkipTimeButton(
                        label = "设置片尾",
                        valueMs = skipTimes.outroMs,
                        onCapture = {
                            val max = exo.duration.takeIf { it > 0 } ?: duration
                            val remaining = if (max > 0) {
                                (max - exo.currentPosition).coerceAtLeast(0) / 1000 * 1000
                            } else {
                                0L
                            }
                            updateSkipTimes(skipTimes.copy(outroMs = remaining.coerceAtMost(max)))
                        },
                        onClear = {
                            updateSkipTimes(skipTimes.copy(outroMs = 0))
                        },
                        onAdjust = { delta ->
                            val max = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            updateSkipTimes(
                                skipTimes.copy(
                                    outroMs = (skipTimes.outroMs + delta).coerceIn(0, max),
                                )
                            )
                        },
                    )
                }
            }
        }

        if (panel != PlayerPanel.None) {
            PlayerSidePanel(
                panel = panel,
                detail = detail,
                sourceIndex = sourceIndex,
                episodeIndex = episodeIndex,
                versions = versions,
                tracksLoaded = tracks != null,
                videoChoices = videoChoices,
                audioChoices = audioChoices,
                textChoices = textChoices,
                selectedVideoKey = selectedVideoKey,
                selectedAudioKey = selectedAudioKey,
                selectedTextKey = selectedTextKey,
                onSelectVideo = { applyVideoChoice(it) },
                onSelectAudio = { applyAudioChoice(it) },
                onSelectText = { applyTextChoice(it) },
                onClose = { closePanel() },
                onSelectEpisode = { index ->
                    episodeIndex = index
                    panel = PlayerPanel.None
                    showControls()
                },
                onSelectSource = { index ->
                    sourceIndex = index
                    episodeIndex = episodeIndex.coerceAtMost(
                        (detail.sources.getOrNull(index)?.episodes?.lastIndex ?: 0).coerceAtLeast(0)
                    )
                    panel = PlayerPanel.None
                    showControls()
                },
                onSelectVersion = { card ->
                    panel = PlayerPanel.None
                    scope.launch {
                        streamState = LoadState.Loading
                        runCatching { viewModel.loadDetail(card.id, card.providerId) }
                            .onSuccess { newDetail ->
                                hasStarted = true
                                val keepEpisode = episodeIndex
                                detail = newDetail
                                sourceIndex = 0
                                episodeIndex = keepEpisode.coerceAtMost(
                                    (newDetail.sources.firstOrNull()?.episodes?.lastIndex ?: 0).coerceAtLeast(0)
                                )
                            }
                            .onFailure { error = it.friendlyMessage() }
                    }
                },
            )
        }

        seekHintText?.let { hint ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xD91A2029))
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                ) {
                    Text(hint, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SkipTimeButton(
    label: String,
    valueMs: Long,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    onAdjust: (Long) -> Unit,
) {
    var lastPressAt by remember { mutableLongStateOf(0L) }
    TvButton(
        text = "$label ${formatDuration(valueMs)}",
        onClick = {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastPressAt <= 400) {
                lastPressAt = 0L
                onClear()
            } else {
                lastPressAt = now
                onCapture()
            }
        },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> {
                    onAdjust(1000L)
                    true
                }
                Key.DirectionDown -> {
                    onAdjust(-1000L)
                    true
                }
                else -> false
            }
        },
    )
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "playerBtnScale")
    val background by animateColorAsState(
        when {
            primary -> Accent
            focused -> SurfaceHigh
            else -> Color(0x33000000)
        },
        label = "playerBtnBg",
    )
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(if (focused) 3.dp else 1.dp, if (focused) Gold else Color(0x55FFFFFF), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (primary) Color.White else TextMain,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color(0xFFFF8A80), fontSize = 17.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "重试",
                    onClick = onRetry,
                    primary = true,
                    focusRequester = retryFocus,
                )
                TvButton("返回", onClick = onClose)
            }
        }
    }
}

private data class SaveSnapshot(
    val detail: VideoDetail,
    val sourceIndex: Int,
    val episodeIndex: Int,
    val position: Long,
    val duration: Long,
)

private data class TrackChoice(
    val key: String,
    val label: String,
    val group: TrackGroup,
    val index: Int,
    val height: Int = 0,
)

private fun videoChoicesOf(tracks: Tracks?): List<TrackChoice> {
    if (tracks == null) return emptyList()
    val byHeight = LinkedHashMap<Int, TrackChoice>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val height = group.getTrackFormat(index).height
            if (height <= 0 || byHeight.containsKey(height)) continue
            byHeight[height] = TrackChoice(
                key = "video-$height",
                label = "${height}P",
                group = group.mediaTrackGroup,
                index = index,
                height = height,
            )
        }
    }
    return byHeight.values.sortedByDescending { it.height }
}

private fun audioChoicesOf(tracks: Tracks?): List<TrackChoice> {
    if (tracks == null) return emptyList()
    val result = mutableListOf<TrackChoice>()
    val usedLabels = mutableSetOf<String>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_AUDIO) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            val number = result.size + 1
            val base = format.label?.takeIf { it.isNotBlank() }
                ?: languageLabel(format.language)
                ?: "音轨 $number"
            val label = if (usedLabels.add(base)) base else "$base $number"
            result += TrackChoice("audio-$number", label, group.mediaTrackGroup, index)
        }
    }
    return result
}

private fun textChoicesOf(tracks: Tracks?): List<TrackChoice> {
    if (tracks == null) return emptyList()
    val result = mutableListOf<TrackChoice>()
    val usedLabels = mutableSetOf<String>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSupported(index)) continue
            val format = group.getTrackFormat(index)
            val number = result.size + 1
            val base = format.label?.takeIf { it.isNotBlank() }
                ?: languageLabel(format.language)
                ?: "字幕 $number"
            val label = if (usedLabels.add(base)) base else "$base $number"
            result += TrackChoice("text-$number", label, group.mediaTrackGroup, index)
        }
    }
    return result
}

private fun languageLabel(language: String?): String? {
    val code = language?.substringBefore('-')?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (code) {
        "zh", "chi", "zho" -> "中文"
        "yue" -> "粤语"
        "en", "eng" -> "英语"
        "ja", "jpn" -> "日语"
        "ko", "kor" -> "韩语"
        "th", "tha" -> "泰语"
        "fr", "fra", "fre" -> "法语"
        "de", "deu", "ger" -> "德语"
        "es", "spa" -> "西班牙语"
        "ru", "rus" -> "俄语"
        "pt", "por" -> "葡萄牙语"
        "it", "ita" -> "意大利语"
        else -> null
    }
}

@Composable
private fun TrackSectionLabel(text: String) {
    Text(
        text = text,
        color = TextMain,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TrackHint(text: String) {
    Text(text = text, color = TextDim, fontSize = 13.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSidePanel(
    panel: PlayerPanel,
    detail: VideoDetail,
    sourceIndex: Int,
    episodeIndex: Int,
    versions: List<VideoCard>,
    tracksLoaded: Boolean,
    videoChoices: List<TrackChoice>,
    audioChoices: List<TrackChoice>,
    textChoices: List<TrackChoice>,
    selectedVideoKey: String?,
    selectedAudioKey: String?,
    selectedTextKey: String?,
    onSelectVideo: (TrackChoice?) -> Unit,
    onSelectAudio: (TrackChoice?) -> Unit,
    onSelectText: (TrackChoice?) -> Unit,
    onClose: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectVersion: (VideoCard) -> Unit,
) {
    val episodes = detail.sources.getOrNull(sourceIndex)?.episodes.orEmpty()
    val gridState = rememberLazyGridState()
    val itemFocus = remember { FocusRequester() }

    val title = when (panel) {
        PlayerPanel.Episodes -> "选集"
        PlayerPanel.Sources -> "播放线路"
        PlayerPanel.Versions -> "版本 / 语言"
        PlayerPanel.Tracks -> "画质 / 音轨 / 字幕"
        PlayerPanel.None -> ""
    }
    val subtitle = when (panel) {
        PlayerPanel.Episodes -> if (episodes.isEmpty()) "暂无剧集" else "第 ${episodeIndex + 1} 集 · 共 ${episodes.size} 集"
        PlayerPanel.Sources -> "共 ${detail.sources.size} 条线路"
        PlayerPanel.Versions -> "共 ${versions.size} 个版本"
        PlayerPanel.Tracks -> if (!tracksLoaded) {
            "正在读取音轨信息…"
        } else {
            "清晰度 ${videoChoices.size} · 音轨 ${audioChoices.size} · 字幕 ${textChoices.size}"
        }
        PlayerPanel.None -> ""
    }

    LaunchedEffect(panel, sourceIndex) {
        if (panel == PlayerPanel.Episodes && episodes.isNotEmpty()) {
            runCatching { gridState.scrollToItem(episodeIndex.coerceIn(0, episodes.lastIndex)) }
        }
        delay(100)
        runCatching { itemFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xA6000000)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .background(Color(0xFF12161D))
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(subtitle, color = TextDim, fontSize = 13.sp)
                }
                TvIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "关闭",
                    onClick = onClose,
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )
            Spacer(Modifier.height(16.dp))
            when (panel) {
                PlayerPanel.Episodes -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(episodes.size) { index ->
                            Chip(
                                text = episodes[index].name,
                                selected = index == episodeIndex,
                                onClick = { onSelectEpisode(index) },
                                focusRequester = if (index == episodeIndex) itemFocus else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp),
                            )
                        }
                    }
                }
                PlayerPanel.Sources -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(detail.sources.size) { index ->
                            val source = detail.sources[index]
                            Chip(
                                text = "${source.name.ifBlank { "线路${index + 1}" }}（${source.episodes.size} 集）",
                                selected = index == sourceIndex,
                                onClick = { onSelectSource(index) },
                                focusRequester = if (index == sourceIndex) itemFocus else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp),
                            )
                        }
                    }
                }
                PlayerPanel.Versions -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(versions.size) { index ->
                            val version = versions[index]
                            Chip(
                                text = version.title,
                                selected = version.id == detail.id,
                                onClick = { onSelectVersion(version) },
                                focusRequester = if (index == 0) itemFocus else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp),
                            )
                        }
                    }
                }
                PlayerPanel.Tracks -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { TrackSectionLabel("清晰度") }
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Chip(
                                    text = "自动",
                                    selected = selectedVideoKey == null,
                                    onClick = { onSelectVideo(null) },
                                    focusRequester = itemFocus,
                                    modifier = Modifier.heightIn(min = 42.dp),
                                )
                                videoChoices.forEach { choice ->
                                    Chip(
                                        text = choice.label,
                                        selected = selectedVideoKey == choice.key,
                                        onClick = { onSelectVideo(choice) },
                                        modifier = Modifier.heightIn(min = 42.dp),
                                    )
                                }
                            }
                        }
                        if (videoChoices.isEmpty()) {
                            item {
                                TrackHint(if (tracksLoaded) "当前线路没有可切换的清晰度" else "正在读取…")
                            }
                        } else if (videoChoices.size == 1) {
                            item { TrackHint("当前线路仅有一种清晰度") }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            TrackSectionLabel("音轨")
                        }
                        if (audioChoices.size <= 1) {
                            item {
                                TrackHint(
                                    when {
                                        !tracksLoaded -> "正在读取…"
                                        audioChoices.isEmpty() -> "当前影片没有音轨信息"
                                        else -> "当前线路只有一条音轨（${audioChoices.first().label}）"
                                    }
                                )
                            }
                        } else {
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Chip(
                                        text = "自动",
                                        selected = selectedAudioKey == null,
                                        onClick = { onSelectAudio(null) },
                                        modifier = Modifier.heightIn(min = 42.dp),
                                    )
                                    audioChoices.forEach { choice ->
                                        Chip(
                                            text = choice.label,
                                            selected = selectedAudioKey == choice.key,
                                            onClick = { onSelectAudio(choice) },
                                            modifier = Modifier.heightIn(min = 42.dp),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(4.dp))
                            TrackSectionLabel("字幕")
                        }
                        if (textChoices.isEmpty()) {
                            item {
                                TrackHint(if (tracksLoaded) "当前影片没有内置字幕" else "正在读取…")
                            }
                        } else {
                            item {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Chip(
                                        text = "关闭",
                                        selected = selectedTextKey == null,
                                        onClick = { onSelectText(null) },
                                        modifier = Modifier.heightIn(min = 42.dp),
                                    )
                                    textChoices.forEach { choice ->
                                        Chip(
                                            text = choice.label,
                                            selected = selectedTextKey == choice.key,
                                            onClick = { onSelectText(choice) },
                                            modifier = Modifier.heightIn(min = 42.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                PlayerPanel.None -> Unit
            }
            Text(
                text = "按返回键关闭",
                color = TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
