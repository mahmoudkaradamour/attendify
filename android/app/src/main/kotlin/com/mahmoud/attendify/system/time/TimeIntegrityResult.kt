package com.mahmoud.attendify.system.time

/**
 * TimeIntegrityResult
 *
 * Represents the final verdict of time validation.
 * No attendance operation may proceed unless result == OK.
 */
sealed class TimeIntegrityResult {

    /**
     * Time is valid and consistent.
     */
    data object OK : TimeIntegrityResult()

    /**
     * Device time settings are invalid (Auto-Time / Timezone).
     */
    data class Blocked(
        val reason: Reason
    ) : TimeIntegrityResult()

    /**
     * Time manipulation detected (malicious or unsafe).
     */
    data class Tampered(
        val details: String
    ) : TimeIntegrityResult()

    enum class Reason {
        AUTO_TIME_DISABLED,
        AUTO_TIMEZONE_DISABLED,
        TIMEZONE_MISMATCH,
        INITIAL_ANCHOR_REQUIRED,
        CLOCK_DRIFT_DETECTED
    }
}
