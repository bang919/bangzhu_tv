package com.xiaohei.tv.data.provider

import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

/** Verify the exact downloaded manifest before interpreting any of its fields. */
internal object InterfaceUpdateVerifier {
    fun verifyManifest(bytes: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean =
        runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(publicKey)
                update(bytes)
                verify(signature)
            }
        }.getOrDefault(false)

    fun verifyPayload(bytes: ByteArray, expectedSha256: ByteArray): Boolean =
        expectedSha256.size == 32 && MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(bytes), expectedSha256,
        )
}
