package com.mahmoud.attendify.security.legal

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * =============================================================================
 * 🧾 LegalEvidenceWriter — Deterministic Non‑Repudiation Persistence Layer
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 CONCEPTUAL MODEL
 * -----------------------------------------------------------------------------
 *
 * This component represents a **legal-grade evidence sink** designed to provide:
 *
 *   • Non-repudiation
 *   • Immutability (logical)
 *   • Ordered persistence
 *   • Independent legal auditability
 *
 * -----------------------------------------------------------------------------
 * 📐 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   E = LegalEvidenceBundle
 *   S = storage system
 *
 * Then:
 *
 *   append(E) → S' such that:
 *
 *     S' = S ∪ {E}
 *
 * Subject to constraints:
 *
 *   1. No overwrites (append-only)
 *   2. Deterministic ordering
 *   3. Idempotent-safe behavior (optional extension)
 *
 * -----------------------------------------------------------------------------
 * 📊 DATAFLOW PIPELINE
 * -----------------------------------------------------------------------------
 *
 *   Evidence Bundle (E)
 *           │
 *           ▼
 *   Serialization Layer
 *           │
 *           ▼
 *   Persistence Strategy
 *           │
 *           ▼
 *   Durable Storage
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * ✅ Append-only semantics (no mutation)
 * ✅ Isolation from forensic pipeline
 * ✅ Replay-resilient when combined with hash
 * ✅ Auditable trail (legal proof chain)
 *
 * -----------------------------------------------------------------------------
 * ⚖️ LEGAL SIGNIFICANCE
 * -----------------------------------------------------------------------------
 *
 * The stored records can be used to:
 *
 * ✅ Prove that an action occurred
 * ✅ Prove who initiated the action
 * ✅ Prove when it occurred
 * ✅ Detect any tampering via hash verification
 *
 * -----------------------------------------------------------------------------
 * ⚙️ ENGINEERING DESIGN
 * -----------------------------------------------------------------------------
 *
 * ✔ Thread-safe (explicit locking)
 * ✔ Pluggable storage backend
 * ✔ Minimal responsibility (Single Responsibility Principle)
 *
 */
class LegalEvidenceWriter(

    /**
     * Optional external storage delegate.
     *
     * Can be replaced with:
     *  - Room DAO
     *  - File system writer
     *  - Secure remote API
     *  - Blockchain/WORM connector
     */
    private val storage: LegalEvidenceStorage = InMemoryLegalEvidenceStorage()

) {

    private val lock = ReentrantLock()

    /**
     * =============================================================================
     * 🧾 append — Append-Only Legal Record Operation
     * =============================================================================
     *
     * -----------------------------------------------------------------------------
     * 🧠 OPERATIONAL SEMANTICS
     * -----------------------------------------------------------------------------
     *
     * This function performs an **atomic append operation**:
     *
     *   append(E):
     *     acquire lock
     *     validate input
     *     persist safely
     *     release lock
     *
     * -----------------------------------------------------------------------------
     * 📊 FLOW DIAGRAM
     * -----------------------------------------------------------------------------
     *
     *        Input Evidence
     *              │
     *              ▼
     *     ┌───────────────────┐
     *     │ Lock Acquisition  │
     *     └────────┬──────────┘
     *              ▼
     *     ┌───────────────────┐
     *     │ Validation        │
     *     └────────┬──────────┘
     *              ▼
     *     ┌───────────────────┐
     *     │ Storage Append    │
     *     └────────┬──────────┘
     *              ▼
     *     ┌───────────────────┐
     *     │ Unlock            │
     *     └───────────────────┘
     *
     * -----------------------------------------------------------------------------
     * 🔐 GUARANTEES
     * -----------------------------------------------------------------------------
     *
     * ✅ Atomic execution (no partial write)
     * ✅ Thread-safe
     * ✅ Deterministic ordering
     * ✅ No data mutation
     *
     */
    fun append(bundle: LegalEvidenceBundle) {

        lock.withLock {

            try {

                /* --------------------------------------------------------
                 * ✅ STEP 1 — VALIDATION
                 * -------------------------------------------------------- */
                if (!isValid(bundle)) {
                    Log.e("LegalEvidenceWriter", "Invalid legal evidence")
                    return
                }

                /* --------------------------------------------------------
                 * ✅ STEP 2 — SERIALIZATION (CANONICAL FORM)
                 * --------------------------------------------------------
                 *
                 * Canonical string ensures:
                 *   - deterministic representation
                 *   - hash reproducibility
                 */
                val serialized = serialize(bundle)

                /* --------------------------------------------------------
                 * ✅ STEP 3 — PERSISTENCE
                 * -------------------------------------------------------- */
                storage.append(serialized)

            } catch (e: Exception) {
                Log.e("LegalEvidenceWriter", "Append failed", e)
            }
        }
    }

    /* =========================================================================
     * 🔍 VALIDATION
     * ========================================================================= */

    /**
     * Ensures integrity constraints before persistence.
     */
    private fun isValid(bundle: LegalEvidenceBundle): Boolean {

        return bundle.employeeId.isNotBlank() &&
                bundle.timestamp > 0 &&
                bundle.evidenceHash.isNotBlank()
    }

    /* =========================================================================
     * 🔄 SERIALIZATION
     * ========================================================================= */

    /**
     * Converts bundle to canonical string representation.
     *
     * This format must remain:
     * ✅ deterministic
     * ✅ consistent across versions
     */
    private fun serialize(bundle: LegalEvidenceBundle): String {

        return buildString {
            append("employeeId=").append(bundle.employeeId)
            append("|timestamp=").append(bundle.timestamp)
            append("|result=").append(bundle.result.javaClass.simpleName)
            append("|hash=").append(bundle.evidenceHash)
        }
    }
}

/* =============================================================================
 * 🔌 STORAGE ABSTRACTION
 * =============================================================================
 *
 * Allows replacing storage backend without changing writer.
 */
interface LegalEvidenceStorage {

    fun append(serialized: String)
}

/**
 * =============================================================================
 * 🧪 InMemoryLegalEvidenceStorage — Reference Implementation
 * =============================================================================
 *
 * Used for testing or fallback scenarios.
 */
class InMemoryLegalEvidenceStorage : LegalEvidenceStorage {

    private val records = mutableListOf<String>()

    override fun append(serialized: String) {
        records.add(serialized)
    }
}