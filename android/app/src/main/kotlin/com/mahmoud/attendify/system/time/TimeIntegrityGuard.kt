package com.mahmoud.attendify.system.time

import android.content.Context
import android.provider.Settings
import kotlin.math.abs

/**
 * TimeIntegrityGuard
 *
 * ============================================================================
 * ROLE (Architectural Responsibility):
 * ============================================================================
 * The single and final authority responsible for validating device time
 * integrity for attendance decisions.
 *
 * All validation is:
 * - Anchor‑based
 * - Stateless per invocation
 * - Fail‑secure on ambiguity
 */
class TimeIntegrityGuard(
    private val context: Context,
    private val policy: TimeIntegrityPolicy,
    private val anchorStorage: TimeAnchorStorage
) {

    /**
     * validate
     *
     * Validates device time integrity at the exact moment of attendance.
     *
     * @return TimeIntegrityResult.OK / Blocked / Tampered
     */
    fun validate(): TimeIntegrityResult {

        /* ================================================================
         * 1️⃣ INITIAL ANCHOR REQUIREMENT
         * ================================================================ */
        if (policy.requireInitialAnchor && !anchorStorage.hasAnchor()) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        val anchor = try {
            anchorStorage.loadAnchor()
        } catch (_: Exception) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        /* ================================================================
         * 2️⃣ SYSTEM TIME CONFIGURATION CHECKS
         * ================================================================ */
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

        /* ================================================================
         * 3️⃣ CURRENT ATOMIC TIME SNAPSHOT
         * ================================================================ */
        val current = TimeSource.snapshot()

        /* ================================================================
         * 4️⃣ TIMEZONE POLICY ENFORCEMENT
         * ================================================================ */
        if (current.timeZoneId != policy.requiredTimeZone) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.TIMEZONE_MISMATCH
            )
        }

        /* ================================================================
         * 5️⃣ REBOOT PROTECTION
         * ================================================================ */
        if (current.bootId != anchor.bootId) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        /* ================================================================
         * 6️⃣ ANCHOR‑BASED DRIFT DETECTION (DYNAMIC TOLERANCE)
         * ================================================================ */
        val deltaWall =
            current.wallClockMillis - anchor.wallClockMillis

        val deltaElapsed =
            current.elapsedRealtimeMillis - anchor.elapsedRealtimeMillis

        val observedDrift =
            abs(deltaWall - deltaElapsed)

        val daysSinceAnchor =
            maxOf(
                0L,
                (current.wallClockMillis - anchor.wallClockMillis)
                        / (24 * 60 * 60 * 1000)
            )

        val allowedDrift =
            policy.baseDriftToleranceMillis +
                    daysSinceAnchor * policy.driftPerDayMillis

        if (observedDrift > allowedDrift) {
            return TimeIntegrityResult.Tampered(
                details =
                    "Anchor‑based clock drift exceeded tolerance: " +
                            "${observedDrift}ms (allowed ${allowedDrift}ms)"
            )
        }

        /* ================================================================
         * ✅ ALL CHECKS PASSED
         * ================================================================ */
        return TimeIntegrityResult.OK
    }

    /* ================================================================
     * SYSTEM CONFIGURATION HELPERS
     * ================================================================ */

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