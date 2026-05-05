package com.mahmoud.attendify.orchestration

import java.util.concurrent.atomic.AtomicBoolean

/* ============================================================================
 * DOMAIN
 * ========================================================================== */
import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase

/* ============================================================================
 * CAMERA / IMAGE QUALITY
 * ========================================================================== */
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus

/* ============================================================================
 * FACE PIPELINE
 * ========================================================================== */
import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

/* ============================================================================
 * LIVENESS (OPTIONAL)
 * ========================================================================== */
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* ============================================================================
 * MATCHING
 * ========================================================================== */
import com.mahmoud.attendify.matching.FaceMatchingOrchestrator
import com.mahmoud.attendify.matching.MatchDecision

/* ============================================================================
 * ATOMIC CONTEXT / SNAPSHOT
 * ========================================================================== */
import com.mahmoud.attendify.orchestration.context.AttendanceAtomicContext
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot

/* ============================================================================
 * SECURITY
 * ========================================================================== */
import com.mahmoud.attendify.security.ReplayProtectionGuard
import com.mahmoud.attendify.security.SecureEmployeeSession

/* ============================================================================
 * FORENSICS (PHASE 3.4 + 3.5)
 * ========================================================================== */
import com.mahmoud.attendify.forensics.ForensicAuditTrailWriter
import com.mahmoud.attendify.forensics.EvidenceNormalizer

/**
 * ============================================================================
 * AttendanceRuntimeOrchestrator
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * Single trusted runtime coordinator for ONE attendance attempt.
 *
 * Responsibilities:
 *  ✅ Orchestrates execution flow
 *  ✅ Enforces security gates
 *  ✅ Consumes signed, atomic physical snapshots
 *  ✅ Triggers forensic persistence AFTER final decision
 *
 * Explicit Non‑Responsibilities:
 *  ❌ Capture physical reality
 *  ❌ Validate time or location
 *  ❌ Normalize evidence
 *  ❌ Make final administrative decisions
 *
 * FINAL DECISION AUTHORITY:
 * ----------------------------------------------------------------------------
 * AttendanceUseCase
 *
 * ============================================================================
 * HIGH‑LEVEL FORENSIC FLOW (POST PHASE 3.5)
 * ============================================================================
 *
 * SignedPhysicalRealitySnapshot
 *             │
 *             ▼
 * AttendanceRuntimeOrchestrator
 *             │
 *             ▼
 * AttendanceResult (FINAL)
 *             │
 *             ▼
 * EvidenceNormalizer
 *             │
 *             ▼
 * NormalizedForensicEvidence
 *             │
 *             ▼
 * ForensicAuditTrailWriter
 *
 * ============================================================================
 * SECURITY & FORENSIC GUARANTEES
 * ============================================================================
 *
 * ✅ Atomic Reality (no TOCTOU)
 * ✅ Replay Protection (nonce‑based)
 * ✅ Tamper Evidence (signed snapshots)
 * ✅ Privacy‑by‑Design (normalized evidence)
 * ✅ Append‑only forensic ledger
 * ✅ Single‑flight execution
 *
 * Any violation → AttendanceResult.Blocked
 */
