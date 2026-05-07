package com.mahmoud.attendify.orchestration

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean

/* ============================================================================ */
import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase
/* ============================================================================ */
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus
/* ============================================================================ */
import com.mahmoud.attendify.face.*
/* ============================================================================ */
import com.mahmoud.attendify.liveness.*
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult
/* ============================================================================ */
import com.mahmoud.attendify.matching.*
/* ============================================================================ */
import com.mahmoud.attendify.orchestration.context.*
/* ============================================================================ */
import com.mahmoud.attendify.security.*
import com.mahmoud.attendify.security.boundary.AttendanceRequestGate
import com.mahmoud.attendify.security.boundary.SessionIntegrityGuard
import com.mahmoud.attendify.security.legal.*
import com.mahmoud.attendify.security.privacy.*
/* ============================================================================ */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.integrity.SnapshotVerifier
/* ============================================================================ */
import com.mahmoud.attendify.attendance.lifecycle.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * 🧠 AttendanceRuntimeOrchestrator — Secure Biometric Transaction Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 FORMAL SYSTEM MODEL
 * -----------------------------------------------------------------------------
 *
 * This class implements a **deterministic transactional execution pipeline**
 * over real-world biometric signals.
 *
 * Formal definition:
 *
 *   Output = f(A, E(t), C)
 *
 * Where:
 *
 *   A = User Action
 *   E(t) = Evidence captured at time t
 *   C = Constraint set (security + privacy + integrity)
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE (MULTI-LAYER DATAFLOW)
 * -----------------------------------------------------------------------------
 *
 *   USER ACTION
 *       │
 *       ▼
 * ┌───────────────┐
 * │ SESSION CHECK │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ UI GATE       │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ MUTEX         │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ SNAPSHOT      │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ VERIFY        │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ REPLAY GUARD  │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ QUALITY       │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ FACE PIPELINE │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ LIVENESS      │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ MATCHING      │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ DECISION      │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ LEGAL PROOF   │
 * └──────┬────────┘
 *        ▼
 * ┌───────────────┐
 * │ COMMIT        │
 * └───────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY + PRIVACY INVARIANTS
 * -----------------------------------------------------------------------------
 *
 * ✅ Replay-safe execution
 * ✅ Rate-limited user boundary
 * ✅ Cryptographically verified inputs
 * ✅ Non-repudiation (legal proof)
 * ✅ No biometric data persistence
 *
 * -----------------------------------------------------------------------------
 * 🔬 KEY PROPERTY
 * -----------------------------------------------------------------------------
 *
 *   The system is:
 *
 *     Deterministic + Memory-safe + Append-only + Privacy-preserving
 *
 */
class AttendanceRuntimeOrchestrator(

    private val physicalRealityBuilder: PhysicalRealityBuilder,
    private val faceDetector: FaceDetector,
    private val faceNet: MobileFaceNet,

    private val livenessOrchestrator: LivenessOrchestrator?,
    private val facialMetricsEngine: FacialMetricsEngine,

    private val faceMatchingOrchestrator: FaceMatchingOrchestrator,
    private val attendanceUseCase: AttendanceUseCase,

    private val auditTrailWriter: ForensicAuditTrailWriter,
    private val legalEvidenceWriter: LegalEvidenceWriter,

    val lifecycleManager: AttemptLifecycleManager

) {

    /**
     * 🔒 Linearizability control
     */
    private val isProcessing = AtomicBoolean(false)

    suspend fun attemptAttendance(
        action: AttendanceAction
    ): AttendanceResult {

        /* ================= SESSION ================= */
        if (!SessionIntegrityGuard.validate()) {
            return AttendanceResult.Blocked("Session invalid")
        }

        /* ================= UI GATE ================= */
        val gateResult = AttendanceRequestGate.guard()
        if (gateResult != null) return gateResult

        /* ================= MUTEX ================= */
        if (!isProcessing.compareAndSet(false, true)) {
            return blocked("Attendance already in progress")
        }

        var frame: Bitmap? = null
        var faceBitmap: Bitmap? = null

        val employeeId = SecureEmployeeSession.requireEmployeeId()
        val timestamp = System.currentTimeMillis()

        lifecycleManager.markInitiated(employeeId, timestamp)
        auditTrailWriter.preLogInitiated(employeeId, timestamp)

        try {

            /* ================= SNAPSHOT ================= */
            val snapshot = physicalRealityBuilder
                .buildSignedOrFail(2000)
                .getOrElse {
                    lifecycleManager.markFinal(AttemptStatus.FAILED)
                    return blocked("Snapshot failed")
                }

            frame = snapshot.payload.frozenFrame

            /* ================= VERIFY ================= */
            if (!SnapshotVerifier.verify(snapshot)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Invalid snapshot")
            }

            /* ================= REPLAY ================= */
            if (!ReplayProtectionGuard.registerOrReject(snapshot.snapshotId)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Replay detected")
            }

            /* ================= QUALITY ================= */
            if (!isImageValid(frame)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Bad image quality")
            }

            /* ================= FACE ================= */
            val detection = faceDetector.detectBestFace(frame)
                ?: return fail("No face detected")

            faceBitmap = FaceCropper.cropAndResize(
                frame,
                detection.box,
                112
            ) ?: return fail("Face crop failed")

            /* ================= LIVENESS ================= */
            val liveness =
                handleLiveness(faceBitmap) ?: return fail("Spoof detected")

            /* ================= MATCHING ================= */
            val rawEmbedding = faceNet.getEmbedding(faceBitmap)

            /**
             * 📌 Privacy enforcement:
             * Embedding is anonymized to reduce reconstruction risk
             */
            val embedding =
                EmbeddingAnonymizer.anonymize(rawEmbedding)

            val matched =
                faceMatchingOrchestrator.performMatch(
                    embedding,
                    employeeId,
                    null
                ) is MatchDecision.MatchSuccess

            if (!matched) return fail("Face mismatch")

            /* ================= DECISION ================= */
            val result = attendanceUseCase.attempt(
                action,
                AttendanceAtomicContext(
                    frame,
                    snapshot.payload.timeSnapshot,
                    snapshot.payload.locationEvidence
                ),
                liveness
            )

            /* ================= LEGAL PROOF ================= */
            val legalProof = LegalProofGenerator.generate(
                employeeId,
                timestamp,
                result
            )

            /* ================= COMMIT ================= */
            persistEvidence(snapshot, result, legalProof)

            lifecycleManager.markFinal(AttemptStatus.SUCCESS)

            return result

        } catch (e: Exception) {

            lifecycleManager.markFinal(AttemptStatus.FAILED)
            throw e

        } finally {

            /**
             * =====================================================================
             * 🔐 PRIVACY-COMPLIANT MEMORY FINALIZATION
             * =====================================================================
             *
             * Critical invariant:
             *
             *   ∀ Bitmap b:
             *       After use(b) → disposed(b)
             *
             * Ensures:
             *   ✅ no biometric leakage
             *   ✅ no memory retention
             */
            BiometricPrivacyGuard.secureDispose(faceBitmap)
            BiometricPrivacyGuard.secureDispose(frame)

            isProcessing.set(false)
        }
    }

    /* ========================================================================= */

    private fun fail(reason: String): AttendanceResult {
        lifecycleManager.markFinal(AttemptStatus.FAILED)
        return blocked(reason)
    }

    private fun blocked(reason: String) =
        AttendanceResult.Blocked(reason)

    private fun isImageValid(frame: Bitmap) =
        ImageQualityChecker.checkFrame(frame) == SystemStatus.OK

    /**
     * -----------------------------------------------------------------------------
     * 🧬 LIVENESS PIPELINE
     * -----------------------------------------------------------------------------
     *
     * Transform:
     *
     *   Bitmap → Feature Space → Temporal Evaluation → Decision
     */
    private fun handleLiveness(faceBitmap: Bitmap): Boolean? {

        val orchestrator = livenessOrchestrator ?: return false

        val metrics =
            facialMetricsEngine.computeFrameFromBitmap(faceBitmap)

        orchestrator.onFrame(metrics)

        return when (orchestrator.evaluate()) {
            is LivenessResult.SpoofDetected -> null
            else -> true
        }
    }

    /**
     * -----------------------------------------------------------------------------
     * 🔐 DUAL-CHANNEL PERSISTENCE
     * -----------------------------------------------------------------------------
     *
     * Separates:
     *
     *   1. Forensic evidence (system audit)
     *   2. Legal proof (non-repudiation)
     *
     * -----------------------------------------------------------------------------
     * FLOW:
     *
     *   Input  → Normalize → Split → Persist
     */
    private suspend fun persistEvidence(
        snapshot: SignedPhysicalRealitySnapshot,
        result: AttendanceResult,
        legalProof: LegalEvidenceBundle
    ) {
        withContext(NonCancellable) {

            val evidence =
                EvidenceNormalizer.normalize(snapshot, result)

            auditTrailWriter.append(evidence)
            legalEvidenceWriter.append(legalProof)
        }
    }
}