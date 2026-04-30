package com.mahmoud.attendify.orchestration

import java.util.concurrent.atomic.AtomicBoolean

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase

import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus

import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.matching.MatchDecision

/**
 * AttendanceRuntimeOrchestrator
 *
 * ============================================================================
 * ROLE (FINAL & SEALED):
 * ============================================================================
 * Coordinates the COMPLETE attendance pipeline using
 * EXACTLY ONE camera frame obtained via CameraManager.
 *
 * This class is PURE ORCHESTRATION:
 *  ✅ No Camera HAL access
 *  ✅ No frame streaming
 *  ✅ No business rules
 *  ✅ No policy decisions
 *
 * All DECISIONS are delegated to:
 *  - LivenessOrchestrator
 *  - FaceMatchingUseCase
 *  - AttendanceUseCase (FINAL AUTHORITY)
 *
 * ============================================================================
 * SECURITY GUARANTEES:
 * ============================================================================
 * Stage 0.1:
 *  ✅ Frame Freeze (single immutable bitmap)
 *  ✅ Camera HAL Circuit Breaker respected
 *
 * Stage 0.2:
 *  ✅ No retry storms (guarded upstream)
 *  ✅ Deterministic sequencing
 *
 * Stage 0.3:
 *  ✅ Proper suspend boundary
 *  ✅ No blocking calls
 *  ✅ Safe coroutine interoperability
 *
 * Any change here MUST be security-reviewed.
 */
class AttendanceRuntimeOrchestrator(

    /* ---------------- CAMERA ---------------- */
    private val cameraManager: CameraManager,

    /* ---------------- FACE ---------------- */
    private val faceDetector: FaceDetector,
    private val faceNet: MobileFaceNet,

    /* -------------- LIVENESS -------------- */
    private val livenessOrchestrator: LivenessOrchestrator?, // Nullable by policy
    private val facialMetricsEngine: FacialMetricsEngine,

    /* --------------- MATCHING ------------- */
    private val faceMatchingUseCase: FaceMatchingUseCase,

    /* ------------- ATTENDANCE ------------- */
    private val attendanceUseCase: AttendanceUseCase
) {

    /**
     * Concurrency guard (fail-fast).
     *
     * WHY AtomicBoolean HERE?
     * -----------------------
     * - Protects Camera + ML from parallel execution
     * - Prevents rapid user button spam
     * - Reduces thermal / memory pressure
     *
     * IMPORTANT:
     * ----------
     * This is an OUTER guard.
     * A SECOND, STRONGER Mutex exists INSIDE AttendanceUseCase (Stage 0.2).
     * The two layers serve different purposes and do NOT conflict.
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * attemptAttendance
     *
     * =========================================================================
     * SINGLE ENTRY POINT
     * =========================================================================
     *
     * Executes a FULL attendance attempt.
     *
     * ❗ This function MUST be suspended (Stage 0.3):
     * ---------------------------------------------
     * - Calls suspend AttendanceUseCase
     * - Performs heavy ML work
     * - Must not block threads
     *
     * IMPORTANT SECURITY RULES:
     * -------------------------
     * - Camera frame is obtained HERE and ONLY HERE
     * - No external Bitmap injection is allowed
     * - This completely closes the Frame Slippage exploit
     *
     * @param action     CHECK_IN / CHECK_OUT
     * @param employeeId Target employee identifier
     */
    suspend fun attemptAttendance(
        action: AttendanceAction,
        employeeId: String
    ): AttendanceResult {

        /* ================================================================
         * 🔒 CONCURRENCY GUARD (ABSOLUTE FIRST)
         * ================================================================ */
        if (!isProcessing.compareAndSet(false, true)) {
            return AttendanceResult.Blocked(
                reason = "Attendance already in progress"
            )
        }

        /* ================================================================
         * 🧊 FRAME FREEZE + HARDWARE CIRCUIT BREAKER
         * ================================================================
         * - Exactly ONE frame
         * - Time-bounded
         * - No camera streaming
         */
        val frameBitmap =
            cameraManager.captureSingleFrame(timeoutMs = 2000)
                ?: run {
                    isProcessing.set(false)
                    return AttendanceResult.Blocked(
                        reason = "Camera hardware not responding"
                    )
                }

        try {

            /* ================================================================
             * 1️⃣ IMAGE QUALITY GATE
             * ================================================================
             * Reject unusable frames EARLY:
             * - Very dark / bright
             * - Too blurry
             * - Corrupted frame
             */
            val imageStatus =
                ImageQualityChecker.checkFrame(frameBitmap)

            if (imageStatus != SystemStatus.OK) {
                return AttendanceResult.Blocked(
                    reason = "Image quality check failed: $imageStatus"
                )
            }

            /* ================================================================
             * 2️⃣ FACE DETECTION (SINGLE FACE POLICY)
             * ================================================================ */
            val detection =
                faceDetector.detectBestFace(frameBitmap)
                    ?: return AttendanceResult.Blocked(
                        reason = "No face detected"
                    )

            /* ================================================================
             * 3️⃣ FACE CROP (SECURITY-AWARE)
             * ================================================================ */
            val faceBitmap =
                FaceCropper.cropAndResize(
                    sourceBitmap = frameBitmap,
                    faceBox = detection.box,
                    targetSize = 112 // MobileFaceNet input size
                ) ?: return AttendanceResult.Blocked(
                    reason = "Face crop failed"
                )

            /* ================================================================
             * 4️⃣ LIVENESS (OPTIONAL, BUT AUDITED)
             * ================================================================ */
            val livenessExecuted =
                livenessOrchestrator != null

            if (livenessOrchestrator != null) {

                val metricsFrame =
                    facialMetricsEngine
                        .computeFrameFromBitmap(faceBitmap)

                livenessOrchestrator.onFrame(metricsFrame)

                val livenessResult =
                    livenessOrchestrator.evaluate()

                if (livenessResult is LivenessResult.SpoofDetected) {
                    return AttendanceResult.Blocked(
                        reason = "Liveness check failed"
                    )
                }
            }

            /* ================================================================
             * 5️⃣ FACE EMBEDDING (DETERMINISTIC)
             * ================================================================ */
            val embedding = try {
                faceNet.getEmbedding(faceBitmap)
            } catch (_: Exception) {
                return AttendanceResult.Blocked(
                    reason = "Face embedding extraction failed"
                )
            }

            /* ================================================================
             * 6️⃣ FACE MATCHING (POLICY-DRIVEN)
             * ================================================================ */
            val matchDecision =
                faceMatchingUseCase.matchNow(
                    liveEmbedding = embedding,
                    employeeId = employeeId
                )

            if (matchDecision !is MatchDecision.MatchSuccess) {
                return AttendanceResult.Blocked(
                    reason = "Face matching failed"
                )
            }

            /* ================================================================
             * 7️⃣ FINAL ADMINISTRATIVE ATTENDANCE DECISION
             * ================================================================
             * Delegates to AttendanceUseCase
             * (Stage 0.2 — Mutex + Timeout enforced there)
             */
            return attendanceUseCase.attempt(
                action = action,
                livenessExecuted = livenessExecuted
            )

        } finally {

            /* ================================================================
             * 🔥 MEMORY SAFETY (NON-NEGOTIABLE)
             * ================================================================
             * The frozen frame MUST ALWAYS be recycled:
             * - Success
             * - Failure
             * - Exception
             */
            frameBitmap.recycle()

            /* ================================================================
             * 🔓 RELEASE CONCURRENCY GUARD
             * ================================================================ */
            isProcessing.set(false)
        }
    }
}
