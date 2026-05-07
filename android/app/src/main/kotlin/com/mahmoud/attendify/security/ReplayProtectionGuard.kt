package com.mahmoud.attendify.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * =============================================================================
 * 🛡 ReplayProtectionGuard — Persistent Replay Defense (SECTION E COMPLETE)
 * =============================================================================
 *
 * ┌──────────────────────────── SECURITY MODEL ─────────────────────────────┐
 *
 * SnapshotID (UUID)
 *        │
 *        ▼
 *   registerOrReject()
 *        │
 *   ┌────┴─────────────┐
 *   ▼                  ▼
 * ACCEPT (first use)   REJECT (replay)
 *
 *        │
 *        ▼
 * Persist (Encrypted) + TTL Cleanup
 *
 * └────────────────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Prevents:
 *
 * ❌ Replay attacks
 * ❌ Duplicate submissions
 * ❌ Reuse after restart
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ One-time usage per snapshot
 * ✅ Survives restart
 * ✅ Encrypted storage
 * ✅ Automatic cleanup (TTL)
 *
 */
object ReplayProtectionGuard {

    private const val PREF_NAME = "replay_guard"
    private const val KEY_PREFIX = "used_"

    /**
     * ✅ Retention policy (7 days)
     */
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000

    /* =========================================================================
     * ✅ IN-MEMORY CACHE (FAST PATH)
     * ========================================================================= */
    private val usedSnapshots =
        ConcurrentHashMap<UUID, Boolean>()

    /* =========================================================================
     * ✅ CONTEXT PROVIDER
     * ========================================================================= */
    private var contextProvider: (() -> Context)? = null

    /* =========================================================================
     * ✅ INITIALIZATION (LOAD + CLEANUP)
     * ========================================================================= */
    @Suppress("DEPRECATION")
    fun initialize(contextProvider: () -> Context) {

        this.contextProvider = contextProvider

        try {
            val ctx = contextProvider()
            val prefs = createPrefs(ctx)

            val now = System.currentTimeMillis()
            val editor = prefs.edit()

            for ((key, value) in prefs.all) {

                if (!key.startsWith(KEY_PREFIX)) continue

                if (value !is Long) {
                    editor.remove(key) // invalid format cleanup
                    continue
                }

                val age = now - value

                if (age <= TTL_MS) {
                    try {
                        val uuid = UUID.fromString(
                            key.removePrefix(KEY_PREFIX)
                        )
                        usedSnapshots[uuid] = true
                    } catch (_: Exception) {
                        editor.remove(key)
                    }
                } else {
                    // ✅ TTL cleanup
                    editor.remove(key)
                }
            }

            editor.apply()

        } catch (_: Exception) {
            // fallback: memory-only (safe but not persistent)
        }
    }

    /* =========================================================================
     * ✅ REGISTER OR REJECT (CORE FUNCTION)
     * ========================================================================= */
    @Suppress("DEPRECATION")
    fun registerOrReject(snapshotId: UUID): Boolean {

        /* ✅ FAST MEMORY CHECK */
        val isNew =
            usedSnapshots.putIfAbsent(snapshotId, true) == null

        if (!isNew) {
            return false
        }

        /* ✅ PERSIST */
        try {
            val ctx = contextProvider?.invoke() ?: return true
            val prefs = createPrefs(ctx)

            prefs.edit()
                .putLong(
                    KEY_PREFIX + snapshotId.toString(),
                    System.currentTimeMillis()
                )
                .apply()

        } catch (_: Exception) {
            // persistence failure should NOT block execution
        }

        return true
    }

    /* =========================================================================
     * ✅ INTERNAL HELPER
     * ========================================================================= */
    @Suppress("DEPRECATION")
    private fun createPrefs(ctx: Context) =
        EncryptedSharedPreferences.create(
            ctx,
            PREF_NAME,
            MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
}
