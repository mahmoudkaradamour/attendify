package com.mahmoud.attendify.attendance.domain

import com.mahmoud.attendify.system.location.LocationEvidence
import com.mahmoud.attendify.system.time.AttendanceTimeProof
import com.mahmoud.attendify.system.time.TimeIntegrityResult
import com.mahmoud.attendify.system.time.working.WorkingTimeEvidence

/**
 * AttendanceResult
 *
 * Final outcome of an attendance attempt.
 * No partial success is allowed.
 */
sealed class AttendanceResult {

    /**
     * Attendance accepted.
     *
     * May or may not require justification
     * depending on applied policy.
     */
    data class Accepted(
        val action: AttendanceAction,
        val timeProof: AttendanceTimeProof,
        val locationEvidence: LocationEvidence?,
        val workingTimeEvidence: WorkingTimeEvidence?,
        val livenessExecuted: Boolean, // ✅ NEW
        val justification: Justification? = null
    ) : AttendanceResult()

    /**
     * Attendance blocked.
     */
    data class Blocked(
        val reason: String,
        val timeReason: TimeIntegrityResult? = null
    ) : AttendanceResult()



}