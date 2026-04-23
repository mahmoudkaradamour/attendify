package com.mahmoud.attendify.system.time

import android.content.Context
import android.provider.Settings
import kotlin.math.abs

/**
 * TimeIntegrityGuard
 *
 * The single authority responsible for validating device time integrity.
 *
 * Any attendance operation MUST call this guard before proceeding.
 */
class TimeIntegrityGuard(
    private val context: Context,
    private val policy: TimeIntegrityPolicy,
    private val anchorStorage: TimeAnchorStorage
) {

    /**
     * Main entry point.
     *
     * @param previousSnapshot Last trusted time snapshot (may be null).
     */
    fun validate(previousSnapshot: TimeSnapshot?): TimeIntegrityResult {

        // 1️⃣ Mandatory initial handshake (Anchor)
        if (policy.requireInitialAnchor && !anchorStorage.hasAnchor()) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.INITIAL_ANCHOR_REQUIRED
            )
        }

        // 2️⃣ Mandatory time sync checks
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

        // 3️⃣ Capture current snapshot atomically
        val current = TimeSource.snapshot()

        // 4️⃣ Timezone policy gate
        if (current.timeZoneId != policy.requiredTimeZone) {
            return TimeIntegrityResult.Blocked(
                TimeIntegrityResult.Reason.TIMEZONE_MISMATCH
            )
        }

        // 5️⃣ First record after anchor (no delta yet)
        if (previousSnapshot == null) {
            return TimeIntegrityResult.OK
        }

        // 6️⃣ Reboot handling (boot ID changed)
        if (current.bootId != previousSnapshot.bootId) {
            if (!policy.allowPostBootRecords) {
                return TimeIntegrityResult.Blocked(
                    TimeIntegrityResult.Reason.CLOCK_DRIFT_DETECTED
                )
            }
            // Allowed by policy – but flagged later in Attendance Proof
            return TimeIntegrityResult.OK
        }

        // 7️⃣ Time Drift Detection (core security rule)
        val deltaWall = current.wallClockMillis - previousSnapshot.wallClockMillis
        val deltaElapsed =
            current.elapsedRealtimeMillis - previousSnapshot.elapsedRealtimeMillis

        val drift = abs(deltaWall - deltaElapsed)

        return if (drift > policy.driftToleranceMillis) {
            TimeIntegrityResult.Tampered(
                details = "Clock drift exceeded tolerance: ${drift}ms"
            )
        } else {
            TimeIntegrityResult.OK
        }
    }

    // --------------------------------------------------
    // System checks
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
