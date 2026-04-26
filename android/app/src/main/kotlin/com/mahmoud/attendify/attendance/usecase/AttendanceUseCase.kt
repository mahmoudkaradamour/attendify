package com.mahmoud.attendify.attendance.usecase

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.system.location.LocationIntegrityGuard
import com.mahmoud.attendify.system.location.LocationIntegrityResult
import com.mahmoud.attendify.system.location.zones.LocationZonesPolicy
import com.mahmoud.attendify.system.location.zones.ZoneEvaluator
import com.mahmoud.attendify.system.location.zones.ZonePolicy
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
 * ============================================================================
 * ROLE (FINAL AUTHORITY):
 * ============================================================================
 * The SINGLE decision-making authority for attendance acceptance.
 *
 * This class:
 *  ✅ Combines all validated evidence
 *  ✅ Applies administrative policies
 *  ✅ Produces a legally defensible AttendanceResult
 *
 * This class NEVER:
 *  ❌ Talks to UI / Flutter
 *  ❌ Talks to sensors or Android framework directly
 *  ❌ Performs biometric verification
 *
 * ============================================================================
 * DECISION FLOW (LOCKED DESIGN):
 * ============================================================================
 * 1) Time Integrity        (security / anchor-based)
 * 2) Working Time Policy  (administrative / HR)
 * 3) Location Integrity   (technical / anti-spoof)
 * 4) Zone Policy          (administrative / contextual)
 * 5) Evidence aggregation
 *
 * ANY failure aborts immediately (Fail-Secure).
 */
class AttendanceUseCase(

    /* ----------------------------------------------------------------
     * TIME (SECURITY CRITICAL)
     * ---------------------------------------------------------------- */
    private val timeIntegrityGuard: TimeIntegrityGuard,
    private val timeProofFactory: AttendanceTimeProofFactory,
    private val timeAnchorStorage: TimeAnchorStorage,

    /* ----------------------------------------------------------------
     * LOCATION (TECHNICAL TRUST)
     * ---------------------------------------------------------------- */
    private val locationIntegrityGuard: LocationIntegrityGuard,

    /* ----------------------------------------------------------------
     * LOCATION ZONES (ADMINISTRATIVE POLICY)
     * ---------------------------------------------------------------- */
    private val zonesPolicy: LocationZonesPolicy,

    /* ----------------------------------------------------------------
     * WORKING TIME (OPTIONAL ADMINISTRATIVE LAYER)
     * ---------------------------------------------------------------- */
    private val workingTimeEvaluator: WorkingTimeEvaluator?
) {

    /**
     * attempt
     *
     * Executes the FULL administrative attendance decision
     * AFTER biometric verification has succeeded.
     *
     * IMPORTANT:
     * ----------
     * This function assumes identity is already verified.
     * It focuses ONLY on contextual legitimacy.
     *
     * @param action            CHECK_IN or CHECK_OUT
     * @param livenessExecuted  Whether liveness was executed (for audit)
     */
    fun attempt(
        action: AttendanceAction,
        livenessExecuted: Boolean
    ): AttendanceResult {

        /* ================================================================
         * 1️⃣ TIME INTEGRITY (ABSOLUTE SECURITY GATE)
         * ================================================================
         * No trustworthy time → nothing else matters.
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

        /* ================================================================
         * 2️⃣ TRUSTED TIME SNAPSHOT
         * ================================================================
         * This is now safe because integrity passed.
         */
        val timeSnapshot = TimeSource.snapshot()

        /* ================================================================
         * 3️⃣ WORKING TIME POLICY (HR RULES)
         * ================================================================
         * Determines whether attendance is allowed
         * based on shifts, holidays, etc.
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

        /* ================================================================
         * 4️⃣ CREATE TIME PROOF (FORENSIC EVIDENCE)
         * ================================================================
         */
        val timeProof = timeProofFactory.create(
            currentSnapshot = timeSnapshot,
            integrityResult = timeResult
        )

        /**
         * The anchor is updated ONLY AFTER a successful integrity pass.
         * This prevents attacker-controlled anchors.
         */
        timeAnchorStorage.saveAnchor(timeSnapshot)

        /* ================================================================
         * 5️⃣ LOCATION INTEGRITY (ANTI-SPOOF)
         * ================================================================
         * Verifies:
         * - freshness
         * - no mock
         * - no teleportation
         */
        val locationResult = locationIntegrityGuard.evaluate()

        if (locationResult is LocationIntegrityResult.Blocked) {
            return AttendanceResult.Blocked(
                reason =
                    "Location integrity validation failed: ${locationResult.reason}"
            )
        }

        val locationEvidence =
            (locationResult as LocationIntegrityResult.Allowed).evidence

        /* ================================================================
         * 6️⃣ ZONE POLICY (ADMINISTRATIVE CONTEXT)
         * ================================================================
         * Interprets the trusted location in an HR context.
         */

        /* ================================================================
 * 6️⃣ ZONE POLICY (ADMINISTRATIVE CONTEXT)
 * ================================================================
 * At this point, location integrity has already passed.
 * Therefore, required coordinates MUST be present.
 *
 * If they are missing, this is a system inconsistency,
 * not a user error.
 */
        val latitude = locationEvidence.latitude
            ?: return AttendanceResult.Blocked(
                reason = "Location evidence missing latitude after integrity approval"
            )

        val longitude = locationEvidence.longitude
            ?: return AttendanceResult.Blocked(
                reason = "Location evidence missing longitude after integrity approval"
            )

        val accuracyMeters = locationEvidence.accuracyMeters
            ?.toDouble()
            ?: return AttendanceResult.Blocked(
                reason = "Location evidence missing accuracy after integrity approval"
            )

        val zoneDecision =
            ZoneEvaluator.evaluate(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                zonesPolicy = zonesPolicy
            )


        when (zoneDecision.policy) {
            ZonePolicy.BLOCK -> {
                return AttendanceResult.Blocked(
                    reason = "Attendance blocked by location zone policy"
                )
            }

            else -> {
                // ALLOW or ALLOW_WITH_JUSTIFICATION handled later
            }
        }

        /* ================================================================
         * 7️⃣ JUSTIFICATION & EVIDENCE AGGREGATION
         * ================================================================
         */
        val justificationRequired =
            locationEvidence.justificationRequired ||
                    zoneDecision.policy == ZonePolicy.ALLOW_WITH_JUSTIFICATION ||
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

        /* ================================================================
         * ✅ ACCEPTED (FINAL, AUDIT-SAFE RESULT)
         * ================================================================
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
