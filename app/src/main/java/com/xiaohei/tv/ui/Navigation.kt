package com.xiaohei.tv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaohei.tv.clash.ClashPhase
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.TvIconButton
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.Divider
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.Success
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain

enum class MainTab(val label: String, val videoType: VideoType? = null) {
    HOME("首页"),
    RANKING("榜单"),
    MOVIE("电影", VideoType.MOVIE),
    SERIES("剧集", VideoType.SERIES),
    ANIME("动漫", VideoType.ANIME),
    VARIETY("综艺", VideoType.VARIETY),
    DOCUMENTARY("纪录", VideoType.DOCUMENTARY),
    SHORT_RANK("榜单", VideoType.SHORT_RANK),
    SHORT_RECOMMEND("推荐", VideoType.SERIES),
    SHORT_DISCOVERY("找剧", VideoType.SHORT_DISCOVERY),
    SHORT_FEED("推荐流", VideoType.SHORT_FEED),
    GAZE_ALL("全部", VideoType.GAZE_ALL),
    CHINESE_ANIME("国漫", VideoType.CHINESE_ANIME),
    REAL_DRAMA("真人剧", VideoType.REAL_DRAMA),
    COMIC_DRAMA("漫剧", VideoType.COMIC_DRAMA),
    AI_DRAMA("AI剧", VideoType.AI_DRAMA),
    HISTORY("历史"),
    SETTINGS("设置"),
}

val NavTabs = listOf(
    MainTab.HOME,
    MainTab.RANKING,
    MainTab.MOVIE,
    MainTab.SERIES,
    MainTab.ANIME,
    MainTab.VARIETY,
    MainTab.DOCUMENTARY,
)

sealed interface Overlay {
    data object Search : Overlay

    data class Detail(
        val videoId: String,
        val providerId: String = "",
    ) : Overlay

    data class Player(
        val detail: VideoDetail,
        val sourceIndex: Int = 0,
        val episodeIndex: Int = 0,
        val startPosition: Long = 0,
    ) : Overlay
}

private val VpnError = Color(0xFFFF6B6B)

@Composable
fun TopNav(
    current: MainTab,
    tabs: List<MainTab>,
    clashPhase: ClashPhase,
    vpnEnabled: Boolean,
    vpnVisible: Boolean,
    onSelect: (MainTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val searchFocus = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(BgDark)
            .padding(horizontal = ScreenPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "帮主",
                color = Accent,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                "TV",
                color = Gold,
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
        Spacer(Modifier.width(20.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                NavItem(
                    label = tab.label,
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier = if (index == tabs.lastIndex) {
                        Modifier.focusProperties { right = searchFocus }
                    } else {
                        Modifier
                    },
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvIconButton(
                icon = Icons.Filled.Search,
                contentDescription = "搜索",
                onClick = onOpenSearch,
                focusRequester = searchFocus,
            )
            TvIconButton(
                icon = Icons.Filled.History,
                contentDescription = "观看历史",
                onClick = onOpenHistory,
                selected = current == MainTab.HISTORY,
            )
            TvIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "设置",
                onClick = onOpenSettings,
                selected = current == MainTab.SETTINGS,
                badgeColor = if (vpnVisible && vpnEnabled) {
                    when (clashPhase) {
                        ClashPhase.Running -> Success
                        ClashPhase.Connecting, ClashPhase.Preparing -> Gold
                        ClashPhase.Error -> VpnError
                        ClashPhase.Stopped -> TextDim
                    }
                } else {
                    null
                },
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            focused -> Color(0xFF232A35)
            selected -> Accent.copy(alpha = 0.16f)
            else -> Color.Transparent
        },
        label = "navBg",
    )
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                selected -> Accent
                focused -> TextMain
                else -> TextDim
            },
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun MobileNav(current: MainTab, tabs: List<MainTab>, onSelect: (MainTab) -> Unit, onOpenSearch: () -> Unit, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(BgDark)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("帮主", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("TV", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            TvIconButton(icon = Icons.Filled.Search, contentDescription = "搜索", onClick = onOpenSearch)
            TvIconButton(icon = Icons.Filled.History, contentDescription = "观看历史", onClick = onOpenHistory, selected = current == MainTab.HISTORY)
            TvIconButton(icon = Icons.Filled.Settings, contentDescription = "设置", onClick = onOpenSettings, selected = current == MainTab.SETTINGS)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab -> NavItem(tab.label, tab == current, { onSelect(tab) }) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
    }
}
