package com.mahmoud.attendify.system.location

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * LocationSigner
 *
 * Cryptographically signs LocationEvidence using Android Keystore.
 */
object LocationSigner {

    private const val KEY_ALIAS = "location_evidence_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun sign(data: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(getPrivateKey())
        signature.update(data.toByteArray())
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun getPrivateKey(): PrivateKey {
        val keystore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keystore.getEntry(KEY_ALIAS, null)

        return if (entry is KeyStore.PrivateKeyEntry) {
            entry.privateKey
        } else {
            generateKeyPair().private
        }
    }

    private fun generateKeyPair() =
        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        ).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKeyPair()
}