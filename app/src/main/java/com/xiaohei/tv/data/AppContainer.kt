package com.xiaohei.tv.data

import android.content.Context
import com.xiaohei.tv.clash.ClashController
import com.xiaohei.tv.data.local.HistoryDatabase
import com.xiaohei.tv.data.provider.ProviderRegistry
import com.xiaohei.tv.data.provider.XiaoheimiProvider
import com.xiaohei.tv.data.provider.HongguoProvider
import com.xiaohei.tv.data.provider.InterfaceModuleStore
import com.xiaohei.tv.data.provider.gaze.GazeProvider
import com.xiaohei.tv.data.provider.gaze.GazeSession
import com.xiaohei.tv.data.remote.SiteHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val http = SiteHttpClient()
    val gaze = GazeSession(appContext)

    // 接入新站点：实现 MediaProvider 后加入此列表即可。
    val providers = ProviderRegistry(
        listOf(
            XiaoheimiProvider(http),
            GazeProvider(http, gaze),
            HongguoProvider(),
        )
    )
    val repository = MediaRepository(providers)
    internal val interfaceModules = InterfaceModuleStore(appContext, providers)
    val historyDao = HistoryDatabase.get(appContext).historyDao()
    val settings = SettingsStore(appContext)
    val clash = ClashController(appContext)
}
