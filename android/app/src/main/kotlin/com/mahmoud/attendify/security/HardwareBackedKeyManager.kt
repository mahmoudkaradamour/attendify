package com.mahmoud.attendify.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * HardwareBackedKeyManager
 *
 * Manages Android Keystore keys backed by TEE / StrongBox.
 */
object HardwareBackedKeyManager {

    private const val KEY_ALIAS = "ATTENDIFY_HARDWARE_ATTESTATION_KEY"

    private val keyStore: KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun getOrCreateKey(): PrivateKey {

        if (!keyStore.containsAlias(KEY_ALIAS)) {

            val generator =
                KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    "AndroidKeyStore"
                )

            val spec =
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN
                )
                    .setAlgorithmParameterSpec(
                        java.security.spec.ECGenParameterSpec("secp256r1")
                    )
                    .setDigests(
                        KeyProperties.DIGEST_SHA256
                    )
                    .setUserAuthenticationRequired(false)
                    .setIsStrongBoxBacked(true) // ✅ if available
                    .build()

            generator.initialize(spec)
            generator.generateKeyPair()
        }

        return keyStore.getKey(KEY_ALIAS, null) as PrivateKey
    }

    fun getCertificateChain(): List<Certificate> =
        keyStore.getCertificateChain(KEY_ALIAS).toList()
}