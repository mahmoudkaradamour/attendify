package com.mahmoud.attendify.forensics.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * =============================================================================
 * 🛡 ForensicAuditDao — Low-Level Ledger Access Layer
 * =============================================================================
 *
 * ┌──────────────────────────── ARCHITECTURE POSITION ───────────────────────────┐
 *
 *            ForensicAuditTrailWriter
 *                          │
 *                          ▼
 *            ForensicAuditRepository
 *                          │
 *                          ▼
 *                🟢 ForensicAuditDao
 *                          │
 *                          ▼
 *                   SQLite / Room DB
 *
 * └──────────────────────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE & RESPONSIBILITY
 * -----------------------------------------------------------------------------
 *
 * This interface defines the **direct database access API** for forensic records.
 *
 * Responsibilities:
 * ✅ Insert records (append-only)
 * ✅ Read full ledger (ordered)
 * ✅ Retrieve last hash (chain continuity)
 *
 * It is intentionally:
 * ✅ Minimal
 * ✅ Deterministic
 * ✅ Side-effect free (except writes)
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL (D1)
 * -----------------------------------------------------------------------------
 *
 * ✅ Append-only constraint (enforced via ABORT)
 * ✅ No UPDATE / DELETE operations exposed
 * ✅ Deterministic ordering (index ASC)
 *
 * -----------------------------------------------------------------------------
 * 📊 DATA FLOW
 * -----------------------------------------------------------------------------
 *
 * Writer → Repository → DAO → SQLite
 *
 * insert(record)
 *    ↓
 * database write (immutable)
 *
 * -----------------------------------------------------------------------------
 * ⚖️ FORENSIC GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Every record persisted in order
 * ✅ No overwrite allowed
 * ✅ Full replay possible
 *
 */
@Dao
interface ForensicAuditDao {

    /* =========================================================================
     * ✅ INSERT (APPEND-ONLY)
     * ========================================================================= */

    /**
     * 🔐 Insert a new forensic record
     *
     * CRITICAL:
     * - ABORT ensures no duplicate index overwrite
     * - Guarantees immutability
     *
     * FAILURE CASE:
     * - If index already exists → exception thrown
     *
     * This enforces:
     * ✅ append-only ledger
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        record: ForensicAuditEntity
    )

    /* =========================================================================
     * ✅ RETRIEVE FULL LEDGER
     * ========================================================================= */

    /**
     * 🔐 Retrieve full audit history
     *
     * ORDER:
     * ascending index → replay deterministic
     *
     * USE CASES:
     * ✅ forensic analysis
     * ✅ chain verification
     * ✅ export
     */
    @Query("SELECT * FROM forensic_audit ORDER BY `index` ASC")
    suspend fun getAll(): List<ForensicAuditEntity>

    /* =========================================================================
     * ✅ CHAIN CONTINUITY (LAST HASH)
     * ========================================================================= */

    /**
     * 🔐 Retrieve last record hash
     *
     * Used for:
     * ✅ chain continuation after restart
     * ✅ server anchoring (D3)
     *
     * RETURNS:
     * - ByteArray → last hash
     * - null → no records yet
     */
    @Query("SELECT resultHash FROM forensic_audit ORDER BY `index` DESC LIMIT 1")
    suspend fun getLastHash(): ByteArray?
}