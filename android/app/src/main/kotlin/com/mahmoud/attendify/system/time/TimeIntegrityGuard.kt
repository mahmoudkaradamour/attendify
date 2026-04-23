package com.mahmoud.attendify.system.time

import android.content.Context
import android.provider.Settings
import kotlin.math.abs

/**
 * TimeIntegrityGuard
 *
 * The single and final authority responsible for validating
 * device time integrity for attendance operations.
 *
 * SECURITY GUARANTEES:
 * - No attendance without a valid anchor
 * - No offline attendance after device reboot
 * - No cumulative drift (anchor-based comparison only)
 * - Fail-secure behavior for all invalid states
 */
class TimeIntegrityGuard(
    private val context: Context,
    private val policy: TimeIntegrityPolicy,
    private val anchorStorage: TimeAnchorStorage
) {

    /**
     * Validates current device time integrity.
     *
     * NOTE:
     * - previousSnapshot is intentionally NOT used for drift calculation.
     * - All drift checks are performed against the immutable AnchorRecord.
     */
    fun validate(): TimeIntegrityResult {

        // --------------------------------------------------
        // 1️⃣ Mandatory Initial Handshake (Anchor Presence)
        // --------------------------------------------------
        if (policy.requireInitialAnchor && !anchorStorage.hasAnchor()) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        // Anchor MUST exist beyond this point
        val anchor = try {
            anchorStorage.loadAnchor()
        } catch (e: Exception) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        // --------------------------------------------------
        // 2️⃣ Mandatory System Time Settings
        // --------------------------------------------------
        if (!isAutoTimeEnabled()) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.AUTO_TIME_DISABLED
            )
        }

        if (!isAutoTimezoneEnabled()) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.AUTO_TIMEZONE_DISABLED
            )
        }

        // --------------------------------------------------
        // 3️⃣ Capture Atomic Current Snapshot
        // --------------------------------------------------
        val current = TimeSource.snapshot()

        // --------------------------------------------------
        // 4️⃣ Timezone Policy Gate
        // --------------------------------------------------
        if (current.timeZoneId != policy.requiredTimeZone) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.TIMEZONE_MISMATCH
            )
        }

        // --------------------------------------------------
        // 5️⃣ REBOOT BLACKHOLE FIX
        // --------------------------------------------------
        // New boot = unanchored physical timeline
        // Offline attendance is FORBIDDEN until a new anchor is created online
        if (current.bootId != anchor.bootId) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        // --------------------------------------------------
        // 6️⃣ ANCHOR-BASED DRIFT DETECTION (NO ACCUMULATION)
        // --------------------------------------------------
        val deltaWall = current.wallClockMillis - anchor.wallClockMillis
        val deltaElapsed = current.elapsedRealtimeMillis - anchor.elapsedRealtimeMillis

        val drift = abs(deltaWall - deltaElapsed)

        if (drift > policy.driftToleranceMillis) {
            return TimeIntegrityResult.Tampered(
                details = "Anchor-based clock drift exceeded tolerance: ${drift}ms"
            )
        }

        // --------------------------------------------------
        // ✅ All checks passed
        // --------------------------------------------------
        return TimeIntegrityResult.OK
    }

    // --------------------------------------------------
    // System configuration checks
    // --------------------------------------------------

    private fun isAutoTimeEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AUTO_TIME,
            0
        ) == 1
    }

    private fun isAutoTimezoneEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AUTO_TIME_ZONE,
            0
        ) == 1
    }
}