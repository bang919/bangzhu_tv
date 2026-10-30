package com.xiaohei.tv.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficUpload
import com.xiaohei.tv.MainViewModel
import com.xiaohei.tv.clash.ClashController
import com.xiaohei.tv.clash.ClashGroup
import com.xiaohei.tv.clash.ClashPhase
import com.xiaohei.tv.ui.components.AutoFocus
import com.xiaohei.tv.ui.components.Chip
import com.xiaohei.tv.ui.components.ScreenHeader
import com.xiaohei.tv.ui.components.ScreenPad
import com.xiaohei.tv.ui.components.SectionTitle
import com.xiaohei.tv.ui.components.TvButton
import com.xiaohei.tv.ui.components.TvTextField
import com.xiaohei.tv.ui.components.formatDate
import com.xiaohei.tv.ui.friendlyMessage
import com.xiaohei.tv.ui.theme.Accent
import com.xiaohei.tv.ui.theme.Danger
import com.xiaohei.tv.ui.theme.Divider
import com.xiaohei.tv.ui.theme.Gold
import com.xiaohei.tv.ui.theme.Success
import com.xiaohei.tv.ui.theme.SurfaceDark
import com.xiaohei.tv.ui.theme.TextDim
import com.xiaohei.tv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel, vpnVisible: Boolean) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.clashStatus.collectAsStateWithLifecycle()
    val traffic by viewModel.clashTraffic.collectAsStateWithLifecycle()
    val groups by viewModel.clashGroups.collectAsStateWithLifecycle()
    val profileUpdatedAt by viewModel.profileUpdatedAt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subscriptionName by remember(settings.subscriptionName) { mutableStateOf(settings.subscriptionName) }
    var subscriptionUrl by remember(settings.subscriptionUrl) { mutableStateOf(settings.subscriptionUrl) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val saveSubscriptionFocus = remember { FocusRequester() }
    val vpnToggleFocus = remember { FocusRequester() }
    val providerFocus = remember { FocusRequester() }

    AutoFocus(providerFocus)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch {
                busy = true
                val connectResult = viewModel.connectClash()
                busy = false
                message = connectResult.exceptionOrNull()?.friendlyMessage()
            }
        } else {
            message = "需要授予 VPN 权限才能使用 Clash"
        }
    }

    fun connect() {
        if (!settings.vpnEnabled) return
        if (busy || status.phase == ClashPhase.Connecting || status.phase == ClashPhase.Preparing) return
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent != null) {
            permissionLauncher.launch(permissionIntent)
        } else {
            scope.launch {
                busy = true
                val result = viewModel.connectClash()
                busy = false
                message = result.exceptionOrNull()?.friendlyMessage()
            }
        }
    }

    LaunchedEffect(status.phase, settings.vpnEnabled) {
        if (settings.vpnEnabled && status.phase == ClashPhase.Running) {
            runCatching { viewModel.refreshClashGroups() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D10)),
        contentPadding = PaddingValues(start = ScreenPad, end = ScreenPad, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            ScreenHeader(
                title = "设置",
                subtitle = if (vpnVisible) {
                    "视频源与 Clash VPN；VPN 默认关闭，可按需开启"
                } else {
                    "切换视频源，选择想看的内容"
                },
            )
        }

        item(key = "provider") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val activeProviderId = settings.providerId.ifBlank { viewModel.defaultProviderId }
                val active = viewModel.providerInfos.firstOrNull { it.id == activeProviderId }
                SectionTitle(
                    title = "视频源",
                    trailing = active?.name ?: viewModel.defaultProviderId,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    viewModel.providerInfos.forEach { info ->
                        Chip(
                            text = info.name,
                            selected = info.id == activeProviderId,
                            onClick = {
                                if (info.id != activeProviderId) {
                                    viewModel.setProvider(info.id)
                                    message = "已切换视频源：${info.name}"
                                }
                            },
                            focusRequester = if (info.id == activeProviderId) providerFocus else null,
                        )
                    }
                }
                Text(
                    text = "切换后首页、分类、搜索、详情与播放都使用所选站点；观看历史按来源区分。",
                    color = TextDim,
                    fontSize = 12.sp,
                )
                active?.baseUrl?.takeIf { it.isNotBlank() }?.let {
                    Text(text = "站点地址：$it", color = TextDim, fontSize = 12.sp)
                }
                message?.let {
                    Text(it, color = TextDim, fontSize = 12.sp)
                }
            }
        }

        if (vpnVisible) {
        item(key = "vpn-toggle") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(
                    title = "VPN / Clash",
                    trailing = if (settings.vpnEnabled) "已开启" else "已关闭（默认）",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvButton(
                        text = if (settings.vpnEnabled) "VPN 功能：开" else "VPN 功能：关",
                        onClick = {
                            if (busy) return@TvButton
                            scope.launch {
                                val enable = !settings.vpnEnabled
                                viewModel.saveVpnEnabled(enable)
                                message = if (enable) {
                                    "VPN 功能已开启"
                                } else {
                                    "VPN 功能已关闭，连接已断开"
                                }
                            }
                        },
                        primary = settings.vpnEnabled,
                        focusRequester = vpnToggleFocus,
                    )
                    Text(
                        text = if (settings.vpnEnabled) {
                            "可连接内置 Clash，订阅、节点与开机自动连接见下方设置。"
                        } else {
                            "不开启不影响影视浏览与播放；开启后才能配置和使用 Clash。"
                        },
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
                if (!settings.vpnEnabled) {
                    message?.let {
                        Text(it, color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }

        if (settings.vpnEnabled) {
            item(key = "status") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (status.phase) {
                                    ClashPhase.Running -> Success
                                    ClashPhase.Connecting, ClashPhase.Preparing -> Gold
                                    ClashPhase.Error -> Danger
                                    ClashPhase.Stopped -> TextDim
                                }
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (status.phase) {
                            ClashPhase.Running -> "VPN 已连接"
                            ClashPhase.Connecting -> "正在启动 VPN"
                            ClashPhase.Preparing -> "正在准备配置"
                            ClashPhase.Error -> "连接异常"
                            ClashPhase.Stopped -> "VPN 未连接"
                        },
                        color = if (status.phase == ClashPhase.Error) Danger else TextMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(status.message, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("当前 ↑ ${traffic.now.trafficUpload()}/s", color = TextDim, fontSize = 13.sp)
                    Text("当前 ↓ ${traffic.now.trafficDownload()}/s", color = TextDim, fontSize = 13.sp)
                    Text("累计 ↑ ${traffic.total.trafficUpload()}", color = TextDim, fontSize = 13.sp)
                    Text("累计 ↓ ${traffic.total.trafficDownload()}", color = TextDim, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (status.phase == ClashPhase.Running) {
                        TvButton("断开连接", onClick = { viewModel.clash.disconnect() })
                    } else {
                        TvButton(
                            text = if (busy || status.phase == ClashPhase.Connecting || status.phase == ClashPhase.Preparing) {
                                "连接中…"
                            } else {
                                "连接 VPN"
                            },
                            onClick = { connect() },
                            primary = true,
                        )
                    }
                    TvButton(
                        text = "刷新节点",
                        onClick = {
                            scope.launch {
                                val result = viewModel.refreshClashGroups()
                                message = result.exceptionOrNull()?.friendlyMessage()
                                    ?: "节点状态已刷新"
                            }
                        },
                    )
                }
                message?.let {
                    Text(it, color = if (it.contains("失败") || it.contains("异常")) Danger else TextDim, fontSize = 12.sp)
                }
            }
        }

        item(key = "subscription") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle(title = "订阅设置", trailing = "默认：iKuuu_V2")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("名称", color = TextDim, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    TvTextField(
                        value = subscriptionName,
                        onValueChange = { subscriptionName = it },
                        onSearch = {},
                        placeholder = "订阅名称",
                        modifier = Modifier.width(220.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "配置更新时间：${formatDate(profileUpdatedAt).ifBlank { "尚未下载" }}",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("地址", color = TextDim, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    TvTextField(
                        value = subscriptionUrl,
                        onValueChange = { subscriptionUrl = it },
                        onSearch = {},
                        placeholder = "https://…",
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties { down = saveSubscriptionFocus },
                    )
                    Spacer(Modifier.width(10.dp))
                    TvButton(
                        text = "保存并更新订阅",
                        focusRequester = saveSubscriptionFocus,
                        onClick = {
                            if (busy) return@TvButton
                            scope.launch {
                                busy = true
                                val result = viewModel.updateClashSubscription(subscriptionName, subscriptionUrl)
                                busy = false
                                if (result.isSuccess) {
                                    message = "订阅已更新"
                                } else {
                                    message = result.exceptionOrNull()?.friendlyMessage()
                                }
                            }
                        },
                        primary = true,
                    )
                }
                Text(
                    text = "首次连接时会自动下载此订阅。订阅地址包含访问令牌，请勿分享。",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        }

        item(key = "options") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(title = "启动选项")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvButton(
                        text = if (settings.autoStart) "开机自动连接：开" else "开机自动连接：关",
                        onClick = {
                            scope.launch {
                                viewModel.saveAutoStart(!settings.autoStart)
                                message = if (!settings.autoStart) "已开启开机自动连接" else "已关闭开机自动连接"
                            }
                        },
                        primary = settings.autoStart,
                    )
                    Text(
                        text = "需先手动连接一次并授权 VPN 权限。",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item(key = "nodes-title") {
            Column {
                Spacer(Modifier.height(4.dp))
                SectionTitle(
                    title = "节点选择",
                    trailing = if (groups.isEmpty()) "连接或更新订阅后可选择" else "共 ${groups.size} 个策略组",
                )
            }
        }

        items(groups, key = { it.name }) { group ->
            GroupCard(
                group = group,
                onSelect = { proxy -> viewModel.selectClashProxy(group.name, proxy) },
                onHealthCheck = { viewModel.clash.healthCheck(group.name) },
            )
        }

        if (groups.isEmpty()) {
            item(key = "nodes-empty") {
                Text(
                    text = "连接 VPN 后会自动加载策略组和节点；也可以点击「保存并更新订阅」先下载配置。",
                    color = TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        item(key = "tips") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121722))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("使用说明", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("• 连接后所有网络请求（包括影片加载和播放）都会经过 Clash TUN 转发。", color = TextDim, fontSize = 12.sp)
                Text("• 首次使用请允许系统的 VPN 连接请求；若被拒绝，可再次点击连接。", color = TextDim, fontSize = 12.sp)
                Text("• 默认订阅为 iKuuu_V2，可在上方替换为自己的订阅地址。", color = TextDim, fontSize = 12.sp)
            }
        }
        }
        }
    }
}

@Composable
private fun GroupCard(
    group: ClashGroup,
    onSelect: (String) -> Unit,
    onHealthCheck: () -> Unit,
) {
    val healthFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.name,
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "当前：${group.selected.ifBlank { "自动" }}",
                color = Accent,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            TvButton("测速", onClick = onHealthCheck, focusRequester = healthFocus)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.proxies, key = { it.name }) { proxy ->
                Chip(
                    text = if (proxy.delay > 0) "${proxy.name}  ${proxy.delay}ms" else proxy.name,
                    selected = proxy.name == group.selected,
                    onClick = { onSelect(proxy.name) },
                    modifier = Modifier.focusProperties { up = healthFocus },
                )
            }
        }
    }
}
