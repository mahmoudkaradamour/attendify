package com.mahmoud.attendify.system.time

/**
 * TimeIntegrityResult
 *
 * Represents the final verdict of time validation.
 *
 * CORE SECURITY CONTRACT:
 * ----------------------
 * - Attendance may ONLY proceed if result == OK
 * - Any other result is a hard stop (no justification allowed)
 *
 * Time integrity does NOT currently support
 * "allowed but non‑ideal" states.
 */
sealed class TimeIntegrityResult {

    /**
     * Device time is valid, consistent,
     * and anchored correctly.
     *
     * This is the ONLY state that allows attendance to proceed.
     */
    data object OK : TimeIntegrityResult()

    /**
     * Device time configuration is invalid.
     *
     * These are administrative or configuration issues
     * (auto‑time, timezone, initial anchor).
     */
    data class Blocked(
        val reason: Reason
    ) : TimeIntegrityResult()

    /**
     * Active or passive time manipulation detected.
     *
     * Considered a security incident.
     */
    data class Tampered(
        val details: String
    ) : TimeIntegrityResult()

    /**
     * Enumerates all block reasons.
     */
    enum class Reason {
        AUTO_TIME_DISABLED,
        AUTO_TIMEZONE_DISABLED,
        TIMEZONE_MISMATCH,
        INITIAL_ANCHOR_REQUIRED,
        CLOCK_DRIFT_DETECTED
    }
}

/**
 * Indicates whether this time integrity result
 * requires an administrative justification.
 *
 * CURRENT BEHAVIOR:
 * -----------------
 * - Time integrity is binary.
 * - Either OK (no justification),
 * - Or BLOCKED/TAMPERED (attendance forbidden).
 *
 * This function exists for:
 * - architectural symmetry with location integrity,
 * - future extensibility,
 * - and clean aggregation logic in AttendanceUseCase.
 */
fun TimeIntegrityResult.requiresJustification(): Boolean =
    false