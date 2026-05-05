package com.mahmoud.attendify.forensics

import java.util.concurrent.atomic.AtomicLong

/**
 * ============================================================================
 * ForensicAuditTrailWriter
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * This class is responsible for maintaining the system's
 * **persistent forensic audit trail**.
 *
 * It represents the LAST step in the attendance pipeline,
 * executed ONLY AFTER a final AttendanceResult is produced.
 *
 * ============================================================================
 * CORE PRINCIPLES
 * ============================================================================
 *
 * 1. APPEND‑ONLY
 *    ------------------------------------------------------------------------
 *    - Records can ONLY be added.
 *    - No record can be modified or deleted.
 *
 * 2. ORDERED
 *    ------------------------------------------------------------------------
 *    - Every record has a strictly increasing index.
 *    - Preserves chronological integrity.
 *
 * 3. TAMPER‑EVIDENT
 *    ------------------------------------------------------------------------
 *    - Each record is cryptographically linked (hash‑chained)
 *      to the previous record.
 *    - Any deletion or modification breaks the chain.
 *
 * 4. PRIVACY‑SAFE (POST PHASE 3.5)
 *    ------------------------------------------------------------------------
 *    - Only NormalizedForensicEvidence is persisted.
 *    - No biometric data.
 *    - No raw images.
 *    - No precise location data.
 *
 * ============================================================================
 * WHAT THIS CLASS DOES NOT DO
 * ============================================================================
 *
 * ❌ Does not make attendance decisions
 * ❌ Does not handle biometric processing
 * ❌ Does not normalize evidence
 * ❌ Does not expose stored records
 *
 * Its responsibility is purely **forensic persistence**.
 *
 * ============================================================================
 * HIGH‑LEVEL LEDGER MODEL
 * ============================================================================
 *
 *  NormalizedForensicEvidence
 *              │
 *              ▼
 *     ForensicAuditRecord
 *              │
 *              ▼
 *   Hash‑Chained Append‑Only Ledger
 *
 * ============================================================================
 * LEGAL / AUDIT JUSTIFICATION
 * ============================================================================
 *
 * This implementation aligns with:
 *  - Chain of Custody principles
 *  - Audit‑grade logging practices
 *  - Privacy‑by‑Design (GDPR‑style minimization)
 *
 * It intentionally resembles blockchain‑style ledgers
 * (WITHOUT networking or consensus complexity).
 */
class ForensicAuditTrailWriter {

    /**
     * Monotonic index counter.
     *
     * SCIENTIFIC JUSTIFICATION:
     * ------------------------------------------------------------------------
     * - Guarantees strict ordering of events
     * - Prevents insertion between records
     * - Simplifies forensic timeline reconstruction
     */
    private val indexCounter = AtomicLong(0)

    /**
     * Hash of the previous audit record.
     *
     * GENESIS STATE:
     * ------------------------------------------------------------------------
     * - Initialized to a zero‑filled byte array
     * - Acts as the "genesis block" anchor
     */
    private var lastHash: ByteArray = ByteArray(32)

    /**
     * =========================================================================
     * append
     * =========================================================================
     *
     * Appends a NEW forensic audit record to the ledger.
     *
     * INPUT:
     * ------------------------------------------------------------------------
     * evidence → Normalized, privacy‑safe forensic evidence
     *
     * GUARANTEES:
     * ------------------------------------------------------------------------
     * ✅ No mutation of input data
     * ✅ One‑way append
     * ✅ Hash‑chained integrity
     *
     * NOTE ON PERSISTENCE:
     * ------------------------------------------------------------------------
     * This implementation is storage‑agnostic.
     * The record can later be persisted to:
     *  - Encrypted file
     *  - Room database
     *  - Secure hardware‑backed storage
     *
     * WITHOUT changing this API.
     */
    fun append(
        evidence: NormalizedForensicEvidence
    ): ForensicAuditRecord {

        /* ====================================================================
         * BUILD NEW AUDIT RECORD (WITHOUT HASH)
         * ==================================================================== */
        val record =
            ForensicAuditRecord(
                index =
                    indexCounter.incrementAndGet(),

                timestampMillis =
                    evidence.timestampMillis,

                snapshotId =
                    evidence.snapshotId,

                decision =
                    evidence.decision,

                resultHash =
                    ByteArray(0), // placeholder (computed next)

                previousHash =
                    lastHash
            )

        /* ====================================================================
         * COMPUTE HASH (CHAINING STEP)
         * ====================================================================
         *
         * The hash covers:
         *  - record metadata
         *  - decision outcome
         *  - previous record hash
         *
         * Any modification to ANY field breaks the chain.
         */
        val hash =
            ForensicAuditHasher.compute(record)

        val finalRecord =
            record.copy(resultHash = hash)

        /* ====================================================================
         * UPDATE CHAIN STATE
         * ==================================================================== */
        lastHash = hash

        /* ====================================================================
         * PERSISTENCE HOOK
         * ====================================================================
         *
         * 🔒 IMPORTANT:
         * Actual storage implementation is intentionally omitted here.
         *
         * This design:
         *  - Keeps forensic logic independent of storage technology
         *  - Allows future migration without breaking the chain
         */
//      persist(finalRecord)

        return finalRecord
    }
}