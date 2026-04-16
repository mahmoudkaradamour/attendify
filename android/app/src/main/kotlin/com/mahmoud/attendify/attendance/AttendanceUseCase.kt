package com.mahmoud.attendify.attendance

import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.matching.MatchDecision

class AttendanceUseCase(
    private val faceMatchingUseCase: FaceMatchingUseCase
) {

    fun attemptAttendance(
        liveEmbedding: FloatArray,
        employeeId: String
    ): AttendanceDecision {

        val matchDecision =
            faceMatchingUseCase.matchNow(
                liveEmbedding = liveEmbedding,
                employeeId = employeeId
            )

        return when (matchDecision) {
            is MatchDecision.MatchSuccess ->
                AttendanceDecision.AttendanceRecorded

            else ->
                AttendanceDecision.AttendanceRejected(matchDecision)
        }
    }
}