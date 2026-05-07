package com.mahmoud.attendify.forensics

/**
 * =============================================================================
 * 🛡 NormalizedForensicEvidence — Forensic-Safe Evidence Model (FINAL)
 * =============================================================================
 *
 * ┌──────────────────────────── DATA FLOW ─────────────────────────────┐
 *
 *   PhysicalRealityBuilder (C4)
 *              │
 *              ▼
 *   SignedPhysicalRealitySnapshot
 *              │
 *              ▼
 *   EvidenceNormalizer
 *              │
 *              ▼
 *   ✅ NormalizedForensicEvidence (THIS CLASS)
 *              │
 *              ▼
 *   ForensicAuditTrailWriter (D1 + D2)
 *
 * └────────────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE
 * -----------------------------------------------------------------------------
 *
 * This class represents the **ONLY allowed evidence format** after normalization.
 *
 * It is designed to:
 *
 * ✅ Remove sensitive / biometric data
 * ✅ Preserve cryptographic integrity
 * ✅ Remain audit-traceable
 * ✅ Be legally defensible
 *
 * -----------------------------------------------------------------------------
 * 🔐 DESIGN PRINCIPLES
 * -----------------------------------------------------------------------------
 *
 * ✅ Data Minimization:
 *   - No raw image
 *   - No face embeddings
 *   - No precise location coordinates
 *
 * ✅ Integrity Preservation:
 *   - snapshotHash binds evidence → physical reality
 *   - snapshotSignature ensures tamper resistance
 *
 * ✅ Hardware Trust:
 *   - certificateChain proves key origin (TEE / StrongBox)
 *
 * -----------------------------------------------------------------------------
 * ⚖️ FORENSIC GUARANTEE
 * -----------------------------------------------------------------------------
 *
 * This structure ensures:
 *
 * ✅ Evidence cannot be modified unnoticed
 * ✅ Evidence can be verified independently
 * ✅ Evidence can be presented in audit/legal contexts
 *
 * -----------------------------------------------------------------------------
 * 📊 FIELD CLASSIFICATION
 * -----------------------------------------------------------------------------
 *
 * ┌──────────────────────┬────────────────────────────────────────────┐
 * │ Category             │ Fields                                     │
 * ├──────────────────────┼────────────────────────────────────────────┤
 * │ Correlation          │ snapshotId                                 │
 * │ Time                 │ timestampMillis                            │
 * │ Decision             │ action / decision / reason                 │
 * │ Evidence Integrity   │ snapshotHash                               │
 * │ Cryptographic Proof  │ snapshotSignature                          │
 * │ Device Trust         │ certificateChain                           │
 * └──────────────────────┴────────────────────────────────────────────┘
 *
 */
data class NormalizedForensicEvidence(

    /* =========================================================================
     * 🔗 CORRELATION
     * ========================================================================= */

    /**
     * Unique identifier of the snapshot
     *
     * Used for:
     * ✅ replay protection
     * ✅ audit tracing
     * ✅ system correlation
     */
    val snapshotId: String,

    /* =========================================================================
     * 🕒 TIME (NORMALIZED)
     * ========================================================================= */

    /**
     * Trusted timestamp (already validated upstream)
     *
     * Includes:
     * - device time
     * - optionally server correction (future)
     */
    val timestampMillis: Long,

    /* =========================================================================
     * 🧾 ADMINISTRATIVE MEANING
     * ========================================================================= */

    /**
     * High-level action
     * Example: CHECK_IN / CHECK_OUT
     */
    val action: String,

    /**
     * Final system decision
     * Example: ALLOWED / REJECTED
     */
    val decision: String,

    /**
     * Optional explanation for decision
     */
    val decisionReason: String?,

    /* =========================================================================
     * 🔐 CRYPTOGRAPHIC EVIDENCE BINDING (D2)
     * ========================================================================= */

    /**
     * ✅ CRITICAL FIELD
     *
     * Hash of the canonical payload (image + time + location)
     *
     * Guarantees:
     * ✅ direct link to physical reality
     * ✅ tamper detection
     * ✅ forensic traceability
     */
    val snapshotHash: ByteArray,

    /* =========================================================================
     * 🔏 SIGNATURE (AUTHENTICITY)
     * ========================================================================= */

    /**
     * Hardware-backed signature
     *
     * Ensures:
     * ✅ snapshot integrity
     * ✅ proof of origin (device-bound key)
     *
     * FORMAT:
     * Base64 encoded signature
     */
    val snapshotSignature: String,

    /* =========================================================================
     * 🔗 CERTIFICATE CHAIN (ATTESTATION)
     * ========================================================================= */

    /**
     * Chain of trust for the key used in signing
     *
     * Contains:
     * ✅ device hardware root
     * ✅ intermediate certs
     *
     * Used for:
     * ✅ verifying TEE / StrongBox
     * ✅ future server validation (D3)
     */
    val certificateChain: List<String>
)
