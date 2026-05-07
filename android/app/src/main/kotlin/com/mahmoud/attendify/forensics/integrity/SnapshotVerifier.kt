package com.mahmoud.attendify.forensics.integrity

import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot
import com.mahmoud.attendify.security.HardwareBackedSnapshotSigner
import com.mahmoud.attendify.security.attestation.AttestationVerifier
import com.mahmoud.attendify.security.attestation.AttestationResult

/**
 * =============================================================================
 * 🛡 SnapshotVerifier — FULL VALIDATION (B1 + B2)
 * =============================================================================
 *
 * Verifies:
 * ✅ Signature integrity (B1)
 * ✅ Hardware attestation (B2)
 *
 */
object SnapshotVerifier {

    fun verify(
        snapshot: SignedPhysicalRealitySnapshot
    ): Boolean {

        /* ================================================================
         * ✅ STEP 1 — VERIFY SIGNATURE (B1)
         * ================================================================ */
        val signatureValid =
            HardwareBackedSnapshotSigner.verify(
                snapshot.snapshotHash,
                snapshot.signature,
                snapshot.certificateChain
            )

        if (!signatureValid) {
            return false
        }

        /* ================================================================
         * ✅ STEP 2 — VERIFY ATTESTATION (B2)
         * ================================================================ */
        val attestationResult =
            AttestationVerifier.verify(snapshot.certificateChain)

        return when (attestationResult) {

            is AttestationResult.Invalid -> {
                false // ❌ reject immediately
            }

            is AttestationResult.Weak -> {

                true
            }

            is AttestationResult.TrustedTEE -> {
                true
            }

            is AttestationResult.StrongBox -> {
                true
            }
        }
    }
}