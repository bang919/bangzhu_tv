package com.xiaohei.tv.data.net

import android.content.Context
import com.xiaohei.tv.R
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Android 7.0 之前的系统信任库不包含 ISRG Root X1，站点图片/视频 CDN 使用
 * Let's Encrypt 证书时会握手失败，这里在系统根证书之外补充该公共根。
 */
object TlsCompat {
    @Volatile
    private var sslContext: SSLContext? = null

    @Volatile
    private var trustManager: X509TrustManager? = null

    fun initialize(context: Context) {
        if (sslContext != null) return
        synchronized(this) {
            if (sslContext != null) return
            runCatching {
                val system = systemTrustManager()
                val extra = certificateTrustManager(context)
                val combined = CombinedTrustManager(system, extra)
                val context = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<X509TrustManager>(combined), SecureRandom())
                }
                sslContext = context
                trustManager = combined
            }
        }
    }

    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val context = sslContext
        val manager = trustManager
        if (context != null && manager != null) {
            builder.sslSocketFactory(context.socketFactory, manager)
        }
        return builder
    }

    private fun systemTrustManager(): X509TrustManager =
        trustManager(null)

    private fun certificateTrustManager(context: Context): X509TrustManager {
        val certificate = context.resources.openRawResource(R.raw.isrg_root_x1).use { stream ->
            CertificateFactory.getInstance("X.509").generateCertificate(stream)
        }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("isrg-root-x1", certificate)
        }
        return trustManager(keyStore)
    }

    private fun trustManager(keyStore: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private class CombinedTrustManager(
        private val system: X509TrustManager,
        private val extra: X509TrustManager,
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            system.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (error: CertificateException) {
                extra.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + extra.acceptedIssuers
    }
}
