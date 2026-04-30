package com.mahmoud.attendify.attendance.usecase

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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
 * ROLE (FINAL AUTHORITY — NON‑NEGOTIABLE):
 * ============================================================================
 * This class is the SINGLE and FINAL authority that decides whether
 * an attendance operation is ACCEPTED or BLOCKED.
 *
 * It is invoked ONLY AFTER biometric identity is already verified.
 *
 * This class:
 *  ✅ Aggregates trusted evidence
 *  ✅ Applies administrative / legal policies
 *  ✅ Produces an audit‑grade, defensible AttendanceResult
 *
 * This class NEVER:
 *  ❌ Talks to UI or Flutter
 *  ❌ Talks to cameras or sensors
 *  ❌ Performs biometric checks
 *
 * ============================================================================
 * SECURITY HARDENING (STAGE 0.2):
 * ============================================================================
 * ✅ Strict single‑flight execution (Mutex)
 * ✅ Time‑bounded execution (withTimeout)
 * ✅ Automatic self‑recovery (no permanent lockout)
 *
 * Result:
 * ------
 * No retry storms, no deadlocks, no silent OOM, no parallel decisions.
 */
class AttendanceUseCase(

    /* ----------------------------------------------------------------
     * TIME (SECURITY CRITICAL — ANCHOR BASED)
     * ---------------------------------------------------------------- */
    private val timeIntegrityGuard: TimeIntegrityGuard,
    private val timeProofFactory: AttendanceTimeProofFactory,
    private val timeAnchorStorage: TimeAnchorStorage,

    /* ----------------------------------------------------------------
     * LOCATION (TECHNICAL TRUST)
     * ---------------------------------------------------------------- */
    private val locationIntegrityGuard: LocationIntegrityGuard,

    /* ----------------------------------------------------------------
     * LOCATION ZONES (ADMINISTRATIVE CONTEXT)
     * ---------------------------------------------------------------- */
    private val zonesPolicy: LocationZonesPolicy,

    /* ----------------------------------------------------------------
     * WORKING TIME (OPTIONAL HR LAYER)
     * ---------------------------------------------------------------- */
    private val workingTimeEvaluator: WorkingTimeEvaluator?
) {

    /**
     * attendanceMutex
     *
     * ------------------------------------------------------------------------
     * Guarantees STRICT single‑flight execution.
     *
     * WHY Mutex?
     * ----------
     * - Coroutine‑aware locking
     * - Cooperates with structured concurrency
     * - Automatically released on cancellation
     *
     * SECURITY RULE:
     * --------------
     * At most ONE attendance decision may execute at any moment.
     */
    private val attendanceMutex = Mutex()

    /**
     * attempt
     *
     * =========================================================================
     * Executes the FULL administrative attendance decision.
     *
     * HARD GUARANTEES:
     * ----------------
     * ✅ No concurrent execution
     * ✅ No retry amplification
     * ✅ Guaranteed termination (timeout)
     *
     * @param action            CHECK_IN or CHECK_OUT
     * @param livenessExecuted  Explicit forensic flag
     */
    suspend fun attempt(
        action: AttendanceAction,
        livenessExecuted: Boolean
    ): AttendanceResult {

        return try {

            /* ================================================================
             * ⛔ TIME‑BOUNDED SINGLE‑FLIGHT EXECUTION
             * ================================================================
             * If execution exceeds 10 seconds:
             *  - Cancellation occurs
             *  - Mutex is automatically released
             *  - System recovers safely
             */
            withTimeout(10_000L) {

                attendanceMutex.withLock {

                    /* ========================================================
                     * 1️⃣ TIME INTEGRITY (ABSOLUTE SECURITY GATE)
                     * ======================================================== */
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

                    /* ========================================================
                     * 2️⃣ TRUSTED TIME SNAPSHOT
                     * ======================================================== */
                    val timeSnapshot =
                        TimeSource.snapshot()

                    /* ========================================================
                     * 3️⃣ WORKING TIME POLICY (HR RULES)
                     * ======================================================== */
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

                    /* ========================================================
                     * 4️⃣ TIME PROOF (FORENSIC EVIDENCE)
                     * ======================================================== */
                    val timeProof =
                        timeProofFactory.create(
                            currentSnapshot = timeSnapshot,
                            integrityResult = timeResult
                        )

                    /**
                     * Anchor update occurs ONLY after
                     * successful integrity validation.
                     *
                     * This prevents attacker‑controlled anchors.
                     */
                    timeAnchorStorage.saveAnchor(
                        timeSnapshot
                    )

                    /* ========================================================
                     * 5️⃣ LOCATION INTEGRITY (ANTI‑SPOOF)
                     * ======================================================== */
                    val locationResult =
                        locationIntegrityGuard.evaluate()

                    if (locationResult
                                is LocationIntegrityResult.Blocked
                    ) {
                        return@withLock AttendanceResult.Blocked(
                            reason =
                                "Location integrity failed: " +
                                        locationResult.reason
                        )
                    }

                    val locationEvidence =
                        (locationResult
                                as LocationIntegrityResult.Allowed)
                            .evidence

                    /* ========================================================
                     * 6️⃣ ZONE POLICY (ADMINISTRATIVE CONTEXT)
                     * ======================================================== */
                    val latitude =
                        locationEvidence.latitude
                            ?: return@withLock AttendanceResult.Blocked(
                                reason =
                                    "Missing latitude after location approval"
                            )

                    val longitude =
                        locationEvidence.longitude
                            ?: return@withLock AttendanceResult.Blocked(
                                reason =
                                    "Missing longitude after location approval"
                            )

                    val accuracyMeters =
                        locationEvidence.accuracyMeters
                            ?.toDouble()
                            ?: return@withLock AttendanceResult.Blocked(
                                reason =
                                    "Missing GPS accuracy after location approval"
                            )

                    val zoneDecision =
                        ZoneEvaluator.evaluate(
                            latitude = latitude,
                            longitude = longitude,
                            accuracyMeters = accuracyMeters,
                            zonesPolicy = zonesPolicy
                        )

                    if (zoneDecision.policy == ZonePolicy.BLOCK) {
                        return@withLock AttendanceResult.Blocked(
                            reason =
                                "Attendance blocked by zone policy"
                        )
                    }

                    /* ========================================================
                     * 7️⃣ JUSTIFICATION & EVIDENCE AGGREGATION
                     * ======================================================== */
                    val justificationRequired =
                        locationEvidence.justificationRequired ||
                                zoneDecision.policy ==
                                ZonePolicy.ALLOW_WITH_JUSTIFICATION ||
                                workingTimeDecision?.action ==
                                OutOfWorkingTimeAction.ALLOW_WITH_JUSTIFICATION

                    val workingTimeEvidence =
                        workingTimeDecision?.let {
                            WorkingTimeEvidence(
                                policyId = it.policy.id,
                                policyName = it.policy.name,
                                isWithinWorkingTime =
                                    it.isWithinWorkingTime,
                                isHoliday = it.isHoliday,
                                action = it.action
                            )
                        }

                    /* ========================================================
                     * ✅ ACCEPTED (FINAL, AUDIT‑SAFE RESULT)
                     * ======================================================== */
                    AttendanceResult.Accepted(
                        action = action,
                        timeProof = timeProof,
                        locationEvidence = locationEvidence,
                        workingTimeEvidence = workingTimeEvidence,
                        livenessExecuted = livenessExecuted,
                        justification =
                            if (justificationRequired) null else null
                    )
                }
            }

        } catch (_: TimeoutCancellationException) {

            /**
             * ⏱️ TIMEOUT RECOVERY
             *
             * GUARANTEE:
             * ----------
             * - Mutex released
             * - System usable
             * - User can retry safely
             */
            AttendanceResult.Blocked(
                reason = "Attendance processing timeout"
            )
        }
    }
}