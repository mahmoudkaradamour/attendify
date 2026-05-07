package com.mahmoud.attendify.orchestration.security

import java.security.KeyPairGenerator
import java.security.Signature
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

object SnapshotCryptoSigner {

    private const val KEY_ALIAS = "AttendifySnapshotKey"

    private fun getOrCreateKey(): java.security.PrivateKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)

        if (!ks.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            generator.initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build()
            )

            generator.generateKeyPair()
        }

        val entry = ks.getEntry(KEY_ALIAS, null)
                as java.security.KeyStore.PrivateKeyEntry

        return entry.privateKey
    }

    fun sign(payload: ByteArray): ByteArray {
        val privateKey = getOrCreateKey()

        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(payload)

        return signature.sign()
    }
}