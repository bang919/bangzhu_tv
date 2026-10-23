package com.xiaohei.tv.clash

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxySort
import com.xiaohei.tv.data.AppSettings
import com.xiaohei.tv.data.net.TlsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

enum class ClashPhase { Stopped, Preparing, Connecting, Running, Error }

data class ClashStatus(
    val phase: ClashPhase = ClashPhase.Stopped,
    val message: String = "未连接",
)

data class TrafficSnapshot(
    val now: Long = 0,
    val total: Long = 0,
)

data class ClashGroup(
    val name: String,
    val type: String,
    val selected: String,
    val proxies: List<ClashProxy>,
)

data class ClashProxy(
    val name: String,
    val type: String,
    val delay: Int,
)

class ClashController(context: Context) {
    private val appContext = context.applicationContext
    private val profileDir = File(appContext.filesDir, "clash")
    private val configFile = File(profileDir, "config.yaml")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = TlsCompat.configure(
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
    ).build()

    private val _status = MutableStateFlow(ClashStatus())
    val status: StateFlow<ClashStatus> = _status.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _groups = MutableStateFlow<List<ClashGroup>>(emptyList())
    val groups: StateFlow<List<ClashGroup>> = _groups.asStateFlow()

    private val _profileUpdatedAt = MutableStateFlow(configFile.lastModified())
    val profileUpdatedAt: StateFlow<Long> = _profileUpdatedAt.asStateFlow()

    private var trafficJob: Job? = null
    private val operationMutex = Mutex()

    @Volatile
    var isCoreLoaded = false
        private set

    val hasProfile: Boolean
        get() = configFile.exists() && configFile.length() > 0

    fun updateStatus(phase: ClashPhase, message: String) {
        _status.value = ClashStatus(phase, message)
        if (phase == ClashPhase.Running) startTrafficPolling()
        if (phase == ClashPhase.Stopped || phase == ClashPhase.Error) stopTrafficPolling()
    }

    suspend fun connect(settings: AppSettings): Result<Unit> = operationMutex.withLock {
        runCatching {
            if (_status.value.phase == ClashPhase.Running) return@runCatching
            updateStatus(ClashPhase.Preparing, "正在准备订阅…")
            if (!hasProfile) {
                downloadSubscription(settings.subscriptionUrl)
            }
            loadProfile()
            applySavedSelections(settings.groupSelections)
            updateStatus(ClashPhase.Connecting, "正在启动 VPN…")
            startVpnService()
        }.onFailure { error ->
            Log.e(TAG, "connect failed", error)
            updateStatus(ClashPhase.Error, error.message ?: "连接失败")
        }
    }

    fun disconnect() {
        val intent = Intent(appContext, ClashVpnService::class.java).setAction(ClashVpnService.ACTION_DISCONNECT)
        runCatching { appContext.startService(intent) }
        updateStatus(ClashPhase.Stopped, "未连接")
    }

    suspend fun updateSubscription(settings: AppSettings): Result<Unit> = operationMutex.withLock {
        runCatching {
            val wasRunning = _status.value.phase == ClashPhase.Running
            updateStatus(ClashPhase.Preparing, "正在更新订阅…")
            downloadSubscription(settings.subscriptionUrl)
            if (wasRunning) {
                appContext.stopService(Intent(appContext, ClashVpnService::class.java))
                delay(500)
            }
            isCoreLoaded = false
            loadProfile()
            applySavedSelections(settings.groupSelections)
            _groups.value = queryGroups()
            if (wasRunning) {
                updateStatus(ClashPhase.Connecting, "订阅已更新，正在重连…")
                startVpnService()
            } else {
                updateStatus(ClashPhase.Stopped, "订阅已更新")
            }
        }.onFailure { error ->
            Log.e(TAG, "update subscription failed", error)
            updateStatus(ClashPhase.Error, error.message ?: "订阅更新失败")
        }
    }

    suspend fun refreshGroups(): Result<List<ClashGroup>> = runCatching {
        if (!isCoreLoaded) throw IllegalStateException("请先连接 VPN 或加载订阅")
        queryGroups().also { _groups.value = it }
    }

    fun selectProxy(group: String, proxy: String): Boolean {
        return runCatching { Clash.patchSelector(group, proxy) }.getOrDefault(false)
    }

    fun healthCheck(group: String) {
        runCatching { Clash.healthCheck(group) }
    }

    internal suspend fun ensureProfileLoaded() {
        if (!hasProfile) throw IllegalStateException("尚未下载订阅配置")
        loadProfile()
    }

    internal fun onVpnRunning() {
        updateStatus(ClashPhase.Running, "已连接")
    }

    internal fun onVpnStopped(error: String? = null) {
        if (error != null) updateStatus(ClashPhase.Error, error) else updateStatus(ClashPhase.Stopped, "未连接")
    }

    private suspend fun loadProfile() {
        if (isCoreLoaded) return
        if (!hasProfile) throw IllegalStateException("尚未下载订阅配置")
        withContext(Dispatchers.IO) {
            Clash.load(profileDir).await()
            isCoreLoaded = true
        }
        _groups.value = queryGroups()
    }

    private suspend fun downloadSubscription(url: String) {
        if (url.isBlank()) throw IllegalArgumentException("订阅地址为空")
        withContext(Dispatchers.IO) {
            profileDir.mkdirs()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "clash.meta")
                .header("Accept", "*/*")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("订阅下载失败：HTTP ${response.code}")
                val body = response.body?.bytes() ?: throw IllegalStateException("订阅内容为空")
                val text = body.toString(Charsets.UTF_8)
                if (!text.contains("proxies:") &&
                    !text.contains("proxy-providers:") &&
                    !text.contains("proxy-groups:")
                ) {
                    throw IllegalStateException("订阅内容不是有效的 Clash 配置")
                }
                val temp = File(profileDir, "config.yaml.tmp")
                temp.writeText(text)
                if (configFile.exists()) configFile.delete()
                if (!temp.renameTo(configFile)) {
                    temp.copyTo(configFile, overwrite = true)
                    temp.delete()
                }
            }
            isCoreLoaded = false
            _profileUpdatedAt.value = configFile.lastModified()
        }
    }

    private suspend fun applySavedSelections(selections: Map<String, String>) {
        if (selections.isEmpty() || !isCoreLoaded) return
        withContext(Dispatchers.IO) {
            selections.forEach { (group, proxy) ->
                runCatching { Clash.patchSelector(group, proxy) }
            }
        }
    }

    private suspend fun queryGroups(): List<ClashGroup> = withContext(Dispatchers.IO) {
        val names = runCatching { Clash.queryGroupNames(true) }.getOrDefault(emptyList())
        names.mapNotNull { name ->
            runCatching {
                val group = Clash.queryGroup(name, ProxySort.Default)
                ClashGroup(
                    name = name,
                    type = group.type,
                    selected = group.now,
                    proxies = group.proxies
                        .filterNot { it.name.startsWith("DIRECT") || it.name.startsWith("REJECT") }
                        .map { ClashProxy(it.name, it.type, it.delay) },
                )
            }.getOrNull()?.takeIf { it.proxies.isNotEmpty() }
        }
    }

    private fun startVpnService() {
        val intent = Intent(appContext, ClashVpnService::class.java)
            .setAction(ClashVpnService.ACTION_CONNECT)
        androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
    }

    private fun startTrafficPolling() {
        if (trafficJob?.isActive == true) return
        trafficJob = scope.launch {
            while (true) {
                runCatching {
                    _traffic.value = TrafficSnapshot(
                        now = Clash.queryTrafficNow(),
                        total = Clash.queryTrafficTotal(),
                    )
                }
                delay(1000)
            }
        }
    }

    private fun stopTrafficPolling() {
        trafficJob?.cancel()
        trafficJob = null
        _traffic.value = TrafficSnapshot()
    }

    companion object {
        private const val TAG = "ClashController"

        fun vpnPermissionIntent(context: Context): Intent? = VpnService.prepare(context)
    }
}
