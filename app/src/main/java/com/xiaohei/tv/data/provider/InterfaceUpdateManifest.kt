package com.xiaohei.tv.data.provider

import java.security.PublicKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class InterfaceUpdateManifest(
    val schema: Int,
    val modules: List<InterfaceModule>,
) {
    /** Only offer strictly newer modules that support this host's plugin ABI. */
    fun updates(installed: Map<String, Int>, hostVersion: Int): List<InterfaceModule> =
        modules.filter { module ->
            hostVersion in module.minHostVersion..module.maxHostVersion &&
                module.version > (installed[module.id] ?: 1)
        }
}

@Serializable
internal data class InterfaceModule(
    val id: String,
    val version: Int,
    val minHostVersion: Int,
    val maxHostVersion: Int,
    val url: String,
    val sha256: String,
)

internal object InterfaceManifestReader {
    private const val MAX_MANIFEST_SIZE = 64 * 1024
    private val supportedIds = setOf("hongguo", "xiaoheimi")
    private val json = Json { ignoreUnknownKeys = false }

    fun read(bytes: ByteArray, signature: ByteArray, key: PublicKey): InterfaceUpdateManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_SIZE) { "接口清单大小无效" }
        require(InterfaceUpdateVerifier.verifyManifest(bytes, signature, key)) { "接口清单签名无效" }
        val manifest = json.decodeFromString<InterfaceUpdateManifest>(bytes.toString(Charsets.UTF_8))
        require(manifest.schema == 1 && manifest.modules.size <= supportedIds.size) { "接口清单格式不支持" }
        require(manifest.modules.map { it.id }.distinct().size == manifest.modules.size) { "接口清单包含重复来源" }
        manifest.modules.forEach { module ->
            require(module.id in supportedIds && module.version > 0) { "接口版本无效" }
            require(module.minHostVersion > 0 && module.maxHostVersion >= module.minHostVersion) { "兼容版本无效" }
            require(module.sha256.matches(Regex("[a-f0-9]{64}"))) { "模块摘要无效" }
            val url = module.url.toHttpUrlOrNull()
            val allowedPath = url != null && (
                url.host == "github.com" && url.encodedPath.startsWith("/bang919/bangzhu_tv/releases/download/") ||
                url.host == "raw.githubusercontent.com" && url.encodedPath.startsWith("/bang919/bangzhu_tv/main/interface-updates/modules/")
            )
            require(url != null && url.scheme == "https" && allowedPath &&
                url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
                url.encodedQuery == null) { "模块地址无效" }
        }
        return manifest
    }
}
