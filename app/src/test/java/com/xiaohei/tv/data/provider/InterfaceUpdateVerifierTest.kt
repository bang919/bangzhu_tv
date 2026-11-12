package com.xiaohei.tv.data.provider

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.*
import org.junit.Test

class InterfaceUpdateVerifierTest {
    @Test fun rejectsModifiedManifestAndPayload() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val manifest = "version=2".toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private); update(manifest); sign()
        }
        assertTrue(InterfaceUpdateVerifier.verifyManifest(manifest, signature, keys.public))
        assertFalse(InterfaceUpdateVerifier.verifyManifest("version=3".toByteArray(), signature, keys.public))
        val hash = MessageDigest.getInstance("SHA-256").digest(manifest)
        assertTrue(InterfaceUpdateVerifier.verifyPayload(manifest, hash))
        assertFalse(InterfaceUpdateVerifier.verifyPayload("version=3".toByteArray(), hash))
        assertFalse(InterfaceUpdateVerifier.verifyPayload(manifest, byteArrayOf()))
    }
}
