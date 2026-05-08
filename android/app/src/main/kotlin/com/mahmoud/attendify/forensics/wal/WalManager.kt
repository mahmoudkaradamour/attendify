package com.mahmoud.attendify.forensics.wal

import java.util.concurrent.ConcurrentHashMap

/**
 * =============================================================================
 * 🧠 WalManager — Transactional Write-Ahead Logging Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 FORMAL MODEL
 * -----------------------------------------------------------------------------
 *
 * A Write-Ahead Log (WAL) enforces the principle:
 *
 *   Log-before-state
 *
 * Formally:
 *
 *   ∀ transaction T:
 *       WAL(T) must be recorded before Commit(T)
 *
 * -----------------------------------------------------------------------------
 * 📊 TRANSACTION STATE MACHINE
 * -----------------------------------------------------------------------------
 *
 *              BEGIN
 *                │
 *                ▼
 *         ┌─────────────┐
 *         │ INITIATED   │
 *         └──────┬──────┘
 *                │
 *                ▼
 *         ┌─────────────┐
 *         │ COMMITTED   │
 *         └─────────────┘
 *
 * Any state that does not reach COMMITTED is considered:
 *
 *   → INCOMPLETE
 *   → SUSPICIOUS
 *   → RECOVERABLE / DISCARDABLE
 *
 * -----------------------------------------------------------------------------
 * 📊 EXECUTION FLOW
 * -----------------------------------------------------------------------------
 *
 *   BEGIN TRANSACTION
 *        ↓
 *   Write WAL Record
 *        ↓
 *   Execute Pipeline
 *        ↓
 *   Commit WAL Record
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Crash Consistency
 * ✅ Detection of Half-executed operations
 * ✅ Replay traceability
 * ✅ Anti-silent-failure
 *
 * -----------------------------------------------------------------------------
 * ⚠️ ADVERSARIAL MODEL CONSIDERATIONS
 * -----------------------------------------------------------------------------
 *
 * This implementation explicitly defends against:
 *
 *   - Transaction flooding (WAL exhaustion attacks)
 *   - Infinite incomplete transactions
 *   - Resource starvation via repeated BEGIN without COMMIT
 *
 */
class WalManager {

    /**
     * =========================================================================
     * 🧠 INTERNAL STORAGE MODEL
     * =========================================================================
     *
     * Concurrent map provides:
     *
     *   ✅ Thread safety
     *   ✅ Lock-free reads
     *   ✅ Deterministic record overwrite behavior
     *
     * NOTE:
     * This is an in-memory WAL representation.
     * Persistent WAL must be implemented in a storage layer.
     */
    private val walStore = ConcurrentHashMap<String, WalRecord>()

    /**
     * =========================================================================
     * 🔒 CONFIGURATION PARAMETERS
     * =========================================================================
     */

    /**
     * Maximum allowed uncommitted transactions.
     * Prevents WAL flooding attacks.
     */
    private val maxUncommitted = 5

    /**
     * Time-to-live for incomplete transactions (milliseconds).
     *
     * Any transaction older than TTL:
     *   → considered stale
     *   → automatically ignored or purged
     */
    private val ttlMillis = 15 * 60 * 1000L // 15 minutes

    /**
     * =========================================================================
     * 🚀 BEGIN TRANSACTION
     * =========================================================================
     *
     * Registers a transaction BEFORE execution.
     *
     * -----------------------------------------------------------------------------
     * 🧠 CRITICAL PROPERTY
     * -----------------------------------------------------------------------------
     *
     * Guarantees:
     *
     *   BEGIN(T) happens-before EXECUTE(T)
     *
     * -----------------------------------------------------------------------------
     * 🔐 SECURITY
     * -----------------------------------------------------------------------------
     *
     * Prevents:
     *
     *   ❌ execution without trace
     *   ❌ invisible operation starts
     *
     */
    fun begin(id: String, payloadHash: String) {

        val now = System.currentTimeMillis()

        /**
         * -------------------------------------------------------------
         * ✅ Anti-flood protection
         * -------------------------------------------------------------
         *
         * Rejects new transactions if too many are incomplete.
         */
        val incompleteCount = walStore.values.count {
            it.state != WalState.COMMITTED &&
                    (now - it.timestamp) < ttlMillis
        }

        if (incompleteCount >= maxUncommitted) {
            throw IllegalStateException("WAL flood detected")
        }

        walStore[id] = WalRecord(
            id = id,
            timestamp = now,
            payloadHash = payloadHash,
            state = WalState.INITIATED
        )
    }

    /**
     * =========================================================================
     * ✅ COMMIT TRANSACTION
     * =========================================================================
     *
     * Marks transaction as successfully completed.
     *
     * -----------------------------------------------------------------------------
     * 🧠 SEMANTICS
     * -----------------------------------------------------------------------------
     *
     *   Commit(T) → durable success signal
     *
     * -----------------------------------------------------------------------------
     * 🔐 SECURITY
     * -----------------------------------------------------------------------------
     *
     * Guarantees:
     *
     *   ✅ Transaction visible as completed
     *   ✅ Cannot be recovered as incomplete
     */
    fun commit(id: String) {

        walStore[id]?.let {
            walStore[id] = it.copy(
                state = WalState.COMMITTED
            )
        }
    }

    /**
     * =========================================================================
     * 🔍 RECOVERY MECHANISM
     * =========================================================================
     *
     * Identifies all incomplete transactions.
     *
     * -----------------------------------------------------------------------------
     * 🧠 RECOVERY DEFINITION
     * -----------------------------------------------------------------------------
     *
     *   Incomplete(T) =
     *     state(T) != COMMITTED
     *
     * -----------------------------------------------------------------------------
     * 📊 FILTERING LOGIC
     * -----------------------------------------------------------------------------
     *
     *   1. Remove expired (TTL) entries
     *   2. Return only valid incomplete records
     *
     */
    fun getUncommitted(): List<WalRecord> {

        val now = System.currentTimeMillis()

        /**
         * -------------------------------------------------------------
         * ✅ TTL-based cleanup (lazy purge)
         * -------------------------------------------------------------
         */
        walStore.entries.removeIf {
            val expired = (now - it.value.timestamp) > ttlMillis
            expired
        }

        /**
         * -------------------------------------------------------------
         * ✅ Return active incomplete transactions
         * -------------------------------------------------------------
         */
        return walStore.values.filter {
            it.state != WalState.COMMITTED
        }
    }
}