package com.mahmoud.attendify.system.time

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureTimeAnchorStorage
 *
 * Keystore-backed implementation of TimeAnchorStorage.
 *
 * Guarantees:
 * - Anchor cannot be forged or modified
 * - Clear Data forces re-anchoring
 * - Keystore invalidation is treated as security reset
 */
class SecureTimeAnchorStorage(
    context: Context
) : TimeAnchorStorage {

    private val prefs = createEncryptedPrefs(context)

    override fun hasAnchor(): Boolean {
        return try {
            prefs.contains(KEY_WALL) &&
                    prefs.contains(KEY_ELAPSED) &&
                    prefs.contains(KEY_BOOT_ID) &&
                    prefs.contains(KEY_TZ)
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearAnchor()
            false
        }
    }

    override fun loadAnchor(): TimeSnapshot {
        return try {
            TimeSnapshot(
                wallClockMillis = prefs.getLong(KEY_WALL, -1),
                elapsedRealtimeMillis = prefs.getLong(KEY_ELAPSED, -1),
                uptimeMillis = prefs.getLong(KEY_UPTIME, -1),
                bootId = prefs.getString(KEY_BOOT_ID, null)
                    ?: throw IllegalStateException("Missing bootId"),
                timeZoneId = prefs.getString(KEY_TZ, null)
                    ?: throw IllegalStateException("Missing timezone")
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearAnchor()
            throw IllegalStateException("Anchor invalidated, re-initialization required")
        }
    }

    override fun saveAnchor(snapshot: TimeSnapshot) {
        try {
            prefs.edit()
                .putLong(KEY_WALL, snapshot.wallClockMillis)
                .putLong(KEY_ELAPSED, snapshot.elapsedRealtimeMillis)
                .putLong(KEY_UPTIME, snapshot.uptimeMillis)
                .putString(KEY_BOOT_ID, snapshot.bootId)
                .putString(KEY_TZ, snapshot.timeZoneId)
                .apply()
        } catch (e: KeyPermanentlyInvalidatedException) {
            clearAnchor()
            throw IllegalStateException("Keystore invalidated during anchor save")
        }
    }

    override fun clearAnchor() {
        prefs.edit().clear().apply()
    }

    // --------------------------------------------------
    // Internal helpers
    // --------------------------------------------------

    private fun createEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            createMasterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun createMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    companion object {

        private const val PREF_NAME = "secure_time_anchor"

        private const val KEY_WALL = "anchor_wall_time"
        private const val KEY_ELAPSED = "anchor_elapsed_time"
        private const val KEY_UPTIME = "anchor_uptime"
        private const val KEY_BOOT_ID = "anchor_boot_id"
        private const val KEY_TZ = "anchor_timezone"
    }
}
