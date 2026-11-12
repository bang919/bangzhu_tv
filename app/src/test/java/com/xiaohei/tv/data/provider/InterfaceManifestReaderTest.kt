package com.xiaohei.tv.data.provider

import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InterfaceManifestReaderTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun read(text: String): InterfaceUpdateManifest {
        val bytes = text.toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(bytes)
            sign()
        }
        return InterfaceManifestReader.read(bytes, signature, keys.public)
    }

    private fun manifest(url: String, id: String = "hongguo") = """
        {"schema":1,"modules":[{"id":"$id","version":2,"minHostVersion":3,
        "maxHostVersion":3,"url":"$url","sha256":"${"a".repeat(64)}"}]}
    """.trimIndent()

    @Test fun acceptsTrustedReleaseAddress() {
        val url = "https://github.com/bang919/bangzhu_tv/releases/download/v1/module.apk"
        val data = read(manifest(url))
        assertEquals("hongguo", data.modules.single().id)
        assertEquals(1, data.updates(mapOf("hongguo" to 1), 3).size)
        assertEquals(0, data.updates(mapOf("hongguo" to 2), 3).size)
        assertEquals(0, data.updates(mapOf("hongguo" to 1), 4).size)
        val raw = "https://raw.githubusercontent.com/bang919/bangzhu_tv/main/interface-updates/modules/sources-v2.zip"
        assertEquals("hongguo", read(manifest(raw)).modules.single().id)
    }

    @Test fun rejectsUntrustedHostAndUnknownProvider() {
        assertThrows(IllegalArgumentException::class.java) {
            read(manifest("https://example.com/bang919/bangzhu_tv/releases/download/v1/module.apk"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            read(manifest("https://github.com/bang919/bangzhu_tv/releases/download/v1/module.apk", "unknown"))
        }
    }
}
