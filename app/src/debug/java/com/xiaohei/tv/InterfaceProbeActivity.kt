package com.xiaohei.tv

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.xiaohei.tv.data.provider.InterfaceModuleLoader
import com.xiaohei.tv.data.provider.InterfaceModuleStore
import com.xiaohei.tv.data.provider.InterfaceManifestReader
import com.xiaohei.tv.data.provider.InterfaceUpdateClient
import com.xiaohei.tv.data.provider.ProviderRegistry
import com.xiaohei.tv.data.provider.HongguoProvider
import com.xiaohei.tv.data.provider.XiaoheimiProvider
import java.security.cert.CertificateFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File

/** Debug-only probe for checking that real dex modules resolve against the host ABI. */
class InterfaceProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val candidate = File(filesDir, "interface-modules/probe.zip")
        try {
            check(candidate.setReadOnly())
            val loader = InterfaceModuleLoader(this)
            for (id in listOf("hongguo", "xiaoheimi")) {
                val provider = loader.load(id, candidate)
                check(provider.id == id)
                check(provider.supportedTypes.isNotEmpty())
                check(provider.categoryHref(com.xiaohei.tv.data.model.VideoType.SERIES, com.xiaohei.tv.data.model.CategorySort.LATEST).isNotBlank())
                Log.i("InterfaceProbe", "$id OK ${provider.name} ${provider.baseUrl} ${provider.javaClass.classLoader}")
            }
            val key = assets.open("interface-updates/signing.cer").use { CertificateFactory.getInstance("X.509").generateCertificate(it).publicKey }
            val bytes = File(filesDir, "probe-manifest.json").readBytes()
            val signature = File(filesDir, "probe-manifest.sig").readBytes()
            val manifest = InterfaceManifestReader.read(bytes, signature, key)
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                val content = when {
                    chain.request().url.encodedPath.endsWith("manifest.json") -> bytes
                    chain.request().url.encodedPath.endsWith("manifest.sig") -> signature
                    else -> candidate.readBytes()
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(content.toResponseBody()).build()
            }.build()
            getSharedPreferences("interface-modules", MODE_PRIVATE).edit().clear().commit()
            val registry = ProviderRegistry(listOf(XiaoheimiProvider(), HongguoProvider()))
            val store = InterfaceModuleStore(this, registry, transport)
            val signed = runBlocking { store.client.check() }
            check(signed.manifest == manifest)
            check(signed.manifest.updates(store.installedVersions(), 3).size == 2)
            runBlocking { for (module in manifest.modules) store.install(signed, module) }
            check(store.installedVersions().values.all { it == 2 })
            for (id in listOf("hongguo", "xiaoheimi")) check(registry.find(id).javaClass.classLoader !== javaClass.classLoader)
            val restored = ProviderRegistry(listOf(XiaoheimiProvider(), HongguoProvider()))
            InterfaceModuleStore(this, restored, transport).restore()
            for (id in listOf("hongguo", "xiaoheimi")) check(restored.find(id).javaClass.classLoader !== javaClass.classLoader)
            Log.i("InterfaceProbe", "INSTALL AND RESTART OK")
            val prefs = getSharedPreferences("interface-modules", MODE_PRIVATE)
            val broken = File(filesDir, "interface-modules/" + prefs.getString("hongguo.file", ""))
            check(broken.setWritable(true))
            broken.appendText("corrupted")
            check(broken.setReadOnly())
            val fallback = ProviderRegistry(listOf(XiaoheimiProvider(), HongguoProvider()))
            InterfaceModuleStore(this, fallback, transport).restore()
            check(fallback.find("hongguo").javaClass.classLoader === javaClass.classLoader)
            check(fallback.find("xiaoheimi").javaClass.classLoader !== javaClass.classLoader)
            Log.i("InterfaceProbe", "CORRUPTION FALLBACK OK")
        } catch (error: Throwable) {
            Log.e("InterfaceProbe", "MODULE FAILED", error)
        }
        finish()
    }
}
