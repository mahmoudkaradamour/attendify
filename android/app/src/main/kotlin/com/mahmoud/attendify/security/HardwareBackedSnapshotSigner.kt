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

    fun verify(data: ByteArray, signatureBytes: ByteArray, certChain: List<ByteArray>): Boolean {
        return try {
            // Reconstruct public key from first certificate in chain
            val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(java.io.ByteArrayInputStream(certChain[0]))
            val pubKey = cert.publicKey

            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(pubKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}