package com.mahmoud.attendify.forensics

import android.util.Base64
import com.mahmoud.attendify.forensics.repository.ForensicAuditRepository
import java.util.concurrent.atomic.AtomicLong

/**
 * =============================================================================
 * 🛡 ForensicAuditTrailWriter — FINAL STABLE VERSION
 * =============================================================================
 *
 * ┌──────────────────────────── FLOW ─────────────────────────────┐
 *
 *  Orchestrator
 *      │
 *      ▼
 *  append()
 *      │
 *      ▼
 *  Hash Chain (Integrity)
 *      │
 *      ▼
 *  Repository → Room DB (D1)
 *
 * └───────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * ✅ WHAT THIS CLASS GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Append-only forensic ledger
 * ✅ Hash-chain integrity
 * ✅ Binding ledger → real-world evidence (snapshotHash)
 * ✅ Crash-safe chain continuity
 *
 */
class ForensicAuditTrailWriter(

    private val repository: ForensicAuditRepository? = null

) {

    /* =========================================================================
     * ✅ CORE STATE
     * ========================================================================= */

    private val indexCounter = AtomicLong(0)

    private var lastHash: ByteArray = ByteArray(32)

    /* =========================================================================
     * ✅ SECURE STATE PERSISTENCE (CHAIN ONLY)
     * ========================================================================= */

    @Suppress("DEPRECATION")
    private val prefs by lazy {
        try {
            val ctx = com.mahmoud.attendify.app.AppContextProvider.context()

            androidx.security.crypto.EncryptedSharedPreferences.create(
                ctx,
                "forensic_ledger_state",
                androidx.security.crypto.MasterKey.Builder(ctx)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            null
        }
    }

    init {
        loadState()
    }

    private fun loadState() {
        try {
            prefs?.getString("lastHash", null)?.let {
                lastHash = Base64.decode(it, Base64.NO_WRAP)
            }

            prefs?.getString("indexCounter", null)?.let {
                indexCounter.set(it.toLong())
            }
        } catch (_: Exception) {
        }
    }

    private fun persistState() {
        try {
            prefs?.edit()
                ?.putString("lastHash", Base64.encodeToString(lastHash, Base64.NO_WRAP))
                ?.putString("indexCounter", indexCounter.get().toString())
                ?.apply()
        } catch (_: Exception) {
        }
    }

    /* =========================================================================
     * ✅ PRIMARY APPEND (D2 + D1)
     * ========================================================================= */

    suspend fun append(
        evidence: NormalizedForensicEvidence
    ): ForensicAuditRecord {

        /** ✅ Build record */
        val record = ForensicAuditRecord(
            index = indexCounter.incrementAndGet(),
            timestampMillis = evidence.timestampMillis,
            snapshotId = evidence.snapshotId,
            decision = evidence.decision,
            snapshotHash = evidence.snapshotHash, // ✅ FIXED
            resultHash = ByteArray(0),
            previousHash = lastHash
        )

        /** ✅ Compute chain hash */
        val hash = ForensicAuditHasher.compute(record)

        val finalRecord = record.copy(resultHash = hash)

        /** ✅ Update chain */
        lastHash = hash
        persistState()

        /** ✅ D1 persistence */
        repository?.append(finalRecord.toEntity())

        return finalRecord
    }

    /* =========================================================================
     * ✅ INITIATED (C2)
     * ========================================================================= */

    suspend fun preLogInitiated(
        employeeId: String,
        timestamp: Long
    ): ForensicAuditRecord {

        val record = ForensicAuditRecord(
            index = indexCounter.incrementAndGet(),
            timestampMillis = timestamp,
            snapshotId = "INIT-$employeeId-$timestamp",
            decision = "INITIATED",
            snapshotHash = ByteArray(0),
            resultHash = ByteArray(0),
            previousHash = lastHash
        )

        val hash = ForensicAuditHasher.compute(record)

        val finalRecord = record.copy(resultHash = hash)

        lastHash = hash
        persistState()

        repository?.append(finalRecord.toEntity())

        return finalRecord
    }

    /* =========================================================================
     * ✅ SYSTEM EVENTS
     * ========================================================================= */

    suspend fun appendSystemEvent(
        event: String,
        details: String
    ): ForensicAuditRecord {

        val now = System.currentTimeMillis()

        val record = ForensicAuditRecord(
            index = indexCounter.incrementAndGet(),
            timestampMillis = now,
            snapshotId = "SYS-$event-$now",
            decision = "$event|$details",
            snapshotHash = ByteArray(0),
            resultHash = ByteArray(0),
            previousHash = lastHash
        )

        val hash = ForensicAuditHasher.compute(record)

        val finalRecord = record.copy(resultHash = hash)

        lastHash = hash
        persistState()

        repository?.append(finalRecord.toEntity())

        return finalRecord
    }
}

/* ============================================================================
 * ✅ ENTITY MAPPER
 * ========================================================================== */

private fun ForensicAuditRecord.toEntity(): com.mahmoud.attendify.forensics.db.ForensicAuditEntity {
    return com.mahmoud.attendify.forensics.db.ForensicAuditEntity(
        index = index,
        timestampMillis = timestampMillis,
        snapshotId = snapshotId,
        decision = decision,
        snapshotHash = snapshotHash,
        resultHash = resultHash,
        previousHash = previousHash
    )
}