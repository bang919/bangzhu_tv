package com.xiaohei.tv.data.provider

class ProviderRegistry(providers: List<MediaProvider>) {
    private val byId = providers.associateBy { it.id }

    val all: List<MediaProvider> = providers

    val default: MediaProvider = providers.firstOrNull()
        ?: error("至少需要注册一个站点")

    fun find(id: String?): MediaProvider = id?.let { byId[it] } ?: default
}
