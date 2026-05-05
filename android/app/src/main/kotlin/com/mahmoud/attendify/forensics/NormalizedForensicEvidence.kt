package com.mahmoud.attendify.forensics

/**
 * ============================================================================
 * NormalizedForensicEvidence
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * Privacy‑safe, audit‑grade representation of an attendance event.
 *
 * This is the ONLY form of evidence allowed for:
 *  - Long‑term storage
 *  - Audit review
 *  - Legal / forensic analysis
 *
 * ============================================================================
 * DESIGN PRINCIPLES
 * ============================================================================
 *
 * ✅ Data minimization (Phase 3.5)
 * ✅ Hardware‑backed attestation (Phase 3.6)
 * ✅ No biometric data
 * ✅ No raw images
 * ✅ No precise location data
 *
 * ============================================================================
 * SECURITY / LEGAL NOTES
 * ============================================================================
 *
 * - snapshotSignature proves integrity
 * - certificateChain proves key origin (TEE / StrongBox)
 * - snapshotId binds all logs together
 */
data class NormalizedForensicEvidence(

    /* ------------------------------------------------------------
     * CORRELATION
     * ---------------------------------------------------------- */
    val snapshotId: String,

    /* ------------------------------------------------------------
     * TIME (NORMALIZED)
     * ---------------------------------------------------------- */
    val timestampMillis: Long,

    /* ------------------------------------------------------------
     * ADMINISTRATIVE MEANING
     * ---------------------------------------------------------- */
    val action: String,
    val decision: String,
    val decisionReason: String?,

    /* ------------------------------------------------------------
     * HARDWARE‑BACKED ATTESTATION (PHASE 3.6)
     * ---------------------------------------------------------- */
    val snapshotSignature: String,
    val certificateChain: List<String>
)