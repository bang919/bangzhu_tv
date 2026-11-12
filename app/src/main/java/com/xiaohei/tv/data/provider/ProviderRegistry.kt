package com.xiaohei.tv.data.provider

class ProviderRegistry(providers: List<MediaProvider>) {
    private val builtIn = providers.associateBy { it.id }
    private val order = providers.map { it.id }
    @Volatile private var current = builtIn

    val all: List<MediaProvider> get() {
        val snapshot = current
        return order.map { snapshot.getValue(it) }
    }

    private val defaultId = providers.firstOrNull()?.id ?: error("至少需要注册一个站点")
    val default: MediaProvider get() = current.getValue(defaultId)

    fun find(id: String?): MediaProvider {
        val snapshot = current
        return id?.let { snapshot[it] } ?: snapshot.getValue(defaultId)
    }

    /** Replace a known source atomically; callers validate a candidate before publishing it. */
    @Synchronized
    fun replace(provider: MediaProvider) {
        require(provider.id in builtIn) { "未知视频源" }
        current = current + (provider.id to provider)
    }

    @Synchronized
    fun restore(id: String) {
        val original = requireNotNull(builtIn[id]) { "未知视频源" }
        current = current + (id to original)
    }
}
