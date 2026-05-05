package com.mahmoud.attendify.attendance.usecase

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult

import com.mahmoud.attendify.orchestration.context.AttendanceAtomicContext

import com.mahmoud.attendify.system.location.zones.LocationZonesPolicy
import com.mahmoud.attendify.system.location.zones.ZoneEvaluator
import com.mahmoud.attendify.system.location.zones.ZonePolicy

import com.mahmoud.attendify.system.time.AttendanceTimeProofFactory
import com.mahmoud.attendify.system.time.TimeAnchorStorage
import com.mahmoud.attendify.system.time.TimeIntegrityGuard
import com.mahmoud.attendify.system.time.TimeIntegrityResult
import com.mahmoud.attendify.system.time.toInstant

import com.mahmoud.attendify.system.time.working.OutOfWorkingTimeAction
import com.mahmoud.attendify.system.time.working.WorkingTimeEvaluator
import com.mahmoud.attendify.system.time.working.WorkingTimeEvidence

/**
 * ============================================================================
 * AttendanceUseCase
 * ============================================================================
 *
 * ROLE (FINAL AUTHORITY — PHASE 2.3):
 * ----------------------------------------------------------------------------
 * The SINGLE, FINAL, NON‑OVERRIDABLE authority that decides whether an
 * attendance attempt is ACCEPTED or BLOCKED.
 *
 * ABSOLUTE RULE (NEW, PHASE‑2.3):
 * ----------------------------------------------------------------------------
 * ❌ No AttendanceResult may be produced
 * ❌ No decision may be taken
 *
 * unless it is derived from a FULLY‑BOUND AttendanceAtomicContext.
 *
 * ----------------------------------------------------------------------------
 * POSITION IN ARCHITECTURE:
 * ----------------------------------------------------------------------------
 *
 *   AttendanceRuntimeOrchestrator
 *            │
 *            ▼
 *   AttendanceAtomicContext  (Frame + Time + Location)
 *            │
 *            ▼
 *   AttendanceUseCase  ← ★ FINAL AUTHORITY ★
 *            │
 *            ▼
 *     AttendanceResult (Audit‑grade Evidence)
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS DOES:
 * ----------------------------------------------------------------------------
 * ✅ Applies administrative & legal policies
 * ✅ Evaluates working time & zones
 * ✅ Produces signed, audit‑grade results
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS NO LONGER DOES (PHASE‑2.3):
 * ----------------------------------------------------------------------------
 * ❌ Capture time
 * ❌ Capture location
 * ❌ Re‑evaluate physical signals
 *
 * All physical reality is frozen upstream.
 */
