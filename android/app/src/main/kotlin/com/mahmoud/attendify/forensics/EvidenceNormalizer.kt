package com.mahmoud.attendify.forensics

import android.util.Base64
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot
import com.mahmoud.attendify.security.HardwareBackedSnapshotSigner
import com.mahmoud.attendify.security.attestation.AttestationVerifier
import com.mahmoud.attendify.security.attestation.AttestationResult

/**
 * =============================================================================
 * 🛡 EvidenceNormalizer — FINAL (A2 + B1 + B2 + D2)
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE (CRITICAL LAYER)
 * -----------------------------------------------------------------------------
 *
 * This class is the ONLY allowed transformation point between:
 *
 *   Runtime Execution (unsafe / high-risk data)
 *                     ↓
 *   ✅ Forensic Evidence (safe / minimal / permanent)
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * ✅ Removes:
 *   - Raw images
 *   - Biometric embeddings
 *   - Exact sensor streams
 *
 * ✅ Preserves:
 *   - snapshotHash → 🔥 link to physical reality (A2 + D2)
 *   - signature → authenticity proof (B1)
 *   - certificateChain → hardware trust root (B2)
 *
 * -----------------------------------------------------------------------------
 * ⚖️ FORENSIC GUARANTEE
 * -----------------------------------------------------------------------------
 *
 * After normalization:
 *
 * ✅ Evidence is:
 *   - Privacy-safe
 *   - Legally defensible
 *   - Cryptographically verifiable
 *
 * ❗ Any modification → breaks hash chain OR signature
 *
 */
object EvidenceNormalizer {

    /**
     * =========================================================================
     * ✅ NORMALIZE
     * =========================================================================
     *
     * PIPELINE:
     *
     *   SignedSnapshot
     *        ↓
     *   Verify (B1)
     *        ↓
     *   Validate Attestation (B2)
     *        ↓
     *   Reduce Sensitive Data
     *        ↓
     *   Build Minimal Evidence
     *
     */
    fun normalize(
        snapshot: SignedPhysicalRealitySnapshot,
        result: AttendanceResult
    ): NormalizedForensicEvidence {

        /* ===============================================================
         * 🔐 STEP 1 — VERIFY SIGNATURE (B1)
         * ===============================================================
         *
         * Defensive verification:
         * Even if caller forgot verification, we enforce it here.
         */
        val signatureValid =
            HardwareBackedSnapshotSigner.verify(
                snapshot.snapshotHash,
                snapshot.signature,
                snapshot.certificateChain
            )

        if (!signatureValid) {
            throw IllegalStateException(
                "Invalid snapshot signature — normalization aborted"
            )
        }

        /* ===============================================================
         * 🔐 STEP 2 — ATTESTATION VALIDATION (B2)
         * ===============================================================
         *
         * Determines:
         * - StrongBox (highest trust)
         * - TEE (trusted)
         * - Weak / unknown
         */
        val attestationResult =
            AttestationVerifier.verify(snapshot.certificateChain)

        if (attestationResult is AttestationResult.Invalid) {
            throw IllegalStateException(
                "Invalid hardware attestation — rejected"
            )
        }

        val attestationLevel =
            attestationResult::class.simpleName ?: "UNKNOWN"

        /* ===============================================================
         * 🧾 STEP 3 — EXTRACT ADMINISTRATIVE MEANING
         * =============================================================== */
        val (action, decision, decisionReasonBase) =
            when (result) {

                is AttendanceResult.Accepted ->
                    Triple(
                        result.action.name,
                        "ACCEPTED",
                        null
                    )

                is AttendanceResult.Blocked ->
                    Triple(
                        "UNKNOWN",
                        "BLOCKED",
                        result.reason
                    )
            }

        /* ===============================================================
         * ✅ STEP 4 — FORENSIC-AWARE DECISION REASON
         * ===============================================================
         *
         * We append attestation level for audit traceability.
         *
         * Example:
         * BLOCKED|face_mismatch|ATTEST=TrustedTEE
         */
        val decisionReason =
            decisionReasonBase?.let {
                "$it|ATTEST=$attestationLevel"
            } ?: "ATTEST=$attestationLevel"

        /* ===============================================================
         * ✅ STEP 5 — BUILD FINAL FORENSIC EVIDENCE
         * =============================================================== */
        return NormalizedForensicEvidence(

            /* -----------------------------------------------------------
             * 🔗 CORRELATION
             * ----------------------------------------------------------- */
            snapshotId =
                snapshot.snapshotId.toString(),

            /* -----------------------------------------------------------
             * 🕒 TIME
             * ----------------------------------------------------------- */
            timestampMillis =
                snapshot.timestampMillis,

            /* -----------------------------------------------------------
             * 🧾 ADMIN MEANING
             * ----------------------------------------------------------- */
            action =
                action,

            decision =
                decision,

            decisionReason =
                decisionReason,

            /* ============================================================
             * 🔥 D2 — CRYPTOGRAPHIC BINDING TO REALITY
             * ============================================================
             *
             * This is the MOST IMPORTANT FIELD:
             *
             * snapshotHash links:
             *
             * Ledger ↔ Evidence ↔ Physical Reality
             *
             * Without it → system is legally weak
             */
            snapshotHash =
                snapshot.snapshotHash,

            /* -----------------------------------------------------------
             * 🔏 SIGNATURE (AUTHENTICITY)
             * ----------------------------------------------------------- */
            snapshotSignature =
                Base64.encodeToString(
                    snapshot.signature,
                    Base64.NO_WRAP
                ),

            /* -----------------------------------------------------------
             * 🔗 CERTIFICATE CHAIN (TRUST ROOT)
             * ----------------------------------------------------------- */
            certificateChain =
                snapshot.certificateChain.map {
                    Base64.encodeToString(it, Base64.NO_WRAP)
                }
        )
    }
}
