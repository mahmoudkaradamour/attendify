package com.mahmoud.attendify.attendance

import android.graphics.Bitmap
import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.matching.MatchDecision
import com.mahmoud.attendify.fas.runtime.FASOrchestrator
import com.mahmoud.attendify.fas.runtime.FASDecision
import com.mahmoud.attendify.fas.policy.FASPolicy
import com.mahmoud.attendify.fas.policy.PolicyResolution
import com.mahmoud.attendify.audit.AuditLogger

class AttendanceUseCase(
    private val faceMatchingUseCase: FaceMatchingUseCase,
    private val fasOrchestrator: FASOrchestrator
) {

    fun attemptAttendance(
        faceBitmap: Bitmap,
        liveEmbedding: FloatArray,
        employeeId: String,

        // هذه السياسات ستأتي لاحقًا من Flutter / DB
        employeePolicy: FASPolicy?,
        groupPolicy: FASPolicy?,
        orgPolicy: FASPolicy?
    ): AttendanceDecision {

        // ✅ 1) حل السياسة النهائية
        val resolvedPolicy = PolicyResolution.resolve(
            employeePolicy = employeePolicy,
            groupPolicy = groupPolicy,
            orgPolicy = orgPolicy
        )

        // ✅ 2) تنفيذ FAS قبل أي مطابقة
        val fasDecision = fasOrchestrator.evaluate(
            face = faceBitmap,
            policy = resolvedPolicy
        )

        when (fasDecision) {
            is FASDecision.Blocked -> {
                AuditLogger.log(
                    event = "FAS_BLOCKED",
                    details = "Employee=$employeeId | Reason=${fasDecision.reason}"
                )

                return AttendanceDecision.AttendanceRejected(
                    RejectionReason.FASRejected(fasDecision.reason)
                )

            }

            is FASDecision.Skipped -> {
                AuditLogger.log(
                    event = "FAS_SKIPPED",
                    details = "Employee=$employeeId"
                )
            }

            is FASDecision.Passed -> {
                // لا شيء – نكمل
            }
        }

        // ✅ 3) المطابقة الوجهية
        val matchDecision =
            faceMatchingUseCase.matchNow(
                liveEmbedding = liveEmbedding,
                employeeId = employeeId
            )

        return when (matchDecision) {

            is MatchDecision.MatchSuccess ->
                AttendanceDecision.AttendanceRecorded

            else ->

                AttendanceDecision.AttendanceRejected(
                    RejectionReason.FaceMismatch(matchDecision)
                )

        }
    }
}