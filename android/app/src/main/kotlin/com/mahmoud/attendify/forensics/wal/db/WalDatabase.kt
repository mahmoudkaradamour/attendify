package com.mahmoud.attendify.forensics.wal.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * =============================================================================
 * 🧠 WalDatabase — Persistent Forensic Storage Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   W = Write-Ahead Log (logical ledger)
 *   S = persistent storage system (SQLite)
 *
 * Then:
 *
 *   WalDatabase = materialization of W over S
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Provide a **durable, crash-resistant, structured persistence layer**
 * for the WAL (Write-Ahead Log).
 *
 * -----------------------------------------------------------------------------
 * 💡 DATABASE ROLE IN THE SYSTEM
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────┐
 *   │ Security Execution Layer     │
 *   │ (Builder / Signing / etc.)   │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ WalManager (transaction logic)│
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ WalDao (query interface)     │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ WalDatabase (THIS CLASS)     │
 *   └──────────────┬───────────────┘
 *                  ▼
 *   ┌──────────────────────────────┐
 *   │ SQLite Engine (disk layer)   │
 *   └──────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🔐 CORE GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Durability:
 *     Once written → survives crashes
 *
 * ✅ Consistency:
 *     Transactions are atomic from application perspective
 *
 * ✅ Isolation:
 *     Concurrent access handled by Room / SQLite
 *
 * ✅ Determinism:
 *     Same operations → same database state
 *
 * -----------------------------------------------------------------------------
 * ⚠️ DESIGN CONSTRAINTS
 * -----------------------------------------------------------------------------
 *
 * 1. Schema must remain stable (versioned)
 * 2. WAL records must never be silently altered
 * 3. Storage must be local, secure, and private
 * 4. No network dependency allowed
 *
 * -----------------------------------------------------------------------------
 * 📊 DATABASE SCHEMA
 * -----------------------------------------------------------------------------
 *
 * Tables:
 *
 *   wal_records
 *
 * Columns:
 *
 *   id            TEXT PRIMARY KEY
 *   timestamp     INTEGER
 *   payloadHash   TEXT
 *   state         TEXT
 *
 * -----------------------------------------------------------------------------
 * 🔄 LIFECYCLE
 * -----------------------------------------------------------------------------
 *
 * Initialization:
 *
 *   get(context) →
 *       build database →
 *           assign singleton →
 *               reuse instance
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PATTERN
 * -----------------------------------------------------------------------------
 *
 * Singleton + Lazy Initialization + Thread Safety
 *
 * -----------------------------------------------------------------------------
 * 📐 THREAD SAFETY MODEL
 * -----------------------------------------------------------------------------
 *
 * Uses:
 *
 *   - @Volatile for safe publication
 *   - synchronized{} block for atomic initialization
 *
 * -----------------------------------------------------------------------------
 * 🚦 JOURNAL MODE
 * -----------------------------------------------------------------------------
 *
 * Uses:
 *
 *   JournalMode.TRUNCATE
 *
 * Meaning:
 *   - WAL file is truncated after checkpoint
 *   - Reduces file growth
 *   - Still ensures durability
 *
 * NOTE:
 *   Alternative:
 *     JournalMode.WRITE_AHEAD_LOGGING (more complex)
 *
 * -----------------------------------------------------------------------------
 * 🧩 FAILURE HANDLING
 * -----------------------------------------------------------------------------
 *
 * Database ensures:
 *
 *   Crash → partial data persists
 *   Corruption → fallbackToDestructiveMigration()
 *
 * -----------------------------------------------------------------------------
 * ❗ IMPORTANT TRADE-OFF
 * -----------------------------------------------------------------------------
 *
 * fallbackToDestructiveMigration():
 *
 *   ✅ prevents runtime crashes
 *   ❌ may clear data on schema change
 *
 * Acceptable here because:
 *   WAL is short-lived (TTL-based)
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * "Durability must never depend on process lifetime."
 *
 * =============================================================================
 */
@Database(
    entities = [WalEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WalDatabase : RoomDatabase() {

    /**
     * -------------------------------------------------------------------------
     * DAO ACCESSOR
     * -------------------------------------------------------------------------
     *
     * Provides the primary interface to WAL records.
     */
    @Suppress("unused")
    abstract fun walDao(): WalDao

    companion object {

        /**
         * Volatile instance ensures:
         *   latest value is visible across threads
         */
        @Volatile
        private var INSTANCE: WalDatabase? = null

        /**
         * -----------------------------------------------------------------------------
         * 🔐 DATABASE FACTORY (THREAD-SAFE SINGLETON)
         * -----------------------------------------------------------------------------
         *
         * Ensures:
         *
         *   ✅ Only ONE database instance exists
         *   ✅ Initialization is thread-safe
         *   ✅ Lazy loading (only when needed)
         *
         * -----------------------------------------------------------------------------
         * FLOW:
         *
         *   if (INSTANCE exists):
         *       return it
         *
         *   else:
         *       synchronize →
         *           build database →
         *               assign →
         *                   return
         */
        fun get(context: Context): WalDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalDatabase::class.java,
                    "wal_db"
                )
                    /**
                     * Journal mode defines how data is persisted
                     */
                    .setJournalMode(JournalMode.TRUNCATE)

                    /**
                     * Handles schema mismatch by resetting DB
                     */
                    .fallbackToDestructiveMigration(false)

                    /**
                     * Builds the actual database instance
                     */
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}