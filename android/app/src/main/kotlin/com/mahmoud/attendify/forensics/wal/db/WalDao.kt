package com.mahmoud.attendify.forensics.wal.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * =============================================================================
 * 🧠 WalDao — Forensic Write-Ahead Log Data Access Interface
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   W = Write-Ahead Log (persistent store)
 *   R = WalEntity (single record)
 *
 * Then:
 *
 *   WalDao defines:
 *
 *     CRUD(R) operations over W
 *
 * with transactional guarantees.
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Provide a controlled, consistent, and atomic interface
 * for interacting with the WAL storage layer.
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * DAO abstracts:
 *
 *   Physical Storage (SQLite via Room)
 *   ↓
 *   Logical Operations (Begin / Commit / Recover)
 *
 * -----------------------------------------------------------------------------
 * 📊 SYSTEM POSITION
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────┐
 *   │ PhysicalRealityBuilder       │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ WalManager (Orchestrator)    │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ WalDao (THIS CLASS)          │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ SQLite / Room Storage         │
 *   └──────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY CHARACTERISTICS
 * -----------------------------------------------------------------------------
 *
 * ✅ Atomic write operations
 * ✅ Deterministic query behavior
 * ✅ Forensic trace preservation
 * ✅ No implicit mutation (explicit operations only)
 *
 * -----------------------------------------------------------------------------
 * 📊 RECORD STATE MODEL
 * -----------------------------------------------------------------------------
 *
 * Each WalEntity has a lifecycle:
 *
 *   INITIATED → COMMITTED
 *
 * Queries rely on this invariant:
 *
 *   state != COMMITTED → unsafe / incomplete
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL CONSTRAINTS
 * -----------------------------------------------------------------------------
 *
 * 1. ALL inserts must occur BEFORE execution begins
 * 2. Updates are ONLY allowed for state transitions
 * 3. Deletions must be controlled (TTL-based)
 * 4. Queries must NEVER infer state from absence
 *
 * -----------------------------------------------------------------------------
 * 🧩 QUERY FLOW
 * -----------------------------------------------------------------------------
 *
 * BEGIN:
 *   insert(record)
 *
 * COMMIT:
 *   update(record.state = COMMITTED)
 *
 * RECOVERY:
 *   getUncommitted()
 *
 * CLEANUP:
 *   deleteOlderThan(threshold)
 *
 * =============================================================================
 */
@Dao
interface WalDao {

    /* =========================================================================
     * 🟢 INSERT — BEGIN TRANSACTION
     * ========================================================================= */

    /**
     * -------------------------------------------------------------------------
     * INSERT (BEGIN)
     * -------------------------------------------------------------------------
     *
     * Adds a new WAL record with state:
     *   → INITIATED
     *
     * -----------------------------------------------------------------------------
     * BEHAVIOR:
     *
     *   If record with same ID exists:
     *     → replace it (idempotent safety)
     *
     * -----------------------------------------------------------------------------
     * WHY REPLACE STRATEGY?
     *
     *   - Prevent duplicate BEGIN operations
     *   - Ensure idempotent behavior in retry scenarios
     *
     * -----------------------------------------------------------------------------
     * FLOW:
     *
     *   BEGIN(T) →
     *     insert(WalEntity)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WalEntity)

    /* =========================================================================
     * 🟡 UPDATE — COMMIT TRANSACTION
     * ========================================================================= */

    /**
     * -------------------------------------------------------------------------
     * UPDATE (STATE TRANSITION)
     * -------------------------------------------------------------------------
     *
     * Transitions record:
     *
     *   INITIATED → COMMITTED
     *
     * -----------------------------------------------------------------------------
     * CONSTRAINT:
     *
     *   Must preserve:
     *     - id
     *     - payloadHash
     *
     *   Only state changes are expected.
     *
     * -----------------------------------------------------------------------------
     * NOTE:
     *
     *   Room performs UPDATE based on primary key match.
     */
    @Update
    suspend fun update(record: WalEntity)

    /* =========================================================================
     * 🔴 QUERY — RECOVERY ENGINE
     * ========================================================================= */

    /**
     * -------------------------------------------------------------------------
     * GET UNCOMMITTED RECORDS
     * -------------------------------------------------------------------------
     *
     * Returns:
     *
     *   All WAL entries where:
     *     state != 'COMMITTED'
     *
     * -----------------------------------------------------------------------------
     * PURPOSE:
     *
     *   Detect:
     *     - Crashed transactions
     *     - Interrupted operations
     *     - Potential tampering
     *
     * -----------------------------------------------------------------------------
     * FORENSIC INTERPRETATION:
     *
     *   If list is non-empty:
     *     → anomaly exists in execution timeline
     *
     * -----------------------------------------------------------------------------
     * FLOW:
     *
     *   App Startup →
     *       call getUncommitted() →
     *           inspect / recover
     */
    @Query("SELECT * FROM wal_records WHERE state != 'COMMITTED'")
    suspend fun getUncommitted(): List<WalEntity>

    /* =========================================================================
     * 🧹 CLEANUP — TTL BASED DELETION
     * ========================================================================= */

    /**
     * -------------------------------------------------------------------------
     * DELETE OLD RECORDS
     * -------------------------------------------------------------------------
     *
     * Deletes records older than given threshold:
     *
     *   timestamp < threshold
     *
     * -----------------------------------------------------------------------------
     * PURPOSE:
     *
     *   - Prevent database growth
     *   - Maintain bounded forensic window
     *
     * -----------------------------------------------------------------------------
     * SAFETY:
     *
     *   Only records outside time window are removed.
     *
     * -----------------------------------------------------------------------------
     * FLOW:
     *
     *   now →
     *   threshold = now - TTL →
     *   deleteOlderThan(threshold)
     */
    @Query("DELETE FROM wal_records WHERE timestamp < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    /* =========================================================================
     * 🧪 OPTIONAL: STREAM OBSERVATION (ADVANCED)
     * ========================================================================= */

    /**
     * -------------------------------------------------------------------------
     * OBSERVE WAL STATE (OPTIONAL)
     * -------------------------------------------------------------------------
     *
     * Provides reactive stream of WAL records.
     *
     * Use cases:
     *   - Debug dashboards
     *   - Monitoring tools
     *   - Live forensic inspection
     *
     * -----------------------------------------------------------------------------
     * NOTE:
     *
     *   Not required for core flow, but valuable for diagnostics.
     */
    @Query("SELECT * FROM wal_records")
    fun observeAll(): Flow<List<WalEntity>>
}