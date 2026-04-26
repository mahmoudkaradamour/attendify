package com.mahmoud.attendify.attendance.usecase

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.domain.Justification
import com.mahmoud.attendify.system.location.LocationIntegrityGuard
import com.mahmoud.attendify.system.location.LocationIntegrityResult
import com.mahmoud.attendify.system.time.AttendanceTimeProofFactory
import com.mahmoud.attendify.system.time.TimeAnchorStorage
import com.mahmoud.attendify.system.time.TimeIntegrityGuard
import com.mahmoud.attendify.system.time.TimeIntegrityResult
import com.mahmoud.attendify.system.time.TimeSource
import com.mahmoud.attendify.system.time.toInstant
import com.mahmoud.attendify.system.time.working.OutOfWorkingTimeAction
import com.mahmoud.attendify.system.time.working.WorkingTimeEvaluator
import com.mahmoud.attendify.system.time.working.WorkingTimeEvidence

/**
 * AttendanceUseCase
 *
 * Central orchestrator for attendance operations.
 *
 * DECISION FLOW:
 * 1) Time Integrity       (security)
 * 2) Working Time Policy  (administrative)
 * 3) Location Integrity  (context)
 * 4) Aggregate justification
 */
class AttendanceUseCase(

    /* TIME */
    private val timeIntegrityGuard: TimeIntegrityGuard,
    private val timeProofFactory: AttendanceTimeProofFactory,
    private val timeAnchorStorage: TimeAnchorStorage,

    /* LOCATION */
    private val locationIntegrityGuard: LocationIntegrityGuard,

    /* WORKING TIME (OPTIONAL) */
    private val workingTimeEvaluator: WorkingTimeEvaluator?
) {

    fun attempt(
        action: AttendanceAction
    ): AttendanceResult {

        /* 1️⃣ TIME INTEGRITY */
        val timeResult = timeIntegrityGuard.validate()

        if (
            timeResult is TimeIntegrityResult.Blocked ||
            timeResult is TimeIntegrityResult.Tampered
        ) {
            return AttendanceResult.Blocked(
                reason = "Time integrity validation failed",
                timeReason = timeResult
            )
        }

        /* 2️⃣ TRUSTED TIME SNAPSHOT */
        val timeSnapshot = TimeSource.snapshot()

        /* 3️⃣ WORKING TIME POLICY */
        val workingTimeDecision =
            workingTimeEvaluator?.evaluate(timeSnapshot.toInstant())

        if (
            workingTimeDecision != null &&
            workingTimeDecision.action == OutOfWorkingTimeAction.BLOCK
        ) {
            return AttendanceResult.Blocked(
                reason = "Attendance blocked by working time policy"
            )
        }

        /* 4️⃣ CREATE TIME PROOF */
        val timeProof = timeProofFactory.create(
            currentSnapshot = timeSnapshot,
            integrityResult = timeResult
        )

        timeAnchorStorage.saveAnchor(timeSnapshot)

        /* 5️⃣ LOCATION INTEGRITY */
        val locationResult = locationIntegrityGuard.evaluate()

        if (locationResult is LocationIntegrityResult.Blocked) {
            return AttendanceResult.Blocked(
                reason = "Location integrity validation failed: ${locationResult.reason}"
            )
        }

        val locationEvidence =
            (locationResult as LocationIntegrityResult.Allowed).evidence

        /* 6️⃣ JUSTIFICATION AGGREGATION */
        val justificationRequired =
            locationEvidence.justificationRequired ||
                    (workingTimeDecision?.action ==
                            OutOfWorkingTimeAction.ALLOW_WITH_JUSTIFICATION)

        val workingTimeEvidence =
            workingTimeDecision?.let {
                WorkingTimeEvidence(
                    policyId = it.policy.id,
                    policyName = it.policy.name,
                    isWithinWorkingTime = it.isWithinWorkingTime,
                    isHoliday = it.isHoliday,
                    action = it.action
                )
            }

        /* 7️⃣ ACCEPTED (PHASE‑1) */
        return AttendanceResult.Accepted(
            action = action,
            timeProof = timeProof,
            locationEvidence = locationEvidence,
            workingTimeEvidence = workingTimeEvidence,
            justification = if (justificationRequired) null else null
        )
    }

    fun finalizeWithJustification(
        accepted: AttendanceResult.Accepted,
        justification: Justification
    ): AttendanceResult.Accepted =
        accepted.copy(justification = justification)
}