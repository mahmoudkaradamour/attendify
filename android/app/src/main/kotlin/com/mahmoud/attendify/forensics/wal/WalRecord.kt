package com.mahmoud.attendify.forensics.wal

/**
 * =============================================================================
 * 🧾 WalRecord — Write-Ahead Log Entry
 * =============================================================================
 *
 * Represents a transaction BEFORE it is committed.
 *
 * States:
 *
 *   INITIATED → WRITTEN → COMMITTED
 *
 * Guarantees:
 *
 * ✅ Crash recovery
 * ✅ No data loss
 * ✅ Traceability
 *
 */
data class WalRecord(
    val id: String,
    val timestamp: Long,
    val payloadHash: String,
    val state: WalState
)

enum class WalState {
    INITIATED,
    COMMITTED
}