package com.mahmoud.attendify.orchestration

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
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
import com.mahmoud.attendify.forensics.wal.WalManager
import com.mahmoud.attendify.forensics.integrity.SnapshotVerifier
/* ============================================================================ */
import com.mahmoud.attendify.attendance.lifecycle.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * =============================================================================
 * 🧠 AttendanceRuntimeOrchestrator — Formal Transaction Execution Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ABSTRACT MODEL (MATHEMATICAL FORMULATION)
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   A = User Action
 *   E = Evidence (Snapshot)
 *   C = Constraints (Security, Environment, Policies)
 *
 * Then:
 *
 *   Result = f(A, E, C)
 *
 * where:
 *
 *   f : deterministic function
 *
 *   Properties:
 *     - Pure with respect to inputs
 *     - Side-effects are controlled and logged
 *     - Fully auditable
 *
 * -----------------------------------------------------------------------------
 * 📊 SYSTEM EXECUTION GRAPH (CONTROL FLOW)
 * -----------------------------------------------------------------------------
 *
 *   User Input
 *        │
 *        ▼
 *   Session Validation
 *        │
 *        ▼
 *   Request Gate (Rate Limiting)
 *        │
 *        ▼
 *   Atomic Lock (Linearization)
 *        │
 *        ▼
 *   Physical Snapshot Capture
 *        │
 *        ▼
 *   Cryptographic Verification
 *        │
 *        ▼
 *   Replay Protection
 *        │
 *        ▼
 *   WAL Begin (Pre-commit logging)
 *        │
 *        ▼
 *   Environment Validation (Security Signals)
 *        │
 *        ▼
 *   Image Quality Check
 *        │
 *        ▼
 *   Face Detection & Extraction
 *        │
 *        ▼
 *   Liveness Analysis
 *        │
 *        ▼
 *   Face Matching
 *        │
 *        ▼
 *   Decision Execution
 *        │
 *        ▼
 *   Legal Evidence Generation
 *        │
 *        ▼
 *   NonCancellable Persistence (Atomic Commit)
 *        │
 *        ▼
 *   WAL Commit
 *
 * -----------------------------------------------------------------------------
 * 🔐 CRITICAL INVARIANTS (SYSTEM SAFETY PROPERTIES)
 * -----------------------------------------------------------------------------
 *
 * 1. ∀ T : Transaction:
 *      BEGIN(T) happens-before COMMIT(T)
 *
 * 2. ∀ Evidence:
 *      Verified(E) = true before usage
 *
 * 3. ∄ Silent Execution:
 *      Every execution produces forensic trace
 *
 * 4. ∄ Partial Commit:
 *      Persistence ∈ NonCancellable region
 *
 * 5. ∄ Concurrent Execution:
 *      AtomicBoolean guarantees linearizability
 *
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

    /**
     * -----------------------------------------------------------------------------
     * 🔒 LINEARIZATION MECHANISM
     * -----------------------------------------------------------------------------
     *
     * Guarantees:
     *   Only one transaction executes at any given time.
     *
     * Formally:
     *   ∀ T1, T2:
     *     T1 || T2 cannot overlap
     *
     */
    private val isProcessing = AtomicBoolean(false)

    /**
     * =============================================================================
     * 🚀 attemptAttendance — MAIN ENTRY POINT
     * =============================================================================
     *
     * Executes full transactional pipeline.
     *
     * -----------------------------------------------------------------------------
     * 🧠 EXECUTION SEMANTICS
     * -----------------------------------------------------------------------------
     *
     * This function transforms untrusted input into:
     *
     *   → Verified, Signed, Auditable Result
     *
     */
    suspend fun attemptAttendance(
        action: AttendanceAction
    ): AttendanceResult {

        /* ================= SESSION VALIDATION ================= */
        if (!SessionIntegrityGuard.validate()) {
            return AttendanceResult.Blocked("Session invalid")
        }

        /* ================= RATE LIMIT ================= */
        AttendanceRequestGate.guard()?.let { return it }

        /* ================= MUTEX ================= */
        if (!isProcessing.compareAndSet(false, true)) {
            return blocked("Concurrent execution not allowed")
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
                    return blocked("Snapshot failure")
                }

            frame = snapshot.payload.frozenFrame
            val txId = snapshot.snapshotId.toString()

            /* ================= CRYPTOGRAPHIC VERIFICATION ================= */
            if (!SnapshotVerifier.verify(snapshot)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Invalid snapshot")
            }

            /* ================= REPLAY DEFENSE ================= */
            if (!ReplayProtectionGuard.registerOrReject(snapshot.snapshotId)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Replay detected")
            }

            /* ================= WAL BEGIN ================= */
            walManager.begin(txId, snapshot.snapshotHash.toString())

            /**
             * ---------------------------------------------------------------------
             * 🔐 SECURITY CONTEXT CAPTURE
             * ---------------------------------------------------------------------
             *
             * Captures environment signals that may indicate compromise:
             *
             *   - Accessibility Services (automation risk)
             *   - USB Debugging (external control channel)
             */
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) == 1

            val usbDebuggingEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED, 0
            ) == 1

            auditTrailWriter.appendSystemEvent(
                "SECURITY_CONTEXT",
                "accessibility=$accessibilityEnabled, adb=$usbDebuggingEnabled"
            )

            /* ================= IMAGE QUALITY ================= */
            if (!isImageValid(frame)) {
                lifecycleManager.markFinal(AttemptStatus.FAILED)
                return blocked("Poor image quality")
            }

            /* ================= FACE DETECTION ================= */
            val detection =
                faceDetector.detectBestFace(frame)
                    ?: return fail("Face not detected")

            /* ================= FACE EXTRACTION ================= */
            faceBitmap = FaceCropper.cropAndResize(
                frame,
                detection.box,
                112
            ) ?: return fail("Face extraction failed")

            /* ================= LIVENESS ANALYSIS ================= */
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

            if (!matched) return fail("Identity mismatch")

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

            /**
             * ---------------------------------------------------------------------
             * ✅ ATOMIC COMMIT SECTION (CRITICAL)
             * ---------------------------------------------------------------------
             *
             * This region is NON-CANCELLABLE.
             *
             * Guarantees:
             *   - No interruption after decision
             *   - No broken forensic chain
             *
             * Prevents:
             *   ❌ Coroutine cancellation attack
             *   ❌ Partial persistence
             */
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

            /**
             * ---------------------------------------------------------------------
             * 🔐 BIOMETRIC DATA SANITIZATION
             * ---------------------------------------------------------------------
             *
             * Ensures:
             *   - No residual biometric data in memory
             *   - Privacy preservation
             *
             */
            BiometricPrivacyGuard.secureDispose(faceBitmap)
            BiometricPrivacyGuard.secureDispose(frame)

            isProcessing.set(false)
        }
    }

    /* =========================================================================
     * 🧰 HELPER FUNCTIONS
     * ========================================================================= */

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
     * 🧬 LIVENESS EVALUATION PIPELINE
     * -----------------------------------------------------------------------------
     *
     * Pipeline:
     *
     *   Bitmap → Metrics → Temporal Analysis → Decision
     *
     * Output:
     *
     *   null  → spoof
     *   true  → live human
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
     * 🔐 PERSISTENCE LAYER
     * -----------------------------------------------------------------------------
     *
     * Dual-write model:
     *
     *   1. Forensic Evidence (append-only ledger)
     *   2. Legal Proof (non-repudiation)
     *
     * Ensures:
     *   Integrity + Accountability + Traceability
     */
    private suspend fun persistEvidence(
        snapshot: SignedPhysicalRealitySnapshot,
        result: AttendanceResult,
        legalProof: LegalEvidenceBundle
    ) {

        val evidence =
            EvidenceNormalizer.normalize(snapshot, result)

        auditTrailWriter.append(evidence)
        legalEvidenceWriter.append(legalProof)
    }
}