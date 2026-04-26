package com.mahmoud.attendify.system.location

/**
 * LocationDecision
 *
 * Represents the final administrative decision
 * produced by LocationIntegrityGuard.
 *
 * IMPORTANT:
 * - This is NOT a technical result.
 * - This is a POLICY result.
 * - It explains HOW location affected attendance,
 *   not WHETHER attendance is allowed.
 */
enum class LocationDecision {

    /**
     * Location checks passed cleanly.
     * No anomalies detected.
     * No justification required.
     */
    ALLOWED,

    /**
     * Location checks did not pass ideally
     * (timeout, weak GPS, mock signal, teleport).
     *
     * Attendance is allowed,
     * but requires justification and full evidence.
     */
    ALLOWED_WITH_EVIDENCE
}
