package com.mahmoud.attendify.forensics

import android.util.Base64
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot

/**
 * ============================================================================
 * EvidenceNormalizer
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * EvidenceNormalizer is responsible for transforming execution‑time artifacts
 * (which may contain sensitive, raw, or high‑risk data) into a **minimal,
 * privacy‑safe, and legally defensible forensic representation**.
 *
 * This class is a core component of:
 *   - Phase 3.5 — Evidence Normalization & Redaction
 *   - Phase 3.6 — Hardware‑Backed Attestation Consumption
 *
 * ============================================================================
 * WHY THIS CLASS EXISTS
 * ============================================================================
 *
 * Raw execution artifacts (images, locations, biometric signals, timestamps)
 * are:
 *   ❌ Dangerous to store long‑term
 *   ❌ High risk from a privacy perspective
 *   ❌ Often unnecessary for legal or audit purposes
 *
 * Therefore:
 *   ➤ We **normalize** evidence
 *   ➤ We **redact** sensitive details
 *   ➤ We **retain only what is strictly necessary** to prove the event
 *
 * This follows well‑established principles from:
 *   - Digital forensics
 *   - Privacy‑by‑Design (GDPR‑style data minimization)
 *   - Audit‑grade logging systems
 *
 * ============================================================================
 * WHAT THIS CLASS GUARANTEES
 * ============================================================================
 *
 * ✅ No biometric data is persisted
 * ✅ No raw images are persisted
 * ✅ No precise location data is persisted
 * ✅ No sensor‑level data is persisted
 *
 * ✅ Cryptographic attestation is preserved
 * ✅ Administrative meaning is preserved
 * ✅ Chain‑of‑custody remains intact
 *
 * ============================================================================
 * WHAT THIS CLASS DELIBERATELY DOES NOT DO
 * ============================================================================
 *
 * ❌ It does NOT perform cryptographic signing
 * ❌ It does NOT validate signatures
 * ❌ It does NOT make attendance decisions
 * ❌ It does NOT persist data
 *
 * Its role is **purely transformational and deterministic**.
 *
 * ============================================================================
 * TRUST MODEL
 * ============================================================================
 *
 * This class assumes that:
 *   - SignedPhysicalRealitySnapshot is already trusted & attested
 *   - AttendanceResult is already final and authoritative
 *
 * EvidenceNormalizer does NOT question these inputs.
 */
object EvidenceNormalizer {

    /**
     * =========================================================================
     * normalize
     * =========================================================================
     *
     * Transforms a signed snapshot and its final attendance result into
     * NormalizedForensicEvidence — the ONLY form of evidence permitted for
     * long‑term forensic storage.
     *
     * ------------------------------------------------------------------------
     * INPUTS:
     * ------------------------------------------------------------------------
     * snapshot :
     *   - Contains hardware‑backed signature
     *   - Certificate chain proves key origin (TEE / StrongBox)
     *
     * result :
     *   - Final administrative decision (ACCEPTED or BLOCKED)
     *   - Produced exclusively by AttendanceUseCase
     *
     * ------------------------------------------------------------------------
     * OUTPUT:
     * ------------------------------------------------------------------------
     * NormalizedForensicEvidence:
     *   - Minimal
     *   - Privacy‑safe
     *   - Audit‑grade
     *   - Legally defensible
     */
    fun normalize(
        snapshot: SignedPhysicalRealitySnapshot,
        result: AttendanceResult
    ): NormalizedForensicEvidence {

        /* ===============================================================
         * EXTRACT POLICY‑LEVEL MEANING
         * ===============================================================
         *
         * IMPORTANT DESIGN DECISION:
         * ----------------------------------------------------------------
         * • We store the ACTION only for ACCEPTED cases.
         * • For BLOCKED cases, action is considered non‑authoritative.
         *
         * RATIONALE:
         * - A successful attendance must be attributable.
         * - A blocked attempt only requires a reason for rejection.
         * - This avoids leaking partial or misleading intent data.
         */
        val (action, decision, decisionReason) =
            when (result) {

                is AttendanceResult.Accepted ->
                    Triple(
                        result.action.name, // authoritative
                        "ACCEPTED",
                        null                // ✅ no justification persisted
                    )

                is AttendanceResult.Blocked ->
                    Triple(
                        "UNKNOWN",          // non‑authoritative on failure
                        "BLOCKED",
                        result.reason       // ✅ essential forensic reason
                    )
            }

        /* ===============================================================
         * BUILD NORMALIZED FORENSIC EVIDENCE
         * ===============================================================
         *
         * Every field below has passed the following test:
         *
         *   "If this field leaks, would it endanger privacy,
         *    security, or create unnecessary legal exposure?"
         *
         * Only fields with a justified forensic purpose survive.
         */
        return NormalizedForensicEvidence(

            /* -----------------------------------------------------------
             * CORRELATION
             * -----------------------------------------------------------
             * Used to correlate this record with:
             * - Replay protection logs
             * - Snapshot lifecycle
             */
            snapshotId =
                snapshot.snapshotId.toString(),

            /* -----------------------------------------------------------
             * TIME (NORMALIZED)
             * -----------------------------------------------------------
             * Timestamp reflects WHEN the snapshot was sealed,
             * not raw sensor time or external clocks.
             */
            timestampMillis =
                snapshot.timestampMillis,

            /* -----------------------------------------------------------
             * ADMINISTRATIVE MEANING
             * ----------------------------------------------------------- */
            action =
                action,

            decision =
                decision,

            decisionReason =
                decisionReason,

            /* -----------------------------------------------------------
             * HARDWARE‑BACKED CRYPTOGRAPHIC ATTESTATION
             * -----------------------------------------------------------
             * These fields allow a third party to verify that:
             *   - The snapshot was signed
             *   - The signing key is hardware‑backed
             *   - The key cannot be extracted or cloned
             *
             * This is stronger than keeping a raw hash.
             */
            snapshotSignature =
                Base64.encodeToString(
                    snapshot.signature,
                    Base64.NO_WRAP
                ),

            certificateChain =
                snapshot.certificateChain.map {
                    Base64.encodeToString(it, Base64.NO_WRAP)
                }
        )
    }
}