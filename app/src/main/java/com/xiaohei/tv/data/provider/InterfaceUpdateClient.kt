package com.xiaohei.tv.data.provider

import java.io.IOException
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads a bounded manifest and detached signature; failures leave installed sources untouched. */
internal class InterfaceUpdateClient(
    private val publicKey: PublicKey,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build(),
) {
    data class SignedManifest(val manifest: InterfaceUpdateManifest, val bytes: ByteArray, val signature: ByteArray)

    suspend fun check(): SignedManifest = withContext(Dispatchers.IO) {
        val manifest = get(MANIFEST_URL, 64 * 1024)
        val signature = get(SIGNATURE_URL, 1024)
        SignedManifest(InterfaceManifestReader.read(manifest, signature, publicKey), manifest, signature)
    }

    /** A candidate is never made active until the signed manifest's digest matches. */
    suspend fun download(module: InterfaceModule, destination: File): File = withContext(Dispatchers.IO) {
        val downloadClient = client.newBuilder().followRedirects(true).followSslRedirects(false)
            .readTimeout(30, TimeUnit.SECONDS).callTimeout(2, TimeUnit.MINUTES).build()
        val request = Request.Builder().url(module.url).build()
        try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("接口模块下载失败：HTTP ${response.code}")
                if (response.request.url.scheme != "https") throw IOException("接口模块下载连接不安全")
                val body = response.body ?: throw IOException("接口模块为空")
                if (body.contentLength() > MAX_MODULE_SIZE) throw IOException("接口模块过大")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                destination.outputStream().use { output ->
                    if (!destination.setReadOnly()) throw IOException("无法保护接口模块")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_MODULE_SIZE) throw IOException("接口模块过大")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val expected = module.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                if (total == 0L || !MessageDigest.isEqual(digest.digest(), expected))
                    throw IOException("接口模块校验失败")
            }
            destination
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        }
    }

    private fun get(url: String, maxBytes: Long): ByteArray {
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("接口更新检查失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("接口更新响应为空")
            if (body.contentLength() > maxBytes) throw IOException("接口更新响应过大")
            val source = body.source()
            val buffer = okio.Buffer()
            while (true) {
                val read = source.read(buffer, minOf(8192L, maxBytes + 1 - buffer.size))
                if (read == -1L) break
                if (buffer.size > maxBytes) throw IOException("接口更新响应过大")
            }
            return buffer.readByteArray()
        }
    }

    companion object {
        private const val MAX_MODULE_SIZE = 150L * 1024 * 1024
        const val MANIFEST_URL = "https://raw.githubusercontent.com/bang919/bangzhu_tv/main/interface-updates/manifest.json"
        const val SIGNATURE_URL = "https://raw.githubusercontent.com/bang919/bangzhu_tv/main/interface-updates/manifest.sig"
    }
}
