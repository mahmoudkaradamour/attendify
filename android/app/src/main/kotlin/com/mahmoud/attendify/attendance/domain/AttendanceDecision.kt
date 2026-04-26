package com.mahmoud.attendify.attendance.domain

import com.mahmoud.attendify.system.location.LocationEvidence
import com.mahmoud.attendify.system.time.AttendanceTimeProof

/**
 * Unified, forensic-grade attendance decision.
 */
data class AttendanceDecision(
    val action: AttendanceAction,
    val timeProof: AttendanceTimeProof,
    val locationEvidence: LocationEvidence?
)
