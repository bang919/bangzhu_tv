package com.xiaohei.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohei.tv.data.AppSettings
import com.xiaohei.tv.data.local.HistoryEntry
import com.xiaohei.tv.data.model.CategoryPage
import com.xiaohei.tv.data.model.CategorySort
import com.xiaohei.tv.data.model.HomeData
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as XiaoheiApp).container
    private val repository = container.repository
    private val settingsStore = container.settings
    val clash = container.clash

    val history: StateFlow<List<HistoryEntry>> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.data
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val clashStatus: StateFlow<ClashStatus> = clash.status
    val clashTraffic: StateFlow<TrafficSnapshot> = clash.traffic
    val clashGroups: StateFlow<List<ClashGroup>> = clash.groups
    val profileUpdatedAt: StateFlow<Long> = clash.profileUpdatedAt

    val categorySorts: List<CategorySort> get() = repository.categorySorts()
    val rankingSorts: List<RankingSort> get() = repository.rankingSorts()

    fun categoryHref(type: VideoType, sort: CategorySort, providerId: String? = null): String =
        repository.categoryHref(type, sort, providerId)

    fun categorySortOf(href: String?, providerId: String? = null): CategorySort =
        repository.categorySortOf(href, providerId)

    fun refererFor(providerId: String?): String = repository.referer(providerId)

    suspend fun loadHome(force: Boolean = false): HomeData = repository.loadHome(force = force)

    suspend fun loadCategory(type: VideoType, href: String? = null): CategoryPage =
        repository.loadCategory(type, href)

    suspend fun loadRanking(type: VideoType, sort: RankingSort): CategoryPage =
        repository.loadRanking(type, sort)

    suspend fun search(keyword: String): List<VideoCard> = repository.search(keyword)

    suspend fun loadDetail(videoId: String, providerId: String? = null): VideoDetail =
        repository.loadDetail(videoId, providerId)

    suspend fun loadVersions(detail: VideoDetail): List<VideoCard> = repository.loadVersions(detail)

    suspend fun resolveStream(episodeHref: String, providerId: String? = null): StreamInfo =
        repository.resolveStream(episodeHref, providerId)

    fun saveProgress(
        detail: VideoDetail,
        sourceIndex: Int,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        val episode = detail.sources.getOrNull(sourceIndex)?.episodes?.getOrNull(episodeIndex) ?: return
        if (positionMs <= 0) return
        viewModelScope.launch {
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
        }
    }

    fun deleteHistory(videoId: String) {
        viewModelScope.launch { container.historyDao.delete(videoId) }
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