class AttendanceRuntimeOrchestrator(

    /* ========================================================================
     * ATOMIC REALITY PROVIDER (SINGLE AUTHORITY)
     * ======================================================================== */
    private val physicalRealityBuilder: PhysicalRealityBuilder,

    /* ========================================================================
     * FACE PIPELINE
     * ======================================================================== */
    private val faceDetector: FaceDetector,
    private val faceNet: MobileFaceNet,

    /* ========================================================================
     * LIVENESS (OPTIONAL)
     * ======================================================================== */
    private val livenessOrchestrator: LivenessOrchestrator?,
    private val facialMetricsEngine: FacialMetricsEngine,

    /* ========================================================================
     * FACE MATCHING (SINGLE ENTRY POINT)
     * ======================================================================== */
    private val faceMatchingOrchestrator: FaceMatchingOrchestrator,

    /* ========================================================================
     * FINAL ADMINISTRATIVE AUTHORITY
     * ======================================================================== */
    private val attendanceUseCase: AttendanceUseCase,

    /* ========================================================================
     * FORENSIC AUDIT TRAIL (PHASE 3.4 + 3.5)
     * ======================================================================== */
    private val auditTrailWriter: ForensicAuditTrailWriter
) {

    /**
     * Strict single‑flight guard.
     *
     * Prevents:
     * - Parallel attendance attempts
     * - Evidence interleaving
     * - Context contamination
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * =========================================================================
     * attemptAttendance
     * =========================================================================
     *
     * Executes exactly ONE attendance attempt.
     *
     * Trust Model:
     * ------------------------------------------------------------------------
     * ✅ Action comes from UI (intent only)
     * ✅ Identity resolved natively
     * ❌ No user‑supplied evidence
     */
    suspend fun attemptAttendance(
        action: AttendanceAction
    ): AttendanceResult {

        /* ====================================================================
         * CONCURRENCY GATE
         * ==================================================================== */
        if (!isProcessing.compareAndSet(false, true)) {
            return AttendanceResult.Blocked(
                reason = "Attendance already in progress"
            )
        }

        val employeeId =
            SecureEmployeeSession.requireEmployeeId()

        try {

            /* ================================================================
             * STEP 0 — SIGNED, SINGLE‑USE PHYSICAL REALITY
             * ================================================================ */
            val signedSnapshot: SignedPhysicalRealitySnapshot =
                physicalRealityBuilder
                    .buildSignedOrFail(timeoutMs = 2000)
                    .getOrElse {
                        return AttendanceResult.Blocked(
                            reason = it.message ?: "Physical capture failed"
                        )
                    }

            /* ================================================================
             * REPLAY ATTACK DEFENSE
             * ================================================================ */
            if (
                !ReplayProtectionGuard
                    .registerOrReject(signedSnapshot.snapshotId)
            ) {
                return AttendanceResult.Blocked(
                    reason = "Replay attack detected"
                )
            }

            /* ================================================================
             * BUILD ATOMIC CONTEXT
             * ================================================================ */
            val atomicContext =
                AttendanceAtomicContext(
                    frozenFrame = signedSnapshot.payload.frozenFrame,
                    timeSnapshot = signedSnapshot.payload.timeSnapshot,
                    locationEvidence = signedSnapshot.payload.locationEvidence
                )

            /* ================================================================
             * IMAGE QUALITY
             * ================================================================ */
            if (
                ImageQualityChecker.checkFrame(
                    atomicContext.frozenFrame
                ) != SystemStatus.OK
            ) {
                return AttendanceResult.Blocked(
                    reason = "Image quality check failed"
                )
            }

            /* ================================================================
             * FACE DETECTION
             * ================================================================ */
            val detection =
                faceDetector.detectBestFace(
                    atomicContext.frozenFrame
                ) ?: return AttendanceResult.Blocked(
                    reason = "No face detected"
                )

            /* ================================================================
             * FACE CROP & NORMALIZATION
             * ================================================================ */
            val faceBitmap =
                FaceCropper.cropAndResize(
                    atomicContext.frozenFrame,
                    detection.box,
                    112
                ) ?: return AttendanceResult.Blocked(
                    reason = "Face crop failed"
                )

            /* ================================================================
             * OPTIONAL LIVENESS
             * ================================================================ */
            val livenessExecuted =
                livenessOrchestrator != null

            if (livenessOrchestrator != null) {

                val metricsFrame =
                    facialMetricsEngine
                        .computeFrameFromBitmap(faceBitmap)

                livenessOrchestrator.onFrame(metricsFrame)

                if (
                    livenessOrchestrator.evaluate()
                            is LivenessResult.SpoofDetected
                ) {
                    return AttendanceResult.Blocked(
                        reason = "Liveness check failed"
                    )
                }
            }

            /* ================================================================
             * FACE MATCHING
             * ================================================================ */
            if (
                faceMatchingOrchestrator.performMatch(
                    liveEmbedding = faceNet.getEmbedding(faceBitmap),
                    employeeId = employeeId,
                    groupThreshold = null
                ) !is MatchDecision.MatchSuccess
            ) {
                return AttendanceResult.Blocked(
                    reason = "Face matching failed"
                )
            }

            /* ================================================================
             * FINAL ADMINISTRATIVE DECISION
             * ================================================================ */
            val result =
                attendanceUseCase.attempt(
                    action = action,
                    atomicContext = atomicContext,
                    livenessExecuted = livenessExecuted
                )

            /* ================================================================
             * PHASE 3.5 — NORMALIZATION & FORENSIC PERSISTENCE
             * ================================================================ */
            val normalizedEvidence =
                EvidenceNormalizer.normalize(
                    snapshot = signedSnapshot,
                    result = result
                )

            auditTrailWriter.append(
                evidence = normalizedEvidence
            )

            return result

        } finally {

            /* ================================================================
             * RELEASE SINGLE‑FLIGHT GUARD
             * ================================================================ */
            isProcessing.set(false)
        }
    }
}