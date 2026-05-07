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
import kotlinx.coroutines.*

/* ============================================================================
 * CAMERA & SYSTEM
 * ========================================================================== */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.SystemStatusReporter

/* ============================================================================
 * ORCHESTRATION
 * ========================================================================== */
import com.mahmoud.attendify.orchestration.AttendanceRuntimeOrchestrator
import com.mahmoud.attendify.orchestration.PhysicalRealityBuilder

/* ============================================================================
 * DOMAIN
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
 * TIME & LOCATION
 * ========================================================================== */
import com.mahmoud.attendify.system.time.*
import com.mahmoud.attendify.system.location.*
import com.mahmoud.attendify.system.time.working.*

/* ============================================================================
 * FORENSICS
 * ========================================================================== */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.repository.ForensicAuditRepository
import com.mahmoud.attendify.forensics.db.*

/* ============================================================================
 * SECURITY
 * ========================================================================== */
import com.mahmoud.attendify.security.ReplayProtectionGuard
import com.mahmoud.attendify.security.legal.LegalEvidenceWriter

/* ============================================================================
 * RECOVERY
 * ========================================================================== */
import com.mahmoud.attendify.storage.recovery.AttemptRecoveryManager

/* ============================================================================
 * CONFIG + CHANNEL
 * ========================================================================== */
import com.mahmoud.attendify.attendance.config.AttendancePolicyProvider
import com.mahmoud.attendify.channel.AttendanceMethodChannel

/**
 * =============================================================================
 * 🛡 MainActivity — System Composition Root (Deterministic Assembly Layer)
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE (ARCHITECTURAL)
 * -----------------------------------------------------------------------------
 *
 * This class represents the **Composition Root** of the system.
 *
 * It is responsible for:
 *
 *   ✔ Dependency Graph Construction
 *   ✔ System Initialization (Bootstrapping)
 *   ✔ Wiring execution pipeline
 *
 * It MUST NOT contain:
 *
 *   ✖ Business logic
 *   ✖ Security decisions
 *   ✖ Biometric processing
 *
 * -----------------------------------------------------------------------------
 * 🧠 TRUST MODEL
 * -----------------------------------------------------------------------------
 *
 *   UI Layer (Flutter) → UNTRUSTED INPUT
 *   MainActivity        → CONFIGURATION ONLY
 *   Orchestrator        → TRUST AUTHORITY
 *
 * -----------------------------------------------------------------------------
 * 📊 SYSTEM STARTUP FLOW
 * -----------------------------------------------------------------------------
 *
 *   App Launch
 *       │
 *       ▼
 *   Initialize global context
 *       │
 *       ▼
 *   Initialize security guards
 *       │
 *       ▼
 *   Load persistent forensic ledger
 *       │
 *       ▼
 *   Verify chain integrity
 *       │
 *       ▼
 *   Build full runtime pipeline
 *       │
 *       ▼
 *   Run recovery checks
 *       │
 *       ▼
 *   Start camera preview
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Replay protection initialized before any action
 * ✅ Persistent ledger verified on startup
 * ✅ Abnormal shutdowns detected (recovery layer)
 * ✅ Dependency graph cannot be altered at runtime
 *
 */
class MainActivity : FlutterActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: CameraManager
    private lateinit var orchestrator: AttendanceRuntimeOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ================================================================
         * 🧠 GLOBAL CONTEXT INITIALIZATION
         * ================================================================ */
        com.mahmoud.attendify.app.AppContextProvider
            .initialize(applicationContext)

        /* ================================================================
         * 🔐 REPLAY PROTECTION (EARLY INIT)
         * ================================================================ */
        ReplayProtectionGuard.initialize {
            applicationContext
        }

        /* ================================================================
         * 🎥 UI LAYER (STRICTLY PASSIVE)
         * ================================================================ */
        previewView = PreviewView(this)
        setContentView(previewView)

        /* ================================================================
         * 📸 CAMERA LAYER (HARDWARE ACCESS ONLY)
         * ================================================================ */
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = SystemStatusReporter {}
        )

        /* ================================================================
         * 🗄 PERSISTENT FORENSIC LEDGER (D1)
         * ================================================================ */
        val db = androidx.room.Room.databaseBuilder(
            applicationContext,
            ForensicDatabase::class.java,
            "forensic.db"
        ).build()

        val repository = ForensicAuditRepository(db.auditDao())
        val auditWriter = ForensicAuditTrailWriter(repository)

        /* ================================================================
         * 🔐 ANTI-TAMPER CHAIN VERIFICATION (D2)
         * ================================================================ */
        CoroutineScope(Dispatchers.IO).launch {

            val valid = repository.verifyChain()

            if (!valid) {
                auditWriter.appendSystemEvent(
                    event = "LEDGER_TAMPER_DETECTED",
                    details = "Forensic chain integrity violation"
                )
            }
        }

        /* ================================================================
         * 🧾 LEGAL EVIDENCE WRITER (SEPARATE CHANNEL)
         * ================================================================ */
        val legalEvidenceWriter = LegalEvidenceWriter()

        /* ================================================================
         * 🧠 BUILD FULL RUNTIME ENGINE
         * ================================================================ */
        orchestrator = buildAttendanceRuntime(
            cameraManager,
            auditWriter,
            legalEvidenceWriter
        )

        /* ================================================================
         * 🧯 RECOVERY SYSTEM (ANTI-SILENT-FAILURE)
         * ================================================================ */
        val recoveryManager = AttemptRecoveryManager(
            lifecycleManager = orchestrator.lifecycleManager,
            auditWriter = auditWriter
        )

        CoroutineScope(Dispatchers.IO).launch {
            recoveryManager.runRecoveryCheck()
        }

        /* ================================================================
         * 🔑 PERMISSIONS
         * ================================================================ */
        checkCameraPermissionAndStart()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "attendance_channel"
        ).setMethodCallHandler(
            AttendanceMethodChannel(orchestrator)
        )
    }

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
     * Camera preview ONLY — no capture logic allowed here
     */
    private fun startCameraPreview() {
        cameraManager.startCamera(
            preview = previewView.surfaceProvider
        )
    }

    /* =========================================================================
     * 🧠 COMPOSITION ROOT — FULL DEPENDENCY GRAPH
     * ========================================================================= */

    private fun buildAttendanceRuntime(
        cameraManager: CameraManager,
        auditWriter: ForensicAuditTrailWriter,
        legalEvidenceWriter: LegalEvidenceWriter
    ): AttendanceRuntimeOrchestrator {

        /* ---------------- BIOMETRICS ---------------- */
        val faceDetector = FaceDetector(this)
        val faceNet = MobileFaceNet(this)

        val faceMatchingOrchestrator =
            FaceMatchingOrchestrator(
                policy = MatchingPolicy(
                    defaultThreshold = 1.1,
                    referenceValidationPolicy =
                        ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE
                ),
                referenceAccessPolicy = ReferenceAccessPolicy.LOCAL_ONLY,
                repository =
                    LocalEncryptedEmployeeReferenceRepository(this)
            )

        /* ---------------- LOCATION ---------------- */
        val locationIntegrityGuard =
            LocationIntegrityGuard(
                context = this,
                policy = AttendancePolicyProvider.locationPolicy(),
                zonesPolicy = null,
                locationSource = LocationSource(this),
                anchorStorage = SecureLocationAnchorStorage(this)
            )

        /* ---------------- DOMAIN ---------------- */
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

        /* ---------------- REALITY BUILDER ---------------- */
        val physicalRealityBuilder =
            PhysicalRealityBuilder(
                cameraManager = cameraManager,
                locationIntegrityGuard = locationIntegrityGuard
            )

        /* ---------------- LIFECYCLE ---------------- */
        val lifecycleManager =
            com.mahmoud.attendify.attendance.lifecycle
                .AttemptLifecycleManager(applicationContext)

        /* ---------------- ORCHESTRATOR ---------------- */
        return AttendanceRuntimeOrchestrator(
            physicalRealityBuilder = physicalRealityBuilder,
            faceDetector = faceDetector,
            faceNet = faceNet,
            livenessOrchestrator = null,
            facialMetricsEngine = FacialMetricsEngine(),
            faceMatchingOrchestrator = faceMatchingOrchestrator,
            attendanceUseCase = attendanceUseCase,
            auditTrailWriter = auditWriter,
            legalEvidenceWriter = legalEvidenceWriter,
            lifecycleManager = lifecycleManager
        )
    }
}