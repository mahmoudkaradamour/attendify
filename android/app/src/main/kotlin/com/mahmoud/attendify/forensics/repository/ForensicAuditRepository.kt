package com.mahmoud.attendify.forensics.repository

import com.mahmoud.attendify.forensics.db.ForensicAuditDao
import com.mahmoud.attendify.forensics.db.ForensicAuditEntity

/**
 * =============================================================================
 * 🛡 ForensicAuditRepository — Persistent Audit Layer (D1 Implementation)
 * =============================================================================
 *
 * ┌──────────────────────────── ARCHITECTURE POSITION ─────────────────────────────┐
 *
 *                 AttendanceRuntimeOrchestrator
 *                               │
 *                               ▼
 *                ForensicAuditTrailWriter
 *                               │
 *                               ▼
 *                     ForensicAuditRepository
 *                               │
 *                               ▼
 *                        Room Database (Encrypted)
 *
 * └───────────────────────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE & RESPONSIBILITY
 * -----------------------------------------------------------------------------
 *
 * This class is the **single persistence gateway** for forensic audit data.
 *
 * Responsibilities:
 * ✅ Persist audit records (append-only)
 * ✅ Retrieve audit state (last hash)
 * ✅ Provide replayable audit history
 *
 * Explicit NON-responsibilities:
 * ❌ No hashing logic
 * ❌ No business decision logic
 * ❌ No normalization
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL (D1)
 * -----------------------------------------------------------------------------
 *
 * ✅ Durable storage (Room database)
 * ✅ Immediate persistence (write-through)
 * ✅ Immutable append-only behavior (enforced at DAO level)
 *
 * -----------------------------------------------------------------------------
 * 📊 DATA FLOW MODEL
 * -----------------------------------------------------------------------------
 *
 * Writer → Repository → DAO → Database
 *   │         │         │         │
 *   ▼         ▼         ▼         ▼
 * record → entity → insert → disk
 *
 * -----------------------------------------------------------------------------
 * ⚖️ FORENSIC GUARANTEE
 * -----------------------------------------------------------------------------
 *
 * Once a record is written:
 * ✅ It persists across restarts
 * ✅ It participates in chain validation
 * ✅ It can be replayed for audit verification
 *
 */
class ForensicAuditRepository(

    private val dao: ForensicAuditDao

) {

    /* =========================================================================
     * ✅ D1 — APPEND RECORD (PRIMARY RESPONSIBILITY)
     * ========================================================================= */

    /**
     * 🔐 Persist a new forensic audit record
     *
     * CRITICAL BEHAVIOR:
     * - Append-only
     * - Immediate (write-through)
     * - No overwrite allowed
     *
     * SCIENTIFIC ROLE:
     * Converts in-memory evidence → durable forensic artifact
     *
     * FLOW:
     * Writer → Repository → DAO.insert → SQLite → Disk
     *
     * SECURITY:
     * - OnConflictStrategy.ABORT prevents tampering
     */
    suspend fun append(
        entity: ForensicAuditEntity
    ) {
        dao.insert(entity)
    }

    /* =========================================================================
     * ✅ RETRIEVE LAST HASH (CHAIN CONTINUITY)
     * ========================================================================= */

    /**
     * 🔐 Fetch last record hash
     *
     * Used for:
     * ✅ Chain initialization
     * ✅ Recovery after restart
     * ✅ Future D3 (server anchoring)
     *
     * RETURNS:
     * - ByteArray → last hash
     * - null → no records (genesis state)
     */
    suspend fun getLastHash(): ByteArray? {
        return dao.getLastHash()
    }

    /* =========================================================================
     * ✅ FULL HISTORY (FORENSIC REPLAY)
     * ========================================================================= */

    /**
     * 🔐 Retrieve entire audit trail
     *
     * Used for:
     * ✅ forensic reconstruction
     * ✅ chain verification
     * ✅ audit export
     *
     * ORDER:
     * ascending index → deterministic replay
     */
    suspend fun getAll(): List<ForensicAuditEntity> {
        return dao.getAll()
    }

    /* =========================================================================
     * ✅ CHAIN VALIDATION (CRITICAL FORENSIC TOOL)
     * ========================================================================= */

    /**
     * 🔐 Verify integrity of entire ledger
     *
     * SCIENTIFIC MODEL:
     *
     * record[n].previousHash == hash(record[n-1])
     *
     * If any mismatch:
     * → ledger is compromised
     *
     * RETURNS:
     * true  → valid chain
     * false → tampered
     */
    suspend fun verifyChain(): Boolean {

        val records = dao.getAll()

        if (records.isEmpty()) return true

        var previousHash = ByteArray(32)

        for (record in records) {

            val computedHash = com.mahmoud.attendify.forensics.ForensicAuditHasher.compute(
                com.mahmoud.attendify.forensics.ForensicAuditRecord(
                    index = record.index,
                    timestampMillis = record.timestampMillis,
                    snapshotId = record.snapshotId,
                    decision = record.decision,
                    snapshotHash = record.snapshotHash,
                    resultHash = ByteArray(0),
                    previousHash = record.previousHash
                )
            )

            // تحقق من consistency
            if (!record.previousHash.contentEquals(previousHash)) {
                return false
            }

            if (!record.resultHash.contentEquals(computedHash)) {
                return false
            }

            previousHash = record.resultHash
        }

        return true
    }
}