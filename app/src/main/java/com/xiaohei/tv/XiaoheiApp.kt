package com.xiaohei.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.github.kr328.clash.common.Global
import com.xiaohei.tv.data.AppContainer
import com.xiaohei.tv.data.net.TlsCompat

class XiaoheiApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        TlsCompat.initialize(this)
        Global.init(this)
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(TlsCompat.configure(okhttp3.OkHttpClient.Builder()).build())
        .crossfade(true)
        .build()
}
