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

/* ============================================================================ */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.SystemStatusReporter
/* ============================================================================ */
import com.mahmoud.attendify.orchestration.AttendanceRuntimeOrchestrator
import com.mahmoud.attendify.orchestration.PhysicalRealityBuilder
/* ============================================================================ */
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase
/* ============================================================================ */
import com.mahmoud.attendify.face.*
import com.mahmoud.attendify.matching.*
import com.mahmoud.attendify.repository.local.LocalEncryptedEmployeeReferenceRepository
import com.mahmoud.attendify.policy.*
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
/* ============================================================================ */
import com.mahmoud.attendify.system.time.*
import com.mahmoud.attendify.system.location.*
import com.mahmoud.attendify.system.time.working.*
/* ============================================================================ */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.repository.ForensicAuditRepository
import com.mahmoud.attendify.forensics.db.*
import com.mahmoud.attendify.forensics.wal.WalManager
/* ============================================================================ */
import com.mahmoud.attendify.security.*
import com.mahmoud.attendify.security.legal.LegalEvidenceWriter
/* ============================================================================ */
import com.mahmoud.attendify.storage.recovery.AttemptRecoveryManager
/* ============================================================================ */
import com.mahmoud.attendify.attendance.config.AttendancePolicyProvider
import com.mahmoud.attendify.channel.AttendanceMethodChannel

/**
 * =============================================================================
 * 🧠 MainActivity — System Composition Root
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ARCHITECTURAL ROLE
 * -----------------------------------------------------------------------------
 *
 * This class is the **only place where objects are constructed**.
 *
 * It defines:
 *
 *   ✅ Object graph (Dependency Injection without framework)
 *   ✅ System startup sequence
 *   ✅ Execution pipeline wiring
 *
 * -----------------------------------------------------------------------------
 * 📊 SYSTEM INITIALIZATION FLOW
 * -----------------------------------------------------------------------------
 *
 *   App Start
 *       │
 *       ▼
 *   Initialize Context
 *       │
 *       ▼
 *   Initialize Security Guards
 *       │
 *       ▼
 *   Initialize WAL (transaction log)
 *       │
 *       ▼
 *   Initialize Forensic Ledger
 *       │
 *       ▼
 *   Verify Integrity Chain
 *       │
 *       ▼
 *   Build Runtime Orchestrator
 *       │
 *       ▼
 *   Recovery Check
 *       │
 *       ▼
 *   Start Camera Preview
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * UI Layer  → UNTRUSTED
 * Activity  → CONFIGURATION ONLY
 * Core      → TRUSTED EXECUTION ENGINE
 *
 */
class MainActivity : FlutterActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: CameraManager
    private lateinit var orchestrator: AttendanceRuntimeOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ================================================================
         * 🧠 GLOBAL CONTEXT
         * ================================================================ */
        com.mahmoud.attendify.app.AppContextProvider
            .initialize(applicationContext)

        /* ================================================================
         * 🔐 REPLAY PROTECTION
         * ================================================================ */
        ReplayProtectionGuard.initialize { applicationContext }

        /* ================================================================
         * 🧾 WAL MANAGER (TRANSACTION JOURNAL)
         * ================================================================ */
        val walManager = WalManager()

        /**
         * WAL guarantees:
         *
         *   BEGIN → PROCESS → COMMIT
         *
         * If the system crashes:
         *   → WAL reveals incomplete transaction
         */

        /* ================================================================
         * 🎥 UI LAYER
         * ================================================================ */
        previewView = PreviewView(this)
        setContentView(previewView)

        /* ================================================================
         * 📸 CAMERA
         * ================================================================ */
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = SystemStatusReporter {}
        )

        /* ================================================================
         * 🗄 FORENSIC LEDGER
         * ================================================================ */
        val db = androidx.room.Room.databaseBuilder(
            applicationContext,
            ForensicDatabase::class.java,
            "forensic.db"
        ).build()

        val repository = ForensicAuditRepository(db.auditDao())
        val auditWriter = ForensicAuditTrailWriter(repository)

        /* ================================================================
         * 🔐 CHAIN VERIFICATION
         * ================================================================ */
        CoroutineScope(Dispatchers.IO).launch {

            val valid = repository.verifyChain()

            if (!valid) {
                auditWriter.appendSystemEvent(
                    "LEDGER_TAMPER_DETECTED",
                    "Integrity chain broken"
                )
            }
        }

        /* ================================================================
         * ⚖️ LEGAL LAYER
         * ================================================================ */
        val legalEvidenceWriter = LegalEvidenceWriter()

        /* ================================================================
         * 🧠 ORCHESTRATOR CONSTRUCTION
         * ================================================================ */
        orchestrator = buildAttendanceRuntime(
            cameraManager,
            auditWriter,
            legalEvidenceWriter,
            walManager
        )

        /* ================================================================
         * 🧯 RECOVERY SYSTEM
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

    private fun startCameraPreview() {
        cameraManager.startCamera(
            preview = previewView.surfaceProvider
        )
    }

    /* =========================================================================
     * 🧠 DEPENDENCY GRAPH CONSTRUCTION
     * ========================================================================= */

    private fun buildAttendanceRuntime(
        cameraManager: CameraManager,
        auditWriter: ForensicAuditTrailWriter,
        legalEvidenceWriter: LegalEvidenceWriter,
        walManager: WalManager
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

        val physicalRealityBuilder =
            PhysicalRealityBuilder(
                cameraManager,
                locationIntegrityGuard
            )

        val lifecycleManager =
            com.mahmoud.attendify.attendance.lifecycle
                .AttemptLifecycleManager(applicationContext)

        /* ---------------- FINAL ENGINE ---------------- */
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
            walManager = walManager, // ✅ FIX
            lifecycleManager = lifecycleManager
        )
    }
}