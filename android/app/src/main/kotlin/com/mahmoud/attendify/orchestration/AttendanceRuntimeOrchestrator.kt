package com.mahmoud.attendify.attendance.orchestration

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase

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
 * PURPOSE:
 * --------
 * Executes the full runtime attendance pipeline using a SINGLE input image.
 *
 * SECURITY & ARCHITECTURE GUARANTEES:
 * ----------------------------------
 * - No policy definition
 * - No persistence
 * - Fail-fast on first blocking condition
 * - No UI / Flutter knowledge
 * - Deterministic & auditable execution path
 *
 * NOTE:
 * -----
 * This class is intentionally procedural and explicit.
 * Future evolution toward Chain-of-Responsibility is prepared
 * via dedicated interceptor abstractions (outside this file).
 */
class AttendanceRuntimeOrchestrator(

    /* ---------------- FACE ---------------- */
    private val faceDetector: FaceDetector,
    private val faceNet: MobileFaceNet,

    /* -------------- LIVENESS -------------- */
    private val livenessOrchestrator: LivenessOrchestrator?, // nullable by policy
    private val facialMetricsEngine: FacialMetricsEngine,

    /* --------------- MATCHING ------------- */
    private val faceMatchingUseCase: FaceMatchingUseCase,

    /* ------------- ATTENDANCE ------------- */
    private val attendanceUseCase: AttendanceUseCase
) {

    /**
     * Guards against concurrent / repeated execution
     * (Double-tap, race conditions, slow devices).
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * attemptAttendance
     *
     * Entry point for a full attendance attempt using one bitmap.
     *
     * @param action       CHECK_IN or CHECK_OUT
     * @param frameBitmap Full camera frame (RGB bitmap)
     * @param employeeId  Target employee identifier
     *
     * @return AttendanceResult (Accepted or Blocked)
     */
    fun attemptAttendance(
        action: AttendanceAction,
        frameBitmap: Bitmap,
        employeeId: String
    ): AttendanceResult {

        /* ==================================================
         * 🔒 CONCURRENCY GUARD
         * ==================================================
         * Prevents multiple concurrent attendance executions.
         */
        if (!isProcessing.compareAndSet(false, true)) {
            return AttendanceResult.Blocked(
                reason = "Attendance already in progress"
            )
        }

        try {

            /* ==================================================
             * 1️⃣ IMAGE QUALITY GATE
             * ==================================================
             * Prevents unusable frames from entering ML pipeline.
             */
            val imageStatus = ImageQualityChecker.checkFrame(frameBitmap)
            if (imageStatus != SystemStatus.OK) {
                return AttendanceResult.Blocked(
                    reason = "Image quality check failed: $imageStatus"
                )
            }

            /* ==================================================
             * 2️⃣ FACE DETECTION
             * ==================================================
             * Detect ONE best face only.
             * Security assumption: detector must not accept
             * ambiguous or unstable detections.
             */
            val detection = faceDetector.detectBestFace(frameBitmap)
                ?: return AttendanceResult.Blocked(
                    reason = "No face detected"
                )

            /* ==================================================
             * 3️⃣ FACE CROP (SECURITY‑AWARE, EXPANDED)
             * ==================================================
             * Uses expanded crop to preserve background for FAS.
             */
            val faceBitmap = FaceCropper.cropAndResize(
                sourceBitmap = frameBitmap,
                faceBox = detection.box,
                targetSize = 112 // MobileFaceNet input
            ) ?: return AttendanceResult.Blocked(
                reason = "Face crop failed"
            )

            /* ==================================================
             * 4️⃣ LIVENESS (OPTIONAL, POLICY‑DRIVEN)
             * ==================================================
             * Executed only if enabled by policy.
             * Execution state is tracked explicitly for audit.
             */
            val livenessExecuted = livenessOrchestrator != null

            if (livenessOrchestrator != null) {

                val metricsFrame =
                    facialMetricsEngine.computeFrameFromBitmap(faceBitmap)

                livenessOrchestrator.onFrame(metricsFrame)

                val livenessResult = livenessOrchestrator.evaluate()
                if (livenessResult is LivenessResult.SpoofDetected) {
                    return AttendanceResult.Blocked(
                        reason = "Liveness check failed"
                    )
                }
            }

            /* ==================================================
             * 5️⃣ FACE EMBEDDING
             * ==================================================
             * Generates normalized embedding vector.
             */
            val embedding = try {
                faceNet.getEmbedding(faceBitmap)
            } catch (e: Exception) {
                return AttendanceResult.Blocked(
                    reason = "Face embedding extraction failed"
                )
            }

            /* ==================================================
             * 6️⃣ FACE MATCHING
             * ==================================================
             * Delegates ALL decision logic to matching layer.
             */
            val matchDecision = faceMatchingUseCase.matchNow(
                liveEmbedding = embedding,
                employeeId = employeeId
            )

            when (matchDecision) {
                is MatchDecision.MatchSuccess -> {
                    // continue
                }
                else -> {
                    return AttendanceResult.Blocked(
                        reason = "Face matching failed"
                    )
                }
            }

            /* ==================================================
             * 7️⃣ ADMINISTRATIVE ATTENDANCE DECISION
             * ==================================================
             * Time / Location / Working Time.
             *
             * NOTE:
             * livenessExecuted is propagated for forensic audit.
             */
            return attendanceUseCase.attempt(
                action = action,
                livenessExecuted = livenessExecuted
            )

        } finally {
            /* ==================================================
             * 🔓 RELEASE CONCURRENCY GUARD
             * ==================================================
             */
            isProcessing.set(false)
        }
    }
}