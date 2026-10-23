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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.ui.components.EmptyState
import com.xiaohei.tv.ui.components.ScreenHeader
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.formatDuration
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.BgDark
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    history: List<HistoryEntry>,
    onResume: (HistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var pendingClear by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val scrollToTop: () -> Unit = {
        scope.launch {
            delay(60)
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listOf(150L, 500L, 1100L).forEach { wait ->
                delay(wait)
                runCatching { firstFocus.requestFocus() }
            }
        }
    }

    if (history.isEmpty()) {
        EmptyState("还没有观看记录，去首页挑一部影片吧")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentPadding = PaddingValues(start = ScreenPad, end = ScreenPad, top = 24.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScreenHeader(
                    title = "观看历史",
                    subtitle = "共 ${history.size} 条记录 · 自动记录播放进度",
                    modifier = Modifier.weight(1f),
                )
                TvButton("清空历史", onClick = { pendingClear = true }, onFocus = scrollToTop)
            }
        }
        items(history, key = { "${it.providerId}-${it.videoId}" }) { entry ->
            val inFirstRow = entry == history.getOrNull(0) || entry == history.getOrNull(1)
            HistoryCard(
                entry = entry,
                onResume = { onResume(entry) },
                onDelete = { pendingDelete = entry },
                focusRequester = if (entry == history.first()) firstFocus else null,
                onFocusTop = if (inFirstRow) scrollToTop else null,
            )
        }
    }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "删除观看记录",
            message = "确定删除「${entry.title}」的观看记录吗？",
            confirmText = "删除",
            onConfirm = {
                onDelete(entry.videoId)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (pendingClear) {
        ConfirmDialog(
            title = "清空观看历史",
            message = "将删除全部 ${history.size} 条记录，此操作不可恢复。",
            confirmText = "清空",
            onConfirm = {
                onClear()
                pendingClear = false
            },
            onDismiss = { pendingClear = false },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester?,
    onFocusTop: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(126.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF232A35)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(entry.poster).crossfade(true).build(),
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = TextMain,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(entry.typeName, entry.remarks)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${entry.episodeName.ifBlank { "正片" }} · 已观看 ${formatDuration(entry.positionMs)}" +
                    if (entry.durationMs > 0) " / ${formatDuration(entry.durationMs)}" else "",
                color = TextMain,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0xFF2B323D), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(entry.progress)
                        .height(4.dp)
                        .background(Accent, RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(
                    text = "继续播放",
                    onClick = onResume,
                    primary = true,
                    focusRequester = focusRequester,
                    onFocus = onFocusTop,
                )
                TvButton(text = "删除", onClick = onDelete, onFocus = onFocusTop)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { confirmFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(message, color = TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TvButton("取消", onClick = onDismiss)
                TvButton(
                    text = confirmText,
                    onClick = onConfirm,
                    primary = true,
                    focusRequester = confirmFocus,
                )
            }
        }
    }
}
