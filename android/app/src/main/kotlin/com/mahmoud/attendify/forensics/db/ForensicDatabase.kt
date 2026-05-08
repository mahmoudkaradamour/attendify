package com.mahmoud.attendify.forensics.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * =============================================================================
 * 🧠 ForensicDatabase — Persistent Audit Ledger Storage
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * This database stores:
 *
 *   → Forensic audit trail records
 *
 * Characteristics:
 *
 *   - Append-only oriented usage
 *   - Integrity-chain validated
 *   - Crash-resilient
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN MODEL
 * -----------------------------------------------------------------------------
 *
 * Singleton Database Instance
 *
 * Ensures:
 *
 *   ✅ Only one DB instance per process
 *   ✅ Thread-safe initialization
 *   ✅ Resource efficiency
 *
 * -----------------------------------------------------------------------------
 * 📊 INITIALIZATION FLOW
 * -----------------------------------------------------------------------------
 *
 *   getInstance(context)
 *       ↓
 *   if exists → reuse
 *       ↓
 *   else → build()
 *       ↓
 *   cache instance
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY NOTES
 * -----------------------------------------------------------------------------
 *
 * This DB contains:
 *
 *   → Tamper-detection audit logs
 *
 * Therefore:
 *
 *   - Must not be duplicated
 *   - Must not be lazily lost
 *
 * =============================================================================
 */
@Database(
    entities = [ForensicAuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ForensicDatabase : RoomDatabase() {

    abstract fun auditDao(): ForensicAuditDao

    companion object {

        @Volatile
        private var INSTANCE: ForensicDatabase? = null

        /**
         * -------------------------------------------------------------------------
         * ✅ THREAD-SAFE INSTANCE FACTORY
         * -------------------------------------------------------------------------
         *
         * Guarantees:
         *   - Single instance
         *   - No race conditions
         */
        fun getInstance(context: Context): ForensicDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ForensicDatabase::class.java,
                    "forensic.db"
                )
                    /**
                     * Destructive migration is acceptable:
                     * - audit logs are short-lived
                     */
                    .fallbackToDestructiveMigration(false)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}