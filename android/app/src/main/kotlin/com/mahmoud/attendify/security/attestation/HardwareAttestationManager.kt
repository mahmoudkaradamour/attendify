package com.mahmoud.attendify.security.attestation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyInfo
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyFactory

/**
 * =============================================================================
 * 🧠 HardwareAttestationManager — Hardware Trust Evaluation Component
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Provides a deterministic evaluation of the **origin and protection level**
 * of cryptographic keys stored in Android Keystore.
 *
 * -----------------------------------------------------------------------------
 * 📊 PROCESS FLOW
 * -----------------------------------------------------------------------------
 *
 *   Key Initialization
 *         ↓
 *   Retrieve KeyInfo
 *         ↓
 *   Inspect Hardware Flags
 *         ↓
 *   Classify Security Level
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * Trust is inferred from:
 *
 *   - Hardware isolation (TEE / StrongBox)
 *   - Keystore enforcement boundary
 *
 * -----------------------------------------------------------------------------
 * ⚠️ DESIGN NOTE
 * -----------------------------------------------------------------------------
 *
 * Android does not provide a fully portable API for StrongBox detection,
 * therefore classification uses safe inference.
 *
 */
class HardwareAttestationManager {

    private val keyAlias = "attendify_secure_key"

    /**
     * =============================================================================
     * 🔑 ensureKeyExists
     * =============================================================================
     *
     * Creates a persistent signing key inside Android Keystore if missing.
     *
     * Properties:
     *   ✅ Non-exportable
     *   ✅ System-managed lifecycle
     *   ✅ Potentially hardware-backed
     */
    fun ensureKeyExists() {

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        if (keyStore.containsAlias(keyAlias)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /**
     * =============================================================================
     * 🔍 getKeySecurityLevel
     * =============================================================================
     *
     * Evaluates protection level of the key.
     *
     * -----------------------------------------------------------------------------
     * 📐 CLASSIFICATION STRATEGY
     * -----------------------------------------------------------------------------
     *
     * Uses hardware boundary indicator:
     *
     *   isInsideSecureHardware → indicates TEE or StrongBox
     *
     * Since StrongBox is not reliably distinguishable at runtime across all devices,
     * we treat hardware-backed keys conservatively.
     *
     * -----------------------------------------------------------------------------
     * 📊 OUTPUT LEVELS
     * -----------------------------------------------------------------------------
     *
     * HARDWARE → trusted execution (TEE / StrongBox)
     * SOFTWARE → no secure isolation
     *
     */
    fun getKeySecurityLevel(): SecurityLevel {

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        val privateKey =
            keyStore.getKey(keyAlias, null)

        val factory = KeyFactory.getInstance(
            privateKey.algorithm,
            "AndroidKeyStore"
        )

        val keyInfo = factory.getKeySpec(
            privateKey,
            KeyInfo::class.java
        )

        /**
         * NOTE:
         * - isInsideSecureHardware is deprecated but still the only reliable signal
         *   across API levels for hardware isolation.
         */
        @Suppress("DEPRECATION")
        return if (keyInfo.isInsideSecureHardware) {
            SecurityLevel.HARDWARE
        } else {
            SecurityLevel.SOFTWARE
        }
    }
}

/**
 * =============================================================================
 * 🔐 SecurityLevel — Simplified Trust Model
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 SEMANTIC MODEL
 * -----------------------------------------------------------------------------
 *
 * HARDWARE:
 *   Key protected inside secure hardware boundary (TEE or StrongBox)
 *
 * SOFTWARE:
 *   Key managed in software environment (lower trust)
 *
 * -----------------------------------------------------------------------------
 * 📊 HIERARCHY
 * -----------------------------------------------------------------------------
 *
 * HARDWARE > SOFTWARE
 *
 */
enum class SecurityLevel {
    HARDWARE,
    SOFTWARE
}