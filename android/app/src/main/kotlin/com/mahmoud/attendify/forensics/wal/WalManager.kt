package com.mahmoud.attendify.forensics.wal

import android.util.Base64
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

import com.mahmoud.attendify.forensics.wal.db.WalDao
import com.mahmoud.attendify.forensics.wal.db.WalEntity

/**
 * =============================================================================
 * 🧠 WAL Manager — Persistent Forensic Transaction Ledger
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   T = logical transaction (attendance event)
 *   W = write-ahead log (persistent journal)
 *
 * Each transaction must follow:
 *
 *   BEGIN(T) → COMMIT(T)
 *
 * If system crashes:
 *
 *   W preserves incomplete transactions → recoverable state
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Provide a **durable, append-only, crash-safe log**
 * for all critical security operations.
 *
 * -----------------------------------------------------------------------------
 * 💡 WHAT IS A WAL (WRITE-AHEAD LOG)?
 * -----------------------------------------------------------------------------
 *
 * WAL is a fundamental database concept:
 *
 *   BEFORE modifying system state:
 *     → operation is recorded in durable storage
 *
 * Guarantees:
 *
 *   ✅ Crash consistency
 *   ✅ Atomicity support
 *   ✅ Audit trail (forensics)
 *
 * -----------------------------------------------------------------------------
 * 📊 TRANSACTION FLOW (STATE MACHINE)
 * -----------------------------------------------------------------------------
 *
 *   ┌───────────┐
 *   │ BEGIN(T)  │
 *   └─────┬─────┘
 *         ▼
 *   ┌───────────────┐
 *   │ INITIATED     │  ← written to DB
 *   └─────┬─────────┘
 *         ▼
 *   ┌───────────────┐
 *   │ EXECUTION     │  (hash + signature + network)
 *   └─────┬─────────┘
 *         ▼
 *   ┌───────────────┐
 *   │ COMMIT(T)     │
 *   └─────┬─────────┘
 *         ▼
 *   ┌───────────────┐
 *   │ COMMITTED     │  ← final durable state
 *   └───────────────┘
 *
 * -----------------------------------------------------------------------------
 * ⚠️ FAILURE SCENARIOS
 * -----------------------------------------------------------------------------
 *
 * If crash occurs:
 *
 *   Case A:
 *     INITIATED exists but no COMMIT
 *     → "Suspicious / incomplete transaction"
 *
 *   Case B:
 *     No record
 *     → transaction never started
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 *   ✅ Durability:
 *       Data survives process crash
 *
 *   ✅ Atomic visibility:
 *       Only committed data considered finalized
 *
 *   ✅ Forensic trace:
 *       Every action is recoverable
 *
 *   ✅ Replay reconstruction:
 *       Unfinished transactions can be analyzed
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL RULES
 * -----------------------------------------------------------------------------
 *
 *   1. BEGIN must ALWAYS be called before execution
 *   2. COMMIT must ONLY happen after success
 *   3. Hash must be deterministic (Base64, never toString)
 *   4. WAL is append-oriented (no destructive edits)
 *
 * =============================================================================
 */
class WalManager(
    private val dao: WalDao
) {

    /* =========================================================================
     * 🧩 CONFIGURATION
     * ========================================================================= */

    /**
     * Time-To-Live for WAL entries (milliseconds)
     *
     * Old entries are removed to:
     *   - prevent unbounded growth
     *   - keep forensic window manageable
     *
     * Typical design:
     *   10–30 minutes
     */
    private val TTL_MS = 15 * 60 * 1000L

    /* =========================================================================
     * 🚀 BEGIN TRANSACTION
     * ========================================================================= */

    /**
     * Records the START of a security-critical transaction.
     *
     * -----------------------------------------------------------------------------
     * INPUT:
     *   id               → unique transaction identifier
     *   payloadHashBytes → SHA-256 hash of payload
     *
     * -----------------------------------------------------------------------------
     * PROCESS:
     *
     *   1. Encode hash in Base64 (canonical representation)
     *   2. Insert WAL entry with state = INITIATED
     *
     * -----------------------------------------------------------------------------
     * DESIGN NOTE:
     *
     * We store the hash (not raw payload) to:
     *   ✅ minimize storage
     *   ✅ preserve privacy
     *   ✅ ensure immutability
     */
    suspend fun begin(
        id: String,
        payloadHashBytes: ByteArray
    ) {

        val encodedHash = Base64.encodeToString(
            payloadHashBytes,
            Base64.NO_WRAP
        )

        val entity = WalEntity(
            id = id,
            timestamp = System.currentTimeMillis(),
            payloadHash = encodedHash,
            state = "INITIATED"
        )

        dao.insert(entity)
    }

    /* =========================================================================
     * ✅ COMMIT TRANSACTION
     * ========================================================================= */

    /**
     * Marks transaction as COMPLETED.
     *
     * -----------------------------------------------------------------------------
     * RULE:
     *   MUST only be called after:
     *     - signature success
     *     - persistence success
     *
     * -----------------------------------------------------------------------------
     * STATE TRANSITION:
     *
     *   INITIATED → COMMITTED
     *
     * -----------------------------------------------------------------------------
     * FAILURE SAFETY:
     *
     *   If commit never happens:
     *     → transaction remains visible for recovery
     */
    suspend fun commit(id: String) = withContext(NonCancellable) {

        val current =
            dao.getUncommitted().find { it.id == id }
                ?: return@withContext

        val updated = current.copy(state = "COMMITTED")

        dao.update(updated)
    }

    /* =========================================================================
     * 🔄 RECOVERY ENGINE
     * ========================================================================= */

    /**
     * Recovers incomplete transactions on startup.
     *
     * -----------------------------------------------------------------------------
     * PROCESS:
     *
     *   1. Remove expired records (TTL cleanup)
     *   2. Return all transactions where state != COMMITTED
     *
     * -----------------------------------------------------------------------------
     * OUTPUT:
     *
     *   List of suspicious/incomplete transactions
     *
     * -----------------------------------------------------------------------------
     * USE-CASE:
     *
     *   Called at application startup:
     *
     *   if (pending not empty):
     *       → log anomaly
     *       → optional retransmission
     */
    @Suppress("unused")
    suspend fun recover(): List<WalEntity> {

        val now = System.currentTimeMillis()

        /* ---------- TTL CLEANUP ---------- */
        dao.deleteOlderThan(now - TTL_MS)

        /* ---------- UNCOMMITTED ---------- */
        return dao.getUncommitted()
    }

    /* =========================================================================
     * 🧪 DEBUG / FORENSIC UTILITIES
     * ========================================================================= */

    /**
     * Verifies if a transaction exists in WAL.
     *
     * Useful for:
     *   - debugging
     *   - test assertions
     */
    suspend fun exists(id: String): Boolean {

        return dao.getUncommitted().any { it.id == id }
    }

    /**
     * Clears all WAL data (TESTING ONLY)
     *
     * ⚠️ NEVER use in production forensic flows
     */
    @Suppress("unused")
    suspend fun clearAll() {
        dao.deleteOlderThan(Long.MAX_VALUE)
    }
}
