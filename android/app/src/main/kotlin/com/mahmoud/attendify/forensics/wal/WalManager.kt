package com.mahmoud.attendify.forensics.wal

import java.util.concurrent.ConcurrentHashMap

/**
 * =============================================================================
 * 🧠 WalManager — Crash-Consistent Transaction Journal
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Guarantees:
 *
 *   If system crashes:
 *     → We know what was in progress
 *     → We can recover / mark suspicious
 *
 * -----------------------------------------------------------------------------
 * 📊 FLOW
 * -----------------------------------------------------------------------------
 *
 * Begin → Write WAL → Process → Commit WAL → Persist
 *
 */
class WalManager {

    private val walStore = ConcurrentHashMap<String, WalRecord>()

    /**
     * =============================================================================
     * 🚀 BEGIN TRANSACTION
     * =============================================================================
     */
    fun begin(id: String, payloadHash: String) {

        walStore[id] = WalRecord(
            id = id,
            timestamp = System.currentTimeMillis(),
            payloadHash = payloadHash,
            state = WalState.INITIATED
        )
    }

    /**
     * =============================================================================
     * ✅ COMMIT TRANSACTION
     * =============================================================================
     */
    fun commit(id: String) {

        walStore[id]?.let {
            walStore[id] = it.copy(state = WalState.COMMITTED)
        }
    }

    /**
     * =============================================================================
     * 🔍 RECOVERY
     * =============================================================================
     *
     * Finds incomplete transactions.
     */
    fun getUncommitted(): List<WalRecord> {

        return walStore.values.filter {
            it.state != WalState.COMMITTED
        }
    }
}