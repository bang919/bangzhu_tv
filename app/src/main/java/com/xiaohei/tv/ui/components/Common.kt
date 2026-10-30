package com.xiaohei.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.Danger
import com.xiaohei.tv.ui.theme.Divider
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.SurfaceHigh
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ScreenPad = 24.dp
val CardShape = RoundedCornerShape(8.dp)
private val FocusBorder = 3.dp
private val FocusScaleSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)
private val PosterFadeBrush = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
private val ContinueScrimBrush = Brush.verticalGradient(listOf(Color(0x22000000), Color(0xDD000000)))

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    iconOnly: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            primary && focused -> Color(0xFFFF7654)
            primary -> Accent
            focused -> SurfaceHigh
            else -> Color(0xFF151920)
        },
        label = "buttonBg",
    )
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "buttonScale")
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Gold
                    primary -> Accent
                    else -> Divider
                },
                shape = shape,
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(onClick = onClick)
            .then(if (iconOnly) Modifier.padding(horizontal = 14.dp) else Modifier.padding(horizontal = 18.dp))
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = if (iconOnly) text else null,
                tint = if (primary) Color.White else TextMain,
                modifier = Modifier.size(22.dp),
            )
        }
        if (!iconOnly) {
            Text(
                text = text,
                color = if (primary) Color.White else TextMain,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    badgeColor: Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(shape)
            .background(
                when {
                    focused -> SurfaceHigh
                    selected -> Accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (focused || selected) {
                    Modifier.border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) Gold else Accent.copy(alpha = 0.6f),
                        shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                focused -> TextMain
                selected -> Accent
                else -> TextDim
            },
            modifier = Modifier.size(23.dp),
        )
        if (badgeColor != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
            )
        }
    }
}

@Composable
fun PosterCard(
    card: VideoCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    progress: Float? = null,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = FocusScaleSpec,
        label = "posterScale",
    )
    val context = LocalContext.current
    val posterModel = remember(card.poster, context) {
        ImageRequest.Builder(context).data(card.poster).crossfade(false).build()
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusPeekIntoView()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShape)
                .background(SurfaceHigh)
                .then(if (focused) Modifier.border(FocusBorder, Gold, CardShape) else Modifier),
        ) {
            AsyncImage(
                model = posterModel,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.poster.isBlank()) {
                Text(
                    text = card.title.take(1),
                    color = TextDim,
                    style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(PosterFadeBrush),
            )
            if (!card.note.isNullOrBlank()) {
                Text(
                    text = card.note,
                    color = Color.White,
                    style = TextStyle(fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            if (rank != null) {
                Text(
                    text = rank.toString(),
                    color = if (rank <= 3) Gold else Color.White,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp),
                )
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x66000000)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Accent),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = card.title,
            color = if (focused) TextMain else TextMain.copy(alpha = 0.92f),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle ?: listOf(card.year, card.area).filter { it.isNotBlank() }.joinToString(" · "),
            color = TextDim,
            style = TextStyle(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MoreCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = FocusScaleSpec,
        label = "moreScale",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusPeekIntoView()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShape)
                .background(if (focused) Accent.copy(alpha = 0.18f) else SurfaceDark)
                .then(if (focused) Modifier.border(FocusBorder, Gold, CardShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "›",
                        color = Gold,
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "查看全部",
                    color = TextMain,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = "更多",
            color = TextMain,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = title,
            color = TextDim,
            style = TextStyle(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ContinueCard(entry: HistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = FocusScaleSpec,
        label = "continueScale",
    )
    val context = LocalContext.current
    val posterModel = remember(entry.poster, context) {
        ImageRequest.Builder(context).data(entry.poster).crossfade(false).build()
    }
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusPeekIntoView()
            .clip(CardShape)
            .background(SurfaceHigh)
            .then(if (focused) Modifier.border(FocusBorder, Gold, CardShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = posterModel,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ContinueScrimBrush),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
        ) {
            Text(
                entry.title,
                color = Color.White,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.episodeName.ifBlank { "正片" }} · 观看至 ${formatDuration(entry.positionMs)}",
                color = TextDim,
                style = TextStyle(fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(entry.progress)
                        .height(3.dp)
                        .background(Accent, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        if (selected) Accent.copy(alpha = 0.9f) else if (focused) SurfaceHigh else Color(0xFF1B2029),
        label = "chipBg",
    )
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Gold else if (selected) Accent else Divider,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected || focused) Color.White else TextDim,
            style = TextStyle(fontSize = 13.sp),
            maxLines = 1,
        )
    }
}

data class TvFilterOption(
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

data class TvFilterSpec(
    val key: String,
    val label: String,
    val current: String,
    val options: List<TvFilterOption>,
)

@Composable
fun TvFilterBar(
    specs: List<TvFilterSpec>,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    val groupFocus = remember { mutableStateMapOf<String, FocusRequester>() }
    val optionsFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun groupRequester(key: String): FocusRequester =
        groupFocus.getOrPut(key) { FocusRequester() }

    LaunchedEffect(specs.map { it.key }) {
        if (expandedKey != null && specs.none { it.key == expandedKey }) {
            expandedKey = null
        }
    }

    BackHandler(enabled = expandedKey != null) {
        expandedKey = null
    }

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(specs, key = { it.key }) { spec ->
                FilterGroupChip(
                    text = "${spec.label} · ${spec.current}",
                    active = expandedKey == spec.key,
                    onClick = {
                        expandedKey = if (expandedKey == spec.key) null else spec.key
                    },
                    modifier = Modifier.focusRequester(groupRequester(spec.key)),
                    onFocused = onFocus,
                    onDown = if (expandedKey != null) {
                        { runCatching { optionsFocus.requestFocus() } }
                    } else {
                        null
                    },
                )
            }
        }
        val expanded = specs.firstOrNull { it.key == expandedKey }
        if (expanded != null) {
            Spacer(Modifier.height(8.dp))
            val selectedIndex = expanded.options.indexOfFirst { it.selected }
                .let { if (it >= 0) it else 0 }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(expanded.options, key = { index, option -> "$index-${option.label}" }) { index, option ->
                    Chip(
                        text = option.label,
                        selected = option.selected,
                        onClick = {
                            option.onSelect()
                            expandedKey = null
                            scope.launch {
                                delay(80)
                                runCatching { groupRequester(expanded.key).requestFocus() }
                            }
                        },
                        focusRequester = if (index == selectedIndex) optionsFocus else null,
                        modifier = if (onFocus != null) {
                            Modifier.onFocusChanged { if (it.isFocused) onFocus() }
                        } else {
                            Modifier
                        },
                    )
                }
            }
            AutoFocus(optionsFocus)
        }
    }
}

@Composable
private fun FilterGroupChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .onPreviewKeyEvent { event ->
                if (onDown != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onDown()
                    true
                } else {
                    false
                }
            }
            .clip(shape)
            .background(
                when {
                    focused -> SurfaceHigh
                    active -> Accent.copy(alpha = 0.16f)
                    else -> Color(0xFF1B2029)
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Gold else if (active) Accent else Divider,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = text,
            color = if (focused || active) TextMain else TextDim,
            style = TextStyle(fontSize = 14.sp),
            maxLines = 1,
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = if (focused || active) TextMain else TextDim,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (active) 180f else 0f),
        )
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .background(Accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = title,
            color = TextMain,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = TextDim, style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
fun LoadingState(text: String = "加载中…") {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, color = TextDim, style = TextStyle(fontSize = 16.sp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.ifBlank { "加载失败" },
            color = Danger,
            style = TextStyle(fontSize = 17.sp),
        )
        Spacer(Modifier.height(18.dp))
        TvButton("重试", onClick = onRetry)
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextDim, style = TextStyle(fontSize = 17.sp))
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入关键字",
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextMain, fontSize = 17.sp),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier
            .height(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    else -> return@onPreviewKeyEvent false
                }
                focusManager.moveFocus(direction)
                true
            }
            .clip(shape)
            .background(Color(0xFF151920))
            .border(if (focused) 2.dp else 1.dp, if (focused) Gold else Divider, shape)
            .padding(horizontal = 18.dp),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = TextDim, style = TextStyle(fontSize = 16.sp))
                }
                inner()
            }
        },
    )
}

@Composable
fun MetaTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Divider, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, color = TextDim, style = TextStyle(fontSize = 13.sp), maxLines = 1)
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatDate(ms: Long): String {
    if (ms <= 0) return ""
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d-%02d %02d:%02d".format(
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextMain,
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TextDim, style = TextStyle(fontSize = 14.sp))
        }
    }
}

@Composable
fun Modifier.focusPeekIntoView(peek: Dp = 64.dp): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val peekPx = with(LocalDensity.current) { peek.roundToPx() }
    LaunchedEffect(focused, size) {
        if (!focused || size == IntSize.Zero) return@LaunchedEffect
        runCatching {
            requester.bringIntoView(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = size.width.toFloat() + peekPx,
                    bottom = size.height.toFloat() + peekPx,
                )
            )
        }
    }
    return this
        .onGloballyPositioned { size = it.size }
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}

@Composable
fun AutoFocus(requester: FocusRequester, delays: List<Long> = listOf(160L, 700L)) {
    LaunchedEffect(requester) {
        delays.forEach { wait ->
            delay(wait)
            runCatching { requester.requestFocus() }
        }
    }
}
