package com.mahmoud.attendify.attendance.domain

import com.mahmoud.attendify.system.time.AttendanceTimeProof
import com.mahmoud.attendify.system.time.TimeIntegrityResult

sealed class AttendanceResult {

    data class Success(
        val action: AttendanceAction,
        val proof: AttendanceTimeProof
    ) : AttendanceResult()

    data class Blocked(
        val reason: TimeIntegrityResult
    ) : AttendanceResult()
}