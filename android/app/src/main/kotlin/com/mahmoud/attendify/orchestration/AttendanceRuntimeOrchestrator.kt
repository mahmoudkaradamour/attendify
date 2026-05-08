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
import com.mahmoud.attendify.forensics.wal.WalManager
/* ============================================================================ */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.integrity.SnapshotVerifier
/* ============================================================================ */
import com.mahmoud.attendify.attendance.lifecycle.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * 🧠 AttendanceRuntimeOrchestrator — Transactional Biometric Execution Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 FORMAL EXECUTION MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   A = Action
 *   E = Evidence (snapshot)
 *   C = Constraints (security + policies)
 *
 * Then:
 *
 *   Result = f(A, E, C)
 *
 * where:
 *   f is deterministic, side effect controlled, and auditable
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE GRAPH
 * -----------------------------------------------------------------------------
 *
 * USER INPUT
 *    ↓
 * SESSION VALIDATION
 *    ↓
 * UI RATE LIMIT
 *    ↓
 * MUTEX (LINEARIZATION)
 *    ↓
 * SNAPSHOT CAPTURE
 *    ↓
 * SIGNATURE VERIFICATION
 *    ↓
 * REPLAY DEFENSE
 *    ↓
 * WAL BEGIN (transaction journaling)
 *    ↓
 * IMAGE QUALITY FILTER
 *    ↓
 * FACE EXTRACTION
 *    ↓
 * LIVENESS ANALYSIS
 *    ↓
 * MATCHING
 *    ↓
 * DOMAIN DECISION
 *    ↓
 * LEGAL PROOF
 *    ↓
 * PERSISTENCE
 *    ↓
 * WAL COMMIT
 *
 * -----------------------------------------------------------------------------
 * 🔐 CRITICAL INVARIANTS
 * -----------------------------------------------------------------------------
 *
 * ✅ Every execution produces trace
 * ✅ Every snapshot must be verified
 * ✅ Every transaction is recorded (WAL)
 * ✅ No silent failure
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
    private val walManager: WalManager,
    val lifecycleManager: AttemptLifecycleManager

) {

    /**
     * 🔒 Ensures single in-flight transaction (linearizability)
     */
    private val isProcessing = AtomicBoolean()

    suspend fun attemptAttendance(
        action: AttendanceAction
    ): AttendanceResult {

        /* ================= SESSION ================= */
        if (!SessionIntegrityGuard.validate()) {
            return AttendanceResult.Blocked("Session invalid")
        }

        /* ================= UI GATE ================= */
        AttendanceRequestGate.guard()?.let { return it }

        /* ================= MUTEX ================= */
        if (!isProcessing.compareAndSet(false, true)) {
            return blocked("Attendance already in progress")
        }

        var frame: Bitmap? = null
        var faceBitmap: Bitmap? = null
        var txId: String? = null

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

            /**
             * ✅ FIX: UUID → String
             */
            txId = snapshot.snapshotId.toString()

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

            /* ================= WAL BEGIN ================= */
            walManager.begin(
                id = txId,
                payloadHash = snapshot.snapshotHash.toString() // ✅ FIX
            )

            /* ================= QUALITY ================= */
            if (!isImageValid(frame)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Bad image quality")
            }

            /* ================= FACE ================= */
            val detection =
                faceDetector.detectBestFace(frame)
                    ?: return fail("No face detected")

            faceBitmap = FaceCropper.cropAndResize(
                frame,
                detection.box,
                112
            ) ?: return fail("Face crop failed")

            /* ================= LIVENESS ================= */
            val liveness =
                handleLiveness(faceBitmap)
                    ?: return fail("Spoof detected")

            /* ================= MATCHING ================= */
            val embedding =
                EmbeddingAnonymizer.anonymize(
                    faceNet.getEmbedding(faceBitmap)
                )

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
            val legalProof =
                LegalProofGenerator.generate(
                    employeeId,
                    timestamp,
                    result
                )

            /* ================= COMMIT ================= */
            persistEvidence(snapshot, result, legalProof)

            /* ================= WAL COMMIT ================= */
            walManager.commit(txId)

            lifecycleManager.markFinal(AttemptStatus.SUCCESS)

            return result

        } catch (e: Exception) {

            lifecycleManager.markFinal(AttemptStatus.FAILED)
            throw e

        } finally {

            /**
             * 🔐 Memory sanitization (biometric privacy invariant)
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
     * 🧬 Converts raw face into liveness decision
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
     * 🔐 Dual persistence:
     *   - forensic (audit trail)
     *   - legal (non-repudiation)
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
