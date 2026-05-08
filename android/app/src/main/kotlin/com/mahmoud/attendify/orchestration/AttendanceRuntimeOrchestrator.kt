package com.mahmoud.attendify.orchestration

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings

import java.util.concurrent.atomic.AtomicBoolean

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/* ================= DOMAIN ================= */
import com.mahmoud.attendify.attendance.domain.*
import com.mahmoud.attendify.attendance.usecase.*

/* ================= CAMERA ================= */
import com.mahmoud.attendify.camera.*

/* ================= FACE ================= */
import com.mahmoud.attendify.face.*

/* ================= LIVENESS ================= */
import com.mahmoud.attendify.liveness.*
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* ================= MATCHING ================= */
import com.mahmoud.attendify.matching.*

/* ================= CONTEXT ================= */
import com.mahmoud.attendify.orchestration.context.*

/* ================= SECURITY ================= */
import com.mahmoud.attendify.security.*
import com.mahmoud.attendify.security.boundary.*
import com.mahmoud.attendify.security.legal.*
import com.mahmoud.attendify.security.privacy.*

/* ================= FORENSICS ================= */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.wal.WalManager
import com.mahmoud.attendify.forensics.integrity.SnapshotVerifier

/* ================= LIFECYCLE ================= */
import com.mahmoud.attendify.attendance.lifecycle.*

/**
 * =============================================================================
 * 🧠 AttendanceRuntimeOrchestrator — Deterministic Secure Execution Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL COMPUTATION MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   A = User Action
 *   E = Evidence (Snapshot)
 *   S = Security Constraints
 *
 * Then:
 *
 *   Result = f(A, E, S)
 *
 * where:
 *
 *   f is:
 *     ✅ Deterministic
 *     ✅ Auditable
 *     ✅ Side‑effect controlled
 *
 * -----------------------------------------------------------------------------
 * 📊 EXECUTION PIPELINE (STRICT ORDER)
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────┐
 *   │ Session Integrity Validation │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Request Gate (Rate Limit)    │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Atomic Lock (Concurrency)    │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Snapshot Capture             │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Cryptographic Verification   │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Replay Protection            │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ WAL BEGIN                    │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Security Context Capture     │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Image Quality Validation     │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Face Detection               │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Liveness Analysis            │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Identity Matching            │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Business Decision            │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Legal Evidence Generation    │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ NonCancellable Commit        │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ WAL COMMIT                   │
 *   └──────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🔐 CRITICAL SECURITY INVARIANTS
 * -----------------------------------------------------------------------------
 *
 * 1. BEGIN happens-before COMMIT
 * 2. Snapshot MUST be verified before usage
 * 3. All executions produce forensic trace
 * 4. No concurrent execution (linearizable)
 * 5. No partial persistence allowed
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * "Convert untrusted inputs into verifiable evidence through a strictly ordered,
 * deterministic pipeline."
 *
 * =============================================================================
 */
class AttendanceRuntimeOrchestrator(

    private val context: Context,
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

    /* =========================================================================
     * 🔒 CONCURRENCY CONTROL (LINEARIZATION)
     * ========================================================================= */

    private val isProcessing = AtomicBoolean(false)

    /* =========================================================================
     * 🚀 MAIN ENTRY POINT
     * ========================================================================= */

    suspend fun attemptAttendance(
        action: AttendanceAction
    ): AttendanceResult {

        /* ================= SESSION VALIDATION ================= */

        if (!SessionIntegrityGuard.validate()) {
            return AttendanceResult.Blocked("Session invalid")
        }

        /* ================= RATE LIMIT GUARD ================= */

        AttendanceRequestGate.guard()?.let { return it }

        /* ================= ATOMIC LOCK ================= */

        if (!isProcessing.compareAndSet(false, true)) {
            return fail("Concurrent execution not allowed")
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
                    return fail("Snapshot acquisition failed")
                }

            frame = snapshot.payload.frozenFrame
            val txId = snapshot.snapshotId.toString()

            /* ================= VERIFY SNAPSHOT ================= */

            if (!SnapshotVerifier.verify(snapshot)) {
                return fail("Snapshot verification failed")
            }

            /* ================= REPLAY PROTECTION ================= */

            if (!ReplayProtectionGuard.registerOrReject(snapshot.snapshotId)) {
                return fail("Replay attack detected")
            }

            /* ================= WAL BEGIN ================= */

            walManager.begin(txId, snapshot.snapshotHash)

            /* ================= SECURITY CONTEXT ================= */

            val adbEnabled =
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.ADB_ENABLED, 0
                ) == 1

            auditTrailWriter.appendSystemEvent(
                "SECURITY_CONTEXT",
                "adb_enabled=$adbEnabled"
            )

            /* ================= IMAGE QUALITY ================= */

            if (!isImageValid(frame)) {
                return fail("Image quality insufficient")
            }

            /* ================= FACE DETECTION ================= */

            val detection =
                faceDetector.detectBestFace(frame)
                    ?: return fail("No face detected")

            /* ================= FACE EXTRACTION ================= */

            faceBitmap = FaceCropper.cropAndResize(
                frame,
                detection.box,
                112
            ) ?: return fail("Face extraction failed")

            /* ================= LIVENESS ================= */

            val liveness =
                handleLiveness(faceBitmap)
                    ?: return fail("Liveness check failed")

            /* ================= EMBEDDING ================= */

            val embedding =
                EmbeddingAnonymizer.anonymize(
                    faceNet.getEmbedding(faceBitmap)
                )

            /* ================= MATCHING ================= */

            val matched =
                faceMatchingOrchestrator.performMatch(
                    embedding,
                    employeeId,
                    null
                ) is MatchDecision.MatchSuccess

            if (!matched) {
                return fail("Identity mismatch")
            }

            /* ================= BUSINESS DECISION ================= */

            val result = attendanceUseCase.attempt(
                action,
                AttendanceAtomicContext(
                    frame,
                    snapshot.payload.timeSnapshot,
                    snapshot.payload.locationEvidence
                ),
                liveness
            )

            /* ================= LEGAL EVIDENCE ================= */

            val legalProof =
                LegalProofGenerator.generate(
                    employeeId,
                    timestamp,
                    result
                )

            /* ================= ATOMIC COMMIT ================= */

            withContext(NonCancellable) {

                persistEvidence(snapshot, result, legalProof)

                walManager.commit(txId)
            }

            lifecycleManager.markFinal(AttemptStatus.SUCCESS)

            return result

        } catch (e: Exception) {

            lifecycleManager.markFinal(AttemptStatus.FAILED)
            throw e

        } finally {

            /* ================= MEMORY SANITIZATION ================= */

            BiometricPrivacyGuard.secureDispose(faceBitmap)
            BiometricPrivacyGuard.secureDispose(frame)

            isProcessing.set(false)
        }
    }

    /* =========================================================================
     * 🧰 HELPERS
     * ========================================================================= */

    private fun fail(reason: String): AttendanceResult {
        lifecycleManager.markFinal(AttemptStatus.FAILED)
        return AttendanceResult.Blocked(reason)
    }





    private fun isImageValid(frame: Bitmap) =
        ImageQualityChecker.checkFrame(frame) == SystemStatus.OK

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

    private suspend fun persistEvidence(
        snapshot: SignedPhysicalRealitySnapshot,
        result: AttendanceResult,
        legalProof: LegalEvidenceBundle
    ) {

        val normalized =
            EvidenceNormalizer.normalize(snapshot, result)

        auditTrailWriter.append(normalized)
        legalEvidenceWriter.append(legalProof)
    }
}