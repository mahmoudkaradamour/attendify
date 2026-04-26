package com.mahmoud.attendify.attendance.usecase

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
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
 * Central decision unit for attendance operations.
 *
 * DECISION FLOW:
 * --------------
 * 1) Time Integrity        (security)
 * 2) Working Time Policy  (administrative)
 * 3) Location Integrity   (contextual)
 * 4) Evidence aggregation
 *
 * NOTE:
 * -----
 * This class does NOT know about UI or Flutter.
 * It receives all required context explicitly.
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

    /**
     * attempt
     *
     * Executes the full administrative attendance decision
     * after biometric verification has succeeded.
     *
     * @param action            CHECK_IN or CHECK_OUT
     * @param livenessExecuted  Whether liveness was executed during this attempt
     */
    fun attempt(
        action: AttendanceAction,
        livenessExecuted: Boolean
    ): AttendanceResult {

        /* ==================================================
         * 1️⃣ TIME INTEGRITY
         * ==================================================
         */
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

        /* ==================================================
         * 2️⃣ TRUSTED TIME SNAPSHOT
         * ==================================================
         */
        val timeSnapshot = TimeSource.snapshot()

        /* ==================================================
         * 3️⃣ WORKING TIME POLICY
         * ==================================================
         */
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

        /* ==================================================
         * 4️⃣ CREATE TIME PROOF
         * ==================================================
         */
        val timeProof = timeProofFactory.create(
            currentSnapshot = timeSnapshot,
            integrityResult = timeResult
        )

        timeAnchorStorage.saveAnchor(timeSnapshot)

        /* ==================================================
         * 5️⃣ LOCATION INTEGRITY
         * ==================================================
         */
        val locationResult = locationIntegrityGuard.evaluate()

        if (locationResult is LocationIntegrityResult.Blocked) {
            return AttendanceResult.Blocked(
                reason = "Location integrity validation failed: ${locationResult.reason}"
            )
        }

        val locationEvidence =
            (locationResult as LocationIntegrityResult.Allowed).evidence

        /* ==================================================
         * 6️⃣ JUSTIFICATION & EVIDENCE AGGREGATION
         * ==================================================
         */
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

        /* ==================================================
         * 7️⃣ ACCEPTED (PHASE‑1 FINAL)
         * ==================================================
         */
        return AttendanceResult.Accepted(
            action = action,
            timeProof = timeProof,
            locationEvidence = locationEvidence,
            workingTimeEvidence = workingTimeEvidence,
            livenessExecuted = livenessExecuted,
            justification = if (justificationRequired) null else null
        )
    }
}