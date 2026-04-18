package com.mahmoud.attendify.attendance

import com.mahmoud.attendify.matching.MatchDecision


sealed class AttendanceDecision {

    object AttendanceRecorded : AttendanceDecision()

    data class AttendanceRejected(
        val reason: RejectionReason
    ) : AttendanceDecision()
}
