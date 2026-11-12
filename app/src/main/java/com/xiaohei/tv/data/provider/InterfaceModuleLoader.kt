package com.xiaohei.tv.data.provider

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.io.IOException

/** Loads a previously authenticated module from app-private storage. */
internal class InterfaceModuleLoader(private val context: Context) {
    fun load(moduleId: String, candidate: File): MediaProvider {
        require(moduleId == "hongguo" || moduleId == "xiaoheimi") { "未知视频源" }
        val canonical = candidate.canonicalFile
        val root = File(context.filesDir, "interface-modules").canonicalFile
        require(canonical.parentFile == root && canonical.isFile && !canonical.canWrite()) {
            "接口模块必须是私有目录中的只读文件"
        }
        val loader = SourceClassLoader(
            canonical.path,
            context.codeCacheDir.absolutePath,
            requireNotNull(MediaProvider::class.java.classLoader),
            moduleId,
        )
        val entry = when (moduleId) {
            "hongguo" -> "com.xiaohei.tv.data.provider.HongguoProvider"
            else -> "com.xiaohei.tv.data.provider.XiaoheimiProvider"
        }
        val instance = try {
            loader.loadClass(entry).getDeclaredConstructor().newInstance()
        } catch (failure: ReflectiveOperationException) {
            throw IOException("接口模块无法加载", failure)
        } catch (failure: LinkageError) {
            throw IOException("接口模块与当前应用不兼容", failure)
        }
        val provider = instance as? MediaProvider ?: throw IOException("接口模块类型无效")
        if (provider.id != moduleId) throw IOException("接口模块来源不匹配")
        return provider
    }
}

/** Load implementation and its private dependencies from the update, sharing host ABI types. */
private class SourceClassLoader(path: String, optimized: String, parent: ClassLoader, private val id: String) :
    DexClassLoader(path, optimized, null, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        val own = if (id == "hongguo") {
            name.startsWith("com.hongguotv.core.") ||
                name == "com.xiaohei.tv.data.provider.HongguoProvider" ||
                name.startsWith("com.xiaohei.tv.data.provider.HongguoProvider$") ||
                name == "com.xiaohei.tv.data.provider.HongguoProviderKt" ||
                name.startsWith("com.xiaohei.tv.data.provider.HongguoProviderKt$")
        } else {
            name.startsWith("com.xiaohei.tv.data.provider.maccms.") ||
                name == "com.xiaohei.tv.data.remote.SiteHttpClient" ||
                name.startsWith("com.xiaohei.tv.data.remote.SiteHttpClient$") ||
                name == "com.xiaohei.tv.data.provider.XiaoheimiProvider" ||
                name.startsWith("com.xiaohei.tv.data.provider.XiaoheimiProvider$")
        }
        if (!own) return super.loadClass(name, resolve)
        synchronized(this) {
            val loaded = findLoadedClass(name) ?: findClass(name)
            if (resolve) resolveClass(loaded)
            return loaded
        }
    }
}
