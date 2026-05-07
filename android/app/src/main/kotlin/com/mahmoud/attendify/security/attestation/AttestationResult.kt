package com.mahmoud.attendify.security.attestation

/**
 * =============================================================================
 * 🛡 AttestationResult — Trust Classification
 * =============================================================================
 */
sealed class AttestationResult {

    /**
     * ✅ Strongest possible guarantee
     */
    object StrongBox : AttestationResult()

    /**
     * ✅ Trusted but weaker than StrongBox
     */
    object TrustedTEE : AttestationResult()

    /**
     * ⚠️ Weak / fallback / unknown
     */
    data class Weak(val reason: String) : AttestationResult()

    /**
     * ❌ Completely invalid
     */
    data class Invalid(val reason: String) : AttestationResult()
}
