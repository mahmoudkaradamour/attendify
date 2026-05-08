package com.mahmoud.attendify.forensics.wal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * =============================================================================
 * 🧠 WalEntity — Forensic Write-Ahead Log Record
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   T = logical transaction (attendance event)
 *   W = WAL (persistent journal)
 *
 * Then:
 *
 *   WalEntity ∈ W
 *   represents a single immutable record of T
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * This entity models a **durable, atomic record** that captures:
 *
 *   - Transaction identity
 *   - Temporal context
 *   - Cryptographic fingerprint
 *   - Execution state
 *
 * -----------------------------------------------------------------------------
 * 📊 ROLE IN THE SYSTEM
 * -----------------------------------------------------------------------------
 *
 * The WAL (Write-Ahead Log) ensures:
 *
 *   BEFORE executing a critical operation:
 *       → record intent (INITIATED)
 *
 *   AFTER successful execution:
 *       → mark completion (COMMITTED)
 *
 * -----------------------------------------------------------------------------
 * 🧩 LIFECYCLE (STATE TRANSITION)
 * -----------------------------------------------------------------------------
 *
 *   ┌───────────────┐
 *   │ Transaction T │
 *   └───────┬───────┘
 *           ▼
 *   ┌───────────────┐
 *   │ INITIATED     │  ← persisted immediately
 *   └───────┬───────┘
 *           ▼
 *   ┌───────────────┐
 *   │ PROCESSING    │  (hash, sign, network, etc.)
 *   └───────┬───────┘
 *           ▼
 *   ┌───────────────┐
 *   │ COMMITTED     │  ← finalized state
 *   └───────────────┘
 *
 * -----------------------------------------------------------------------------
 * ⚠️ FAILURE SEMANTICS
 * -----------------------------------------------------------------------------
 *
 * Case A:
 *   INITIATED exists, COMMIT missing
 *   → Indicates INTERRUPTED or CRASHED transaction
 *
 * Case B:
 *   No record exists
 *   → Transaction never started
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY SIGNIFICANCE
 * -----------------------------------------------------------------------------
 *
 * This structure provides:
 *
 *   ✅ Forensic traceability (audit trail)
 *   ✅ Crash survivability
 *   ✅ Replay reconstruction capability
 *   ✅ Chain-of-custody continuity
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL DESIGN CONSTRAINTS
 * -----------------------------------------------------------------------------
 *
 * 1. Immutability:
 *    - Records are NEVER modified except state transition
 *
 * 2. Deterministic identifier:
 *    - id MUST uniquely identify transaction
 *
 * 3. Hash integrity:
 *    - payloadHash MUST be canonical (Base64 encoded SHA-256)
 *
 * 4. No raw payload storage:
 *    - Privacy + storage efficiency
 *
 * -----------------------------------------------------------------------------
 * 📐 DATA MODEL
 * -----------------------------------------------------------------------------
 *
 * Fields:
 *
 *   id           → unique transaction identifier (UUID)
 *   timestamp    → creation time (milliseconds)
 *   payloadHash  → Base64 encoded SHA-256 hash of payload
 *   state        → lifecycle marker (INITIATED / COMMITTED)
 *
 * -----------------------------------------------------------------------------
 * 📊 STORAGE CHARACTERISTICS
 * -----------------------------------------------------------------------------
 *
 *   - Stored in SQLite (Room)
 *   - Persistent across crashes
 *   - Queried for incomplete transactions
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * "Every critical operation must leave a trace before it executes."
 *
 * =============================================================================
 */
@Entity(tableName = "wal_records")
data class WalEntity(

    /**
     * -------------------------------------------------------------------------
     * 🆔 PRIMARY KEY (TRANSACTION ID)
     * -------------------------------------------------------------------------
     *
     * Properties:
     *   - Globally unique (UUID recommended)
     *   - Deterministic reference for reconciliation
     *
     * Used for:
     *   - Linking INITIATED ↔ COMMITTED
     *   - Recovery processing
     */
    @PrimaryKey
    val id: String,

    /**
     * -------------------------------------------------------------------------
     * ⏱ TIMESTAMP (CREATION TIME)
     * -------------------------------------------------------------------------
     *
     * Represents:
     *   - When the transaction BEGIN was recorded
     *
     * Units:
     *   - milliseconds since Unix epoch
     *
     * Used for:
     *   - TTL cleanup
     *   - forensic timeline reconstruction
     */
    val timestamp: Long,

    /**
     * -------------------------------------------------------------------------
     * 🔐 PAYLOAD HASH (CRYPTOGRAPHIC FINGERPRINT)
     * -------------------------------------------------------------------------
     *
     * Format:
     *   Base64 encoded SHA-256 hash
     *
     * Important:
     *   - NEVER use toString() of bytes
     *   - Must be canonical encoding
     *
     * Purpose:
     *   - Integrity verification
     *   - Lightweight storage
     *   - Privacy preservation (no raw data stored)
     *
     * Example:
     *   "aG9X3k...==" (Base64)
     */
    val payloadHash: String,

    /**
     * -------------------------------------------------------------------------
     * 🔄 STATE (TRANSACTION LIFECYCLE)
     * -------------------------------------------------------------------------
     *
     * Allowed values:
     *
     *   "INITIATED"
     *       → Transaction started, not yet completed
     *
     *   "COMMITTED"
     *       → Successfully completed
     *
     * -----------------------------------------------------------------------------
     * STATE GRAPH
     * -----------------------------------------------------------------------------
     *
     *   INITIATED ───────► COMMITTED
     *
     * No backward transitions allowed.
     *
     * -----------------------------------------------------------------------------
     * SECURITY NOTE:
     *
     * Any record remaining in INITIATED state is considered:
     *
     *   ⚠️ Suspicious (possible crash or tampering)
     */
    val state: String
)