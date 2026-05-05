package com.mahmoud.attendify.security

import java.security.Signature

/**
 * HardwareBackedSnapshotSigner
 *
 * Signs snapshot hashes using hardware‑backed private keys.
 */
object HardwareBackedSnapshotSigner {

    fun sign(data: ByteArray): ByteArray {

        val privateKey =
            HardwareBackedKeyManager.getOrCreateKey()

        val signature =
            Signature.getInstance("SHA256withECDSA")

        signature.initSign(privateKey)
        signature.update(data)

        return signature.sign()
    }
}