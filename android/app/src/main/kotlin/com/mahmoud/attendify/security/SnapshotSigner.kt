package com.mahmoud.attendify.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * SnapshotSigner
 *
 * Performs hashing and HMAC signing
 * for physical reality snapshots.
 */
object SnapshotSigner {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private val secretKey =
        SecretKeySpec(
            "ATTENDIFY_INTERNAL_SECRET".toByteArray(),
            HMAC_ALGORITHM
        )

    fun sign(data: ByteArray): Pair<ByteArray, ByteArray> {

        val hash =
            MessageDigest
                .getInstance("SHA-256")
                .digest(data)

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(secretKey)

        val signature = mac.doFinal(hash)

        return hash to signature
    }
}