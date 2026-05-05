package com.mahmoud.attendify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/* ============================================================================
 * CAMERA & SYSTEM
 * ========================================================================== */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.SystemStatusReporter

/* ============================================================================
 * ORCHESTRATION (PIPELINE COORDINATION)
 * ========================================================================== */
import com.mahmoud.attendify.orchestration.AttendanceRuntimeOrchestrator
import com.mahmoud.attendify.orchestration.PhysicalRealityBuilder

/* ============================================================================
 * DOMAIN (FINAL DECISION AUTHORITY)
 * ========================================================================== */
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase

/* ============================================================================
 * BIOMETRICS
 * ========================================================================== */
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

import com.mahmoud.attendify.matching.FaceMatchingOrchestrator
import com.mahmoud.attendify.repository.local.LocalEncryptedEmployeeReferenceRepository
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy

import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine

/* ============================================================================
 * TIME, LOCATION, WORKING HOURS
 * ========================================================================== */
import com.mahmoud.attendify.system.time.*
import com.mahmoud.attendify.system.location.*
import com.mahmoud.attendify.system.time.working.*

/* ============================================================================
 * CONFIG & CHANNEL
 * ========================================================================== */
import com.mahmoud.attendify.attendance.config.AttendancePolicyProvider
import com.mahmoud.attendify.channel.AttendanceMethodChannel
import com.mahmoud.attendify.forensics.ForensicAuditTrailWriter

/**
 * ============================================================================
 * MainActivity  —  ANDROID COMPOSITION ROOT
 * ============================================================================
 *
 * ROLE (EXTREMELY RESTRICTED):
 * ---------------------------------------------------------------------------
 * This Activity is:
 *  ✅ Android entry point
 *  ✅ Dependency Composition Root
 *  ✅ Camera PREVIEW host only
 *
 * This Activity is NOT:
 *  ❌ A decision maker
 *  ❌ A biometric processor
 *  ❌ A holder of security authority
 *
 * ============================================================================
 * TRUST MODEL
 * ============================================================================
 * MainActivity is considered **UNTRUSTED** by design.
 *
 * Any compromise of this class MUST NOT:
 *  - Influence attendance decisions
 *  - Alter biometric evidence
 *  - Bypass atomic reality capture
 *
 * ============================================================================
 * ARCHITECTURAL OVERVIEW
 * ============================================================================
 *
 *   ┌───────────────────────────────┐
 *   │        Flutter UI             │  UNTRUSTED
 *   └──────────────┬────────────────┘
 *                  │ MethodChannel (intent only)
 *   ┌──────────────▼────────────────┐
 *   │        MainActivity            │  COMPOSITION ROOT
 *   │  - wires dependencies          │
 *   │  - hosts camera preview        │
 *   └──────────────┬────────────────┘
 *                  │ fully‑wired runtime
 *   ┌──────────────▼────────────────┐
 *   │ AttendanceRuntimeOrchestrator │  TRUSTED PIPELINE
 *   └──────────────┬────────────────┘
 *                  │ AtomicContext
 *   ┌──────────────▼────────────────┐
 *   │     AttendanceUseCase          │  FINAL AUTHORITY
 *   └───────────────────────────────┘
 *
 * ============================================================================
 * POST‑PHASE‑3.2 GUARANTEE (AFTER TOCTOU DEFUSING)
 * ============================================================================
 *
 * ✅ MainActivity NEVER:
 *    - captures frames
 *    - reads time
 *    - evaluates location
 *
 * ✅ All physical reality is captured atomically inside:
 *    PhysicalRealityBuilder
 */
class MainActivity : FlutterActivity() {

    /* =========================================================================
     * UI COMPONENTS
     * ======================================================================= */

    /**
     * Camera preview surface.
     *
     * IMPORTANT DISTINCTION:
     * ----------------------
     * Preview is VISUAL FEEDBACK only.
     * It has ZERO access to frames used in attendance.
     */
    private lateinit var previewView: PreviewView

    /* =========================================================================
     * CORE RUNTIME COMPONENTS
     * ======================================================================= */

    private lateinit var cameraManager: CameraManager
    private lateinit var orchestrator: AttendanceRuntimeOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ---------------------------------------------------------------------
         * UI INITIALIZATION
         * ------------------------------------------------------------------ */
        previewView = PreviewView(this)
        setContentView(previewView)

        /* ---------------------------------------------------------------------
         * CAMERA MANAGER
         *
         * Owns:
         *  - Camera lifecycle
         *  - Hardware access
         *
         * Does NOT:
         *  - Decide when frames are frozen
         *  - Decide attendance logic
         * ------------------------------------------------------------------ */
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = SystemStatusReporter {
                // Optional non‑authoritative UI feedback
            }
        )

        /* ---------------------------------------------------------------------
         * COMPOSITION ROOT
         * ------------------------------------------------------------------ */
        orchestrator = buildAttendanceRuntime(cameraManager)

        /* ---------------------------------------------------------------------
         * PERMISSIONS
         * ------------------------------------------------------------------ */
        checkCameraPermissionAndStart()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        /* ---------------------------------------------------------------------
         * FLUTTER ↔ NATIVE BRIDGE
         *
         * SECURITY CONTRACT:
         * Flutter may send INTENT only.
         * Flutter NEVER supplies:
         *  - Identity
         *  - Evidence
         *  - Flags
         * ------------------------------------------------------------------ */
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "attendance_channel"
        ).setMethodCallHandler(
            AttendanceMethodChannel(orchestrator)
        )
    }

    /* =========================================================================
     * CAMERA PERMISSION FLOW
     * ======================================================================= */

    private fun checkCameraPermissionAndStart() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraPreview()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    /**
     * Starts CAMERA PREVIEW ONLY.
     *
     * Frame capture for attendance is strictly controlled elsewhere.
     */
    private fun startCameraPreview() {
        cameraManager.startCamera(
            preview = previewView.surfaceProvider
        )
    }

    /* =========================================================================
     * COMPOSITION ROOT IMPLEMENTATION
     * ======================================================================= */

    /**
     * Builds the complete attendance runtime.
     *
     * SCIENTIFIC / ARCHITECTURAL RATIONALE:
     * ------------------------------------
     * - All dependencies are explicit
     * - No hidden globals
     * - Auditable construction
     * - Reproducible pipeline
     */
    private fun buildAttendanceRuntime(
        cameraManager: CameraManager
    ): AttendanceRuntimeOrchestrator {

        /* ---------------- FACE & EMBEDDINGS ---------------- */
        val faceDetector = FaceDetector(this)
        val faceNet = MobileFaceNet(this)

        /* ---------------- LOCATION INTEGRITY ---------------- */
        val locationIntegrityGuard =
            LocationIntegrityGuard(
                context = this,
                policy = AttendancePolicyProvider.locationPolicy(),
                zonesPolicy = null, // explicit Phase‑1 decision
                locationSource = LocationSource(this),
                anchorStorage = SecureLocationAnchorStorage(this)
            )

        /* ---------------- FACE MATCHING (SINGLE AUTHORITY) --- */
        val referenceRepository =
            LocalEncryptedEmployeeReferenceRepository(this)

        val faceMatchingOrchestrator =
            FaceMatchingOrchestrator(
                policy = MatchingPolicy(
                    defaultThreshold = 1.1,
                    referenceValidationPolicy =
                        ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE
                ),
                referenceAccessPolicy = ReferenceAccessPolicy.LOCAL_ONLY,
                repository = referenceRepository
            )

        /* ---------------- FINAL DECISION AUTHORITY ----------- */
        val attendanceUseCase =
            AttendanceUseCase(
                timeIntegrityGuard =
                    TimeIntegrityGuard(
                        context = this,
                        policy = AttendancePolicyProvider.timePolicy(),
                        anchorStorage = SecureTimeAnchorStorage(this)
                    ),
                timeProofFactory =
                    AttendanceTimeProofFactory(
                        SecureTimeAnchorStorage(this),
                        GpsTimeProvider(this)
                    ),
                timeAnchorStorage =
                    SecureTimeAnchorStorage(this),
                zonesPolicy =
                    AttendancePolicyProvider.zonePolicy(),
                workingTimeEvaluator =
                    WorkingTimeEvaluator(
                        AttendancePolicyProvider.workingTimePolicy()
                    )
            )

        /* ---------------- ATOMIC REALITY BUILDER ------------- */
        val physicalRealityBuilder =
            PhysicalRealityBuilder(
                cameraManager = cameraManager,
                locationIntegrityGuard = locationIntegrityGuard
            )

        /* ---------------- RUNTIME ORCHESTRATOR --------------- */


        /* ---------------- FORENSIC AUDIT TRAIL (PHASE 3.4 + 3.5) -------- */
        val forensicAuditTrailWriter =
            ForensicAuditTrailWriter()

        return AttendanceRuntimeOrchestrator(
            physicalRealityBuilder = physicalRealityBuilder,
            faceDetector = faceDetector,
            faceNet = faceNet,
            livenessOrchestrator = null, // Phase‑1 disabled by policy
            facialMetricsEngine = FacialMetricsEngine(),
            faceMatchingOrchestrator = faceMatchingOrchestrator,
            attendanceUseCase = attendanceUseCase,
            auditTrailWriter = forensicAuditTrailWriter
        )

    }
}