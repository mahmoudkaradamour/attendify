package com.mahmoud.attendify.system.time

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

/**
 * TimeProofSigner
 *
 * Signs attendance time proofs using Android Keystore.
 */
object TimeProofSigner {

    private const val KEY_ALIAS = "attendance_time_proof_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Signs deterministic payload using Keystore-backed private key.
     */
    fun sign(data: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(getPrivateKey())
        signature.update(data.toByteArray())
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    /**
     * Retrieves the private key from Keystore,
     * or generates a new KeyPair if missing.
     */
    private fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        val entry = keyStore.getEntry(KEY_ALIAS, null)

        return when (entry) {
            is KeyStore.PrivateKeyEntry -> entry.privateKey
            else -> generateKeyPair().private
        }
    }

    /**
     * Generates a new RSA KeyPair in Android Keystore.
     */
    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        generator.initialize(spec)
        return generator.generateKeyPair()
    }
}
