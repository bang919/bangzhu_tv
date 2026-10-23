package com.xiaohei.tv.data.remote

import com.xiaohei.tv.data.net.TlsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SiteHttpClient {
    private val client = TlsCompat.configure(
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
    ).build()

    suspend fun get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
                if (!referer.isNullOrBlank()) builder.header("Referer", referer)
                val request = builder
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("请求失败：HTTP ${response.code}")
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) throw IOException("服务器返回空内容")
                    return@withContext body
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt == 0) Thread.sleep(350)
            }
        }
        throw IOException(lastError?.message ?: "网络请求失败", lastError)
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
