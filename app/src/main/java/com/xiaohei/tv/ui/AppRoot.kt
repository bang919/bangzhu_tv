package com.xiaohei.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaohei.tv.MainActivity
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.ui.components.InputTicker
import com.xiaohei.tv.ui.components.TvConfirmDialog
import com.xiaohei.tv.ui.screens.CategoryScreen
import com.xiaohei.tv.ui.screens.DetailScreen
import com.xiaohei.tv.ui.screens.HistoryScreen
import com.xiaohei.tv.ui.screens.HomeScreen
import com.xiaohei.tv.ui.screens.PlayerScreen
import com.xiaohei.tv.ui.screens.RankingScreen
import com.xiaohei.tv.ui.screens.SearchScreen
import com.xiaohei.tv.ui.screens.SearchUiState
import com.xiaohei.tv.ui.screens.SettingsScreen
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SecretKeyCodes = intArrayOf(
    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN,
    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun XiaoheiTvApp(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var overlays by remember { mutableStateOf<List<Overlay>>(emptyList()) }
    var vpnUnlocked by remember { mutableStateOf(false) }
    var confirmExitApp by remember { mutableStateOf(false) }
    val secretProgress = remember { intArrayOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyLabel by remember { mutableStateOf<String?>(null) }
    val searchState = remember { SearchUiState() }
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clashStatus by viewModel.clashStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val activeProviderId = settings.providerId.ifBlank { viewModel.defaultProviderId }
    val visibleTabs = if (activeProviderId == "hongguo") {
        listOf(MainTab.HOME, MainTab.SHORT_RANK, MainTab.SHORT_RECOMMEND, MainTab.SHORT_FEED, MainTab.SHORT_DISCOVERY, MainTab.REAL_DRAMA, MainTab.COMIC_DRAMA, MainTab.AI_DRAMA)
    } else if (activeProviderId == "gaze") {
        listOf(MainTab.HOME, MainTab.GAZE_ALL, MainTab.MOVIE, MainTab.SERIES, MainTab.ANIME, MainTab.CHINESE_ANIME)
    } else {
        NavTabs.filter { it == MainTab.HOME || (it == MainTab.RANKING && viewModel.supportsRanking) ||
            (it.videoType != null && viewModel.supportsType(it.videoType)) }
    }

    val activity = LocalContext.current as? MainActivity
    val isTv = (LocalContext.current.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val gestureEnabled = rememberUpdatedState(
        tab == MainTab.HOME && overlays.isEmpty() && !vpnUnlocked
    )
    DisposableEffect(activity) {
        activity?.keyEventObserver = { keyCode, repeatCount ->
            if (repeatCount == 0) {
                InputTicker.tick++
            }
            if (repeatCount == 0 && gestureEnabled.value) {
                if (keyCode == SecretKeyCodes.getOrNull(secretProgress[0])) {
                    secretProgress[0] += 1
                    if (secretProgress[0] == SecretKeyCodes.size) {
                        secretProgress[0] = 0
                        vpnUnlocked = true
                    }
                } else {
                    secretProgress[0] = if (keyCode == SecretKeyCodes.first()) 1 else 0
                }
            }
        }
        onDispose { activity?.keyEventObserver = null }
    }

    LaunchedEffect(activeProviderId) {
        searchState.reset()
        if (tab != MainTab.SETTINGS && tab != MainTab.HISTORY && tab !in visibleTabs) tab = MainTab.HOME
    }

    val top = overlays.lastOrNull()
    val detailOverlay = top as? Overlay.Detail
    val playerOverlay = top as? Overlay.Player
    val searchVisible = overlays.any { it is Overlay.Search }

    fun showMessage(text: String) {
        message = text
    }

    fun openDetail(card: VideoCard) {
        overlays = overlays + Overlay.Detail(card.id, card.providerId)
    }

    fun closeTopOverlay() {
        val closing = overlays.lastOrNull()
        overlays = overlays.dropLast(1)
        if (closing is Overlay.Search) searchState.reset()
    }

    fun playDetail(detail: VideoDetail, sourceIndex: Int, episodeIndex: Int, position: Long) {
        overlays = overlays + Overlay.Player(
            detail = detail,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex,
            startPosition = position,
        )
    }

    fun playHistory(detail: VideoDetail, entry: HistoryEntry) {
        var sIndex = entry.sourceIndex.coerceIn(0, (detail.sources.size - 1).coerceAtLeast(0))
        var eIndex = entry.episodeIndex.coerceAtLeast(0)
        detail.sources.forEachIndexed { si, source ->
            val ei = source.episodes.indexOfFirst { it.href == entry.episodeHref }
            if (ei >= 0) {
                sIndex = si
                eIndex = ei
                return@forEachIndexed
            }
        }
        eIndex = eIndex.coerceAtMost(
            (detail.sources.getOrNull(sIndex)?.episodes?.lastIndex ?: 0).coerceAtLeast(0)
        )
        playDetail(detail, sIndex, eIndex, entry.positionMs)
    }

    fun playFromHistory(entry: HistoryEntry) {
        scope.launch {
            busyLabel = "正在打开「${entry.title}」…"
            try {
                playHistory(viewModel.loadDetail(entry.videoId, entry.providerId), entry)
            } catch (error: Exception) {
                showMessage(error.friendlyMessage())
            } finally {
                busyLabel = null
            }
        }
    }

    fun playCard(card: VideoCard) {
        scope.launch {
            busyLabel = "正在打开「${card.title}」…"
            try {
                val detail = viewModel.loadDetail(card.id, card.providerId)
                val entry = history.firstOrNull { it.videoId == detail.id && it.providerId == detail.providerId }
                if (entry != null) {
                    playHistory(detail, entry)
                } else {
                    playDetail(detail, 0, 0, 0)
                }
            } catch (error: Exception) {
                showMessage(error.friendlyMessage())
            } finally {
                busyLabel = null
            }
        }
    }

    BackHandler(enabled = overlays.isNotEmpty()) {
        closeTopOverlay()
    }
    BackHandler(enabled = overlays.isEmpty() && tab != MainTab.HOME) {
        tab = MainTab.HOME
    }
    BackHandler(enabled = overlays.isEmpty() && tab == MainTab.HOME && !confirmExitApp) {
        confirmExitApp = true
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            message = null
        }
    }

    LaunchedEffect(tab, overlays.isNotEmpty()) {
        secretProgress[0] = 0
    }

    DisposableEffect(playerOverlay != null, isTv) {
        if (!isTv) activity?.requestedOrientation = if (playerOverlay != null) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { }
    }

    if (playerOverlay != null) {
        PlayerScreen(
            viewModel = viewModel,
            initialDetail = playerOverlay.detail,
            initialSourceIndex = playerOverlay.sourceIndex,
            initialEpisodeIndex = playerOverlay.episodeIndex,
            initialPosition = playerOverlay.startPosition,
            onClose = { overlays = overlays.dropLast(1) },
        )
        if (message != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MessageBar(message!!)
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (searchVisible) {
            Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
                SearchScreen(
                    viewModel = viewModel,
                    ui = searchState,
                    onOpenDetail = { openDetail(it) },
                    onClose = { closeTopOverlay() },
                )
                BusyOverlay(busyLabel)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark),
            ) {
                if (!isTv) MobileNav(
                    current = tab, tabs = visibleTabs, onSelect = { overlays = emptyList(); tab = it },
                    onOpenSearch = { searchState.reset(); overlays = listOf(Overlay.Search) },
                    onOpenHistory = { overlays = emptyList(); tab = MainTab.HISTORY },
                    onOpenSettings = { overlays = emptyList(); tab = MainTab.SETTINGS },
                ) else TopNav(
                    current = tab,
                    tabs = visibleTabs,
                    clashPhase = clashStatus.phase,
                    vpnEnabled = settings.vpnEnabled,
                    vpnVisible = vpnUnlocked,
                    onSelect = { selected ->
                        overlays = emptyList()
                        tab = selected
                    },
                    onOpenSearch = {
                        searchState.reset()
                        overlays = listOf(Overlay.Search)
                    },
                    onOpenHistory = {
                        overlays = emptyList()
                        tab = MainTab.HISTORY
                    },
                    onOpenSettings = {
                        overlays = emptyList()
                        tab = MainTab.SETTINGS
                    },
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (tab) {
                        MainTab.HOME -> HomeScreen(
                            viewModel = viewModel,
                            history = history,
                            onOpenDetail = { openDetail(it) },
                            onPlayCard = { playCard(it) },
                            onResume = { playFromHistory(it) },
                            onOpenTab = { tab = it },
                        )
                        MainTab.RANKING -> RankingScreen(
                            viewModel = viewModel,
                            onOpenDetail = { openDetail(it) },
                        )
                        MainTab.HISTORY -> HistoryScreen(
                            history = history,
                            onResume = { playFromHistory(it) },
                            onDelete = { viewModel.deleteHistory(it) },
                            onClear = { viewModel.clearHistory() },
                        )
                        MainTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            vpnVisible = vpnUnlocked,
                        )
                        else -> {
                            val type = tab.videoType
                            if (type != null) {
                                CategoryScreen(
                                    viewModel = viewModel,
                                    type = type,
                                    detailOpen = detailOverlay != null,
                                    onOpenDetail = { openDetail(it) },
                                )
                            }
                        }
                    }

                    BusyOverlay(busyLabel)
                }
            }
        }

        // Detail is layered above the tab content instead of replacing it, so filters,
        // loaded pages and grid scroll survive a round trip to the detail page.
        if (detailOverlay != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .focusGroup()
                    .focusProperties { exit = { FocusRequester.Cancel } },
            ) {
                DetailScreen(
                    viewModel = viewModel,
                    videoId = detailOverlay.videoId,
                    providerId = detailOverlay.providerId,
                    resumeEntry = history.firstOrNull {
                        it.videoId == detailOverlay.videoId &&
                            (it.providerId == detailOverlay.providerId || it.providerId.isBlank())
                    },
                    onOpenDetail = { openDetail(it) },
                    onPlay = { detail, sourceIndex, episodeIndex, position ->
                        playDetail(detail, sourceIndex, episodeIndex, position)
                    },
                    onBack = { closeTopOverlay() },
                )
                BusyOverlay(busyLabel)
            }
        }

        if (message != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                MessageBar(message!!)
            }
        }
    }

    if (confirmExitApp) {
        TvConfirmDialog(
            title = "退出应用",
            message = "确定要退出帮主TV吗？",
            confirmText = "退出",
            dismissText = "取消",
            focusCancelFirst = true,
            onConfirm = {
                confirmExitApp = false
                activity?.finish()
            },
            onDismiss = { confirmExitApp = false },
        )
    }
}

@Composable
private fun BusyOverlay(label: String?) {
    if (label == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
            Text(
                text = label,
                color = TextMain,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun MessageBar(text: String) {
    Box(
        modifier = Modifier
            .padding(bottom = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6262D38))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text = text, color = TextMain, fontSize = 13.sp)
    }
}
