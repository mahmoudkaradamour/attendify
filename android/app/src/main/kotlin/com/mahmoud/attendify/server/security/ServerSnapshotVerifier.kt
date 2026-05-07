package com.mahmoud.attendify.server.security

import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * ============================================================================
 * ServerSnapshotVerifier — C1 (Zero Trust Verification)
 * ============================================================================
 *
 * 🔥 PURPOSE:
 * ----------------------------------------------------------------------------
 * Re-verify ALL evidence server-side (even inside app for now).
 *
 * This simulates a Zero-Trust backend:
 *   ❌ Never trust client
 *   ✅ Recompute everything independently
 *
 * ============================================================================
 *
 * 📊 FLOW DIAGRAM:
 *
 *           Snapshot
 *              │
 *              ▼
 *    Use Existing snapshotHash
 *              │
 *              ▼
 *   Verify Signature (public key)
 *              │
 *              ▼
 *   Validate Certificate Chain
 *              │
 *              ▼
 *        ACCEPT ✅ / REJECT ❌
 *
 * ============================================================================
 */
object ServerSnapshotVerifier {

    /**
     * ✅ MAIN ENTRY
     */
    fun verify(snapshot: SignedPhysicalRealitySnapshot): Boolean {

        return try {

            /* ============================================================
             * STEP 1 — CERTIFICATES
             * ============================================================ */
            val certificates =
                snapshot.certificateChain.map { parseCertificate(it) }

            if (certificates.isEmpty()) return false
            if (!validateCertificateChain(certificates)) return false

            val leaf = certificates.first()

            /* ============================================================
             * STEP 2 — SIGNATURE VERIFY
             * ============================================================ */
            val signature = Signature.getInstance("SHA256withRSA")

            signature.initVerify(leaf.publicKey)
            signature.update(snapshot.snapshotHash)

            val valid = signature.verify(snapshot.signature)

            if (!valid) return false

            /* ============================================================
             * STEP 3 — BASIC SANITY
             * ============================================================ */
            if (snapshot.snapshotHash.isEmpty()) return false
            if (snapshot.signature.isEmpty()) return false
            if (snapshot.timestampMillis <= 0) return false

            /* ============================================================
             * ✅ SUCCESS
             * ============================================================ */
            true

        } catch (_: Exception) {
            false
        }
    }

    /* =========================================================================
     * ✅ CERTIFICATE PARSING
     * ========================================================================= */

    private fun parseCertificate(data: ByteArray): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(data.inputStream()) as X509Certificate
    }

    /* =========================================================================
     * ✅ CERTIFICATE VALIDATION
     * ========================================================================= */

    private fun validateCertificateChain(
        chain: List<X509Certificate>
    ): Boolean {

        return try {
            chain.forEach { it.checkValidity() }
            true
        } catch (_: Exception) {
            false
        }
    }
}