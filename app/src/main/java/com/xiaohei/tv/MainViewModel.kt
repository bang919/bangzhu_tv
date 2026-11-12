package com.xiaohei.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohei.tv.data.AppSettings
import com.xiaohei.tv.data.SkipTimes
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.local.EpisodeProgress
import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
import com.xiaohei.tv.data.model.ProviderInfo
import com.xiaohei.tv.data.model.RankingSort
import com.xiaohei.tv.data.model.StreamInfo
import com.xiaohei.tv.data.model.VideoCard
import com.xiaohei.tv.data.model.VideoDetail
import com.xiaohei.tv.data.model.VideoType
import com.xiaohei.tv.clash.ClashGroup
import com.xiaohei.tv.clash.ClashStatus
import com.xiaohei.tv.clash.TrafficSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as XiaoheiApp).container
    private val repository = container.repository
    private val settingsStore = container.settings
    val clash = container.clash
    private val modules = container.interfaceModules
    private var checkedManifest: com.xiaohei.tv.data.provider.InterfaceUpdateClient.SignedManifest? = null
    private val _interfaceUpdateCount = MutableStateFlow(0)
    val interfaceUpdateCount = _interfaceUpdateCount.asStateFlow()
    private val _interfaceGeneration = MutableStateFlow(0)
    val interfaceGeneration = _interfaceGeneration.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modules.restore() }
            repository.clearSourceCaches()
            _interfaceGeneration.value++
            runCatching {
                val manifest = modules.client.check()
                checkedManifest = manifest
                _interfaceUpdateCount.value = manifest.manifest.updates(modules.installedVersions(), com.xiaohei.tv.data.provider.InterfaceModuleStore.HOST_ABI).size
            }
        }
    }

    suspend fun updateInterfaces(): Result<Int> = runCatching {
        val manifest = modules.client.check()
        checkedManifest = manifest
        val updates = manifest.manifest.updates(modules.installedVersions(), com.xiaohei.tv.data.provider.InterfaceModuleStore.HOST_ABI)
        try {
            for (module in updates) modules.install(manifest, module)
        } finally {
            repository.clearSourceCaches()
            _interfaceUpdateCount.value = manifest.manifest.updates(modules.installedVersions(), com.xiaohei.tv.data.provider.InterfaceModuleStore.HOST_ABI).size
            _interfaceGeneration.value++
        }
        updates.size
    }

    val history: StateFlow<List<HistoryEntry>> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val clashStatus: StateFlow<ClashStatus> = clash.status
    val clashTraffic: StateFlow<TrafficSnapshot> = clash.traffic
    val clashGroups: StateFlow<List<ClashGroup>> = clash.groups
    val profileUpdatedAt: StateFlow<Long> = clash.profileUpdatedAt

    val categorySorts: List<CategorySort> get() = repository.categorySorts(activeProviderId)
    val rankingSorts: List<RankingSort> get() = repository.rankingSorts(activeProviderId)
    val defaultProviderId: String get() = repository.defaultProviderId
    val activeProviderId: String get() = settings.value.providerId.ifBlank { defaultProviderId }
    val providerInfos: List<ProviderInfo> get() = repository.providerInfos
    val supportsRanking: Boolean get() = repository.supportsRanking(activeProviderId)
    val supportsSearchFilters: Boolean get() = repository.supportsSearchFilters(activeProviderId)

    fun supportsType(type: VideoType): Boolean = repository.supportsType(type, activeProviderId)

    fun setProvider(providerId: String) {
        viewModelScope.launch { settingsStore.setProviderId(providerId) }
    }

    fun categoryHref(type: VideoType, sort: CategorySort, providerId: String? = null): String =
        repository.categoryHref(type, sort, providerId ?: activeProviderId)

    fun categorySortOf(href: String?, providerId: String? = null): CategorySort =
        repository.categorySortOf(href, providerId ?: activeProviderId)

    fun refererFor(providerId: String?): String = repository.referer(providerId ?: activeProviderId)

    suspend fun loadHome(force: Boolean = false): HomeData =
        repository.loadHome(activeProviderId, force = force)

    suspend fun loadCategory(type: VideoType, href: String? = null): CategoryPage =
        repository.loadCategory(type, href, activeProviderId)

    suspend fun loadRanking(type: VideoType, sort: RankingSort, href: String? = null): CategoryPage =
        repository.loadRanking(type, sort, activeProviderId, href)

    suspend fun search(keyword: String): List<VideoCard> = repository.search(keyword, activeProviderId)
    suspend fun searchPage(keyword: String, page: Int) = repository.searchPage(keyword, page, activeProviderId)

    suspend fun loadDetail(videoId: String, providerId: String? = null): VideoDetail =
        repository.loadDetail(videoId, providerId ?: activeProviderId)

    suspend fun loadVersions(detail: VideoDetail): List<VideoCard> = repository.loadVersions(detail)

    suspend fun resolveStream(episodeHref: String, providerId: String? = null): StreamInfo =
        repository.resolveStream(episodeHref, providerId ?: activeProviderId)

    fun episodeProgress(providerId: String, videoId: String) = container.historyDao.observeEpisodes(providerId, videoId)

    private val progressMutex = kotlinx.coroutines.sync.Mutex()

    fun saveProgress(
        detail: VideoDetail,
        sourceIndex: Int,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long,
        watchedDeltaMs: Long = 0,
    ) {
        val episode = detail.sources.getOrNull(sourceIndex)?.episodes?.getOrNull(episodeIndex) ?: return
        viewModelScope.launch {
            progressMutex.lock()
            try {
            val previous = container.historyDao.findEpisode(detail.providerId, detail.id, episode.href)
            container.historyDao.upsertEpisode(EpisodeProgress(detail.providerId, detail.id, episode.href, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), (previous?.watchedMs ?: 0L) + watchedDeltaMs.coerceAtLeast(0)))
            container.historyDao.upsert(
                HistoryEntry(
                    videoId = detail.id,
                    title = detail.title,
                    poster = detail.poster,
                    typeName = detail.typeName,
                    remarks = detail.remarks,
                    sourceIndex = sourceIndex,
                    episodeIndex = episodeIndex,
                    episodeName = episode.name,
                    episodeHref = episode.href,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                    providerId = detail.providerId,
                )
            )
            } finally { progressMutex.unlock() }
        }
    }

    fun deleteHistory(entry: HistoryEntry) {
        viewModelScope.launch { container.historyDao.delete(entry.providerId, entry.videoId) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.historyDao.clear() }
    }

    suspend fun saveSubscription(name: String, url: String) {
        settingsStore.setSubscription(name, url)
    }

    suspend fun saveAutoStart(enabled: Boolean) {
        settingsStore.setAutoStart(enabled)
    }

    fun skipTimesFor(videoId: String): SkipTimes =
        settings.value.skipTimes[videoId] ?: SkipTimes()

    fun saveSkipTimes(videoId: String, times: SkipTimes) {
        viewModelScope.launch { settingsStore.saveSkipTimes(videoId, times) }
    }

    suspend fun saveVpnEnabled(enabled: Boolean) {
        settingsStore.setVpnEnabled(enabled)
        if (!enabled) clash.disconnect()
    }

    suspend fun connectClash(): Result<Unit> {
        if (!settings.value.vpnEnabled) {
            return Result.failure(IllegalStateException("VPN 功能已关闭，请先在设置中开启"))
        }
        return clash.connect(settings.value)
    }

    suspend fun updateClashSubscription(name: String, url: String): Result<Unit> {
        settingsStore.setSubscription(name, url)
        val updated = settings.value.copy(subscriptionName = name.trim(), subscriptionUrl = url.trim())
        return clash.updateSubscription(updated)
    }

    suspend fun refreshClashGroups(): Result<List<ClashGroup>> = clash.refreshGroups()

    fun selectClashProxy(group: String, proxy: String) {
        if (clash.selectProxy(group, proxy)) {
            viewModelScope.launch { settingsStore.saveSelection(group, proxy) }
            viewModelScope.launch { clash.refreshGroups() }
        }
    }

    fun healthCheck(group: String) {
        clash.healthCheck(group)
    }
}
