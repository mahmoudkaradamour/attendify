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
 * ============================================================================
 * ROLE (What this class is responsible for):
 * ============================================================================
 * Orchestrates the FULL runtime attendance pipeline using a SINGLE camera frame.
 *
 * This is a **pure coordination layer**:
 *  - It glues independent subsystems together
 *  - It owns NO business rules
 *  - It stores NO data
 *  - It defines NO policies
 *
 * ============================================================================
 * SECURITY & ARCHITECTURAL GUARANTEES:
 * ============================================================================
 * ✅ Fail‑Fast: the first blocking condition aborts the pipeline immediately
 * ✅ Deterministic: same input → same decision
 * ✅ Auditable: all security‑relevant decisions are explicit
 * ✅ UI‑agnostic: zero knowledge about Flutter or presentation
 * ✅ Defensive: resistant to race conditions and user abuse
 *
 * ============================================================================
 * IMPORTANT DESIGN NOTE:
 * ============================================================================
 * The logic is intentionally procedural and explicit.
 *
 * A future migration to Chain‑of‑Responsibility (Interceptors) is possible,
 * but NOT prematurely implemented here to avoid abstract complexity.
 */
class AttendanceRuntimeOrchestrator(

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
     * Concurrent execution guard.
     *
     * WHY THIS EXISTS:
     * - Prevents spam clicks / auto‑clickers
     * - Prevents concurrent ML inference
     * - Prevents OutOfMemory on low‑end devices
     *
     * AtomicBoolean is intentionally chosen over Mutex here:
     * ✅ simpler
     * ✅ non‑blocking
     * ✅ fail‑fast
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * attemptAttendance
     *
     * =========================================================================
     * ENTRY POINT
     * =========================================================================
     *
     * Executes a complete attendance attempt from a single RGB bitmap.
     *
     * @param action       AttendanceAction (CHECK_IN / CHECK_OUT)
     * @param frameBitmap Full camera frame (already RGB)
     * @param employeeId  Target employee identifier
     *
     * @return AttendanceResult.Accepted or AttendanceResult.Blocked
     */
    fun attemptAttendance(
        action: AttendanceAction,
        frameBitmap: Bitmap,
        employeeId: String
    ): AttendanceResult {

        /* ================================================================
         * 🔒 CONCURRENCY GUARD (ABSOLUTE FIRST)
         * ================================================================
         * If another attendance attempt is running:
         *  - Drop this request immediately
         *  - Do NOT queue
         *  - Do NOT wait
         */
        if (!isProcessing.compareAndSet(false, true)) {
            return AttendanceResult.Blocked(
                reason = "Attendance already in progress"
            )
        }

        try {

            /* ================================================================
             * 1️⃣ IMAGE QUALITY GATE
             * ================================================================
             * Filters out:
             *  - Extremely dark frames
             *  - Blurry frames
             *  - Obvious camera failures
             *
             * Prevents garbage from reaching ML pipeline.
             */
            val imageStatus = ImageQualityChecker.checkFrame(frameBitmap)
            if (imageStatus != SystemStatus.OK) {
                return AttendanceResult.Blocked(
                    reason = "Image quality check failed: $imageStatus"
                )
            }

            /* ================================================================
             * 2️⃣ FACE DETECTION
             * ================================================================
             * Detect exactly ONE viable face.
             *
             * Any ambiguity is treated as failure.
             */
            val detection = faceDetector.detectBestFace(frameBitmap)
                ?: return AttendanceResult.Blocked(
                    reason = "No face detected"
                )

            /* ================================================================
             * 3️⃣ FACE CROP
             * ================================================================
             * Security‑aware crop:
             *  - Expanded margins
             *  - Preserves background for spoof analysis
             */
            val faceBitmap = FaceCropper.cropAndResize(
                sourceBitmap = frameBitmap,
                faceBox = detection.box,
                targetSize = 112 // MobileFaceNet expected input
            ) ?: return AttendanceResult.Blocked(
                reason = "Face crop failed"
            )

            /* ================================================================
             * 4️⃣ LIVENESS (OPTIONAL, BUT AUDITED)
             * ================================================================
             * Liveness may be disabled by policy.
             *
             * CRITICAL RULE:
             * The system MUST remember whether liveness was executed.
             * This is passed downstream for forensic audit.
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

            /* ================================================================
             * 5️⃣ FACE EMBEDDING
             * ================================================================
             * Convert face bitmap into normalized embedding vector.
             *
             * Any exception here is treated as hard failure
             * (corrupt frame, memory pressure, model instability).
             */
            val embedding = try {
                faceNet.getEmbedding(faceBitmap)
            } catch (_: Exception) {
                return AttendanceResult.Blocked(
                    reason = "Face embedding extraction failed"
                )
            }

            /* ================================================================
             * 6️⃣ FACE MATCHING
             * ================================================================
             * Delegates all matching logic to domain use‑case.
             *
             * Orchestrator does NOT interpret distances or thresholds.
             */
            val matchDecision = faceMatchingUseCase.matchNow(
                liveEmbedding = embedding,
                employeeId = employeeId
            )

            when (matchDecision) {
                is MatchDecision.MatchSuccess -> {
                    // Continue pipeline
                }
                else -> {
                    return AttendanceResult.Blocked(
                        reason = "Face matching failed"
                    )
                }
            }

            /* ================================================================
             * 7️⃣ ADMINISTRATIVE ATTENDANCE DECISION
             * ================================================================
             * This step:
             *  - Combines Time Integrity
             *  - Location Integrity
             *  - Working Time Policies
             *
             * livenessExecuted is passed explicitly for audit trail.
             */
            return attendanceUseCase.attempt(
                action = action,
                livenessExecuted = livenessExecuted
            )

        } finally {
            /* ================================================================
             * 🔓 CONCURRENCY GUARD RELEASE
             * ================================================================
             * Must ALWAYS execute to avoid permanent lock‑out.
             */
            isProcessing.set(false)
        }
    }
}