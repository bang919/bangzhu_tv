package com.xiaohei.tv.data.provider

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installs verified modules without replacing the last known good module on failure. */
internal class InterfaceModuleStore(context: Context, private val registry: ProviderRegistry, transport: okhttp3.OkHttpClient? = null) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "interface-modules").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("interface-modules", Context.MODE_PRIVATE)
    private val key = app.assets.open("interface-updates/signing.cer").use {
        CertificateFactory.getInstance("X.509").generateCertificate(it).publicKey
    }
    private val loader = InterfaceModuleLoader(app)
    val client = if (transport == null) InterfaceUpdateClient(key) else InterfaceUpdateClient(key, transport)

    fun installedVersions(): Map<String, Int> = IDS.associateWith { prefs.getInt("$it.version", 1) }

    suspend fun install(signed: InterfaceUpdateClient.SignedManifest, module: InterfaceModule) = withContext(Dispatchers.IO) {
        val validated = InterfaceManifestReader.read(signed.bytes, signed.signature, key)
        require(module in validated.modules && module.id in IDS && module.version > (installedVersions()[module.id] ?: 1))
        require(HOST_ABI in module.minHostVersion..module.maxHostVersion)
        val candidate = File.createTempFile("${module.id}-", ".zip", root)
        try {
            client.download(module, candidate)
            val provider = loader.load(module.id, candidate)
            val previous = prefs.getString("${module.id}.file", null)
            val saved = prefs.edit()
                .putString("${module.id}.file", candidate.name)
                .putInt("${module.id}.version", module.version)
                .putString("${module.id}.manifest", Base64.encodeToString(signed.bytes, Base64.NO_WRAP))
                .putString("${module.id}.signature", Base64.encodeToString(signed.signature, Base64.NO_WRAP))
                .commit()
            if (!saved) throw IOException("无法保存接口版本")
            registry.replace(provider)
            previous?.takeIf { it != candidate.name }?.let { File(root, it).delete() }
        } catch (problem: Throwable) {
            candidate.delete()
            throw problem
        }
    }

    /** Revalidate both the saved manifest signature and module hash after every restart. */
    fun restore() {
        for (id in IDS) {
            val fileName = prefs.getString("$id.file", null) ?: continue
            try {
                require(fileName.matches(Regex("${id}-[0-9]+\\.zip")))
                val manifest = Base64.decode(requireNotNull(prefs.getString("$id.manifest", null)), Base64.NO_WRAP)
                val signature = Base64.decode(requireNotNull(prefs.getString("$id.signature", null)), Base64.NO_WRAP)
                val entry = InterfaceManifestReader.read(manifest, signature, key).modules.single { it.id == id }
                require(entry.version == prefs.getInt("$id.version", 1) && HOST_ABI in entry.minHostVersion..entry.maxHostVersion)
                val file = File(root, fileName)
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                require(MessageDigest.isEqual(digest.digest(), entry.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()))
                registry.replace(loader.load(id, file))
            } catch (_: Exception) {
                registry.restore(id)
                prefs.edit().remove("$id.file").remove("$id.version").remove("$id.manifest").remove("$id.signature").apply()
            } catch (_: LinkageError) {
                registry.restore(id)
                prefs.edit().remove("$id.file").remove("$id.version").remove("$id.manifest").remove("$id.signature").apply()
            }
        }
    }

    companion object {
        const val HOST_ABI = 3
        val IDS = listOf("hongguo", "xiaoheimi")
    }
}