class AttendanceUseCase(

    /* ========================================================================
     * TIME — LEGAL INTEGRITY (VALIDATION ONLY)
     * ======================================================================== */
    private val timeIntegrityGuard: TimeIntegrityGuard,
    private val timeProofFactory: AttendanceTimeProofFactory,
    private val timeAnchorStorage: TimeAnchorStorage,

    /* ========================================================================
     * ZONES POLICY — ADMINISTRATIVE AUTHORITY
     * ======================================================================== */
    private val zonesPolicy: LocationZonesPolicy,

    /* ========================================================================
     * WORKING TIME — HR LAYER (OPTIONAL)
     * ======================================================================== */
    private val workingTimeEvaluator: WorkingTimeEvaluator?
) {

    /**
     * Mutex — enforces STRICT single‑flight decision.
     *
     * This is the FINAL concurrency gate in the system.
     */
    private val attendanceMutex = Mutex()

    /**
     * =========================================================================
     * attempt
     * =========================================================================
     *
     * Executes the FULL administrative attendance decision.
     *
     * INPUT (TRUST MODEL):
     * -------------------------------------------------------------------------
     * action          ← User intent (already validated upstream)
     * atomicContext   ← Frozen physical reality (MANDATORY)
     * livenessExecuted← Fact recorded upstream (no inference)
     */
    suspend fun attempt(
        action: AttendanceAction,
        atomicContext: AttendanceAtomicContext,
        livenessExecuted: Boolean
    ): AttendanceResult {

        /*
         * ====================================================================
         * ⛔ HARD GUARD — ATOMIC CONTEXT MUST BE COMPLETE
         * ====================================================================
         * This is NOT defensive coding.
         * This is a LEGAL / FORENSIC requirement.
         */
//        requireNotNull(atomicContext.locationEvidence) {
//            "AttendanceAtomicContext is incomplete: missing locationEvidence"
//        }

        return try {

            withTimeout(10_000L) {

                attendanceMutex.withLock {

                    /* ============================================================
                     * 1️⃣ TIME INTEGRITY VALIDATION
                     * ============================================================
                     * We VALIDATE time here,
                     * but we do NOT re‑capture it.
                     */
                    val timeResult =
                        timeIntegrityGuard.validate()

                    if (
                        timeResult is TimeIntegrityResult.Blocked ||
                        timeResult is TimeIntegrityResult.Tampered
                    ) {
                        return@withLock AttendanceResult.Blocked(
                            reason = "Time integrity validation failed",
                            timeReason = timeResult
                        )
                    }

                    val timeSnapshot =
                        atomicContext.timeSnapshot

                    /* ============================================================
                     * 2️⃣ WORKING TIME POLICY (HR)
                     * ============================================================ */
                    val workingTimeDecision =
                        workingTimeEvaluator
                            ?.evaluate(timeSnapshot.toInstant())

                    if (
                        workingTimeDecision != null &&
                        workingTimeDecision.action ==
                        OutOfWorkingTimeAction.BLOCK
                    ) {
                        return@withLock AttendanceResult.Blocked(
                            reason =
                                "Attendance blocked by working time policy"
                        )
                    }

                    /* ============================================================
                     * 3️⃣ TIME PROOF — FORENSIC BINDING
                     * ============================================================ */
                    val timeProof =
                        timeProofFactory.create(
                            currentSnapshot = timeSnapshot,
                            integrityResult = timeResult,
                            wasLivenessExecuted = livenessExecuted
                        )

                    timeAnchorStorage.saveAnchor(timeSnapshot)

                    /* ============================================================
                     * 4️⃣ ZONE POLICY — ADMINISTRATIVE DECISION
                     * ============================================================ */
                    val locationEvidence =
                        atomicContext.locationEvidence

                    val zoneDecision =
                        ZoneEvaluator.evaluate(
                            latitude = locationEvidence.latitude
                                ?: return@withLock AttendanceResult.Blocked(
                                    reason =
                                        "Missing latitude in atomic context"
                                ),
                            longitude = locationEvidence.longitude
                                ?: return@withLock AttendanceResult.Blocked(
                                    reason =
                                        "Missing longitude in atomic context"
                                ),
                            accuracyMeters =
                                locationEvidence.accuracyMeters
                                    ?.toDouble()
                                    ?: return@withLock AttendanceResult.Blocked(
                                        reason =
                                            "Missing GPS accuracy in atomic context"
                                    ),
                            zonesPolicy = zonesPolicy
                        )

                    if (zoneDecision.policy == ZonePolicy.BLOCK) {
                        return@withLock AttendanceResult.Blocked(
                            reason =
                                "Attendance blocked by zone policy"
                        )
                    }

                    /* ============================================================
                     * ✅ ACCEPTED — FINAL, AUDIT‑GRADE RESULT
                     * ============================================================ */
                    AttendanceResult.Accepted(
                        action = action,
                        timeProof = timeProof,
                        locationEvidence = locationEvidence,
                        workingTimeEvidence =
                            workingTimeDecision?.let {
                                WorkingTimeEvidence(
                                    policyId = it.policy.id,
                                    policyName = it.policy.name,
                                    isWithinWorkingTime =
                                        it.isWithinWorkingTime,
                                    isHoliday = it.isHoliday,
                                    action = it.action
                                )
                            },
                        livenessExecuted = livenessExecuted,
                        justification = null
                    )
                }
            }

        } catch (_: TimeoutCancellationException) {

            AttendanceResult.Blocked(
                reason = "Attendance processing timeout"
            )
        }
    }
}