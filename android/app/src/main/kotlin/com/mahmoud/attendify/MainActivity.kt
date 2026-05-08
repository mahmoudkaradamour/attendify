package com.mahmoud.attendify
//1
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import kotlinx.coroutines.*

/* ================= UI ================= */
import com.mahmoud.attendify.ui.onboarding.DisclosureActivity
import com.mahmoud.attendify.ui.controller.AttendanceUiController
import com.mahmoud.attendify.ui.state.AttendanceUiState

/* ================= CAMERA ================= */
import com.mahmoud.attendify.camera.*

/* ================= ORCHESTRATION ================= */
import com.mahmoud.attendify.orchestration.*

/* ================= DOMAIN ================= */
import com.mahmoud.attendify.attendance.usecase.*

/* ================= FACE ================= */
import com.mahmoud.attendify.face.*
import com.mahmoud.attendify.matching.*
import com.mahmoud.attendify.repository.local.*
import com.mahmoud.attendify.policy.*
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine

/* ================= SYSTEM ================= */
import com.mahmoud.attendify.system.time.*
import com.mahmoud.attendify.system.location.*
import com.mahmoud.attendify.system.time.working.*

/* ================= FORENSICS ================= */
import com.mahmoud.attendify.forensics.*
import com.mahmoud.attendify.forensics.repository.*
import com.mahmoud.attendify.forensics.db.*
import com.mahmoud.attendify.forensics.wal.*
import com.mahmoud.attendify.forensics.wal.db.WalDatabase

/* ================= SECURITY ================= */
import com.mahmoud.attendify.security.*
import com.mahmoud.attendify.security.legal.*

/* ================= LIFECYCLE ================= */
import com.mahmoud.attendify.attendance.lifecycle.*

/* ================= RECOVERY ================= */
import com.mahmoud.attendify.storage.recovery.*

/* ================= APP ================= */
import com.mahmoud.attendify.app.AppContextProvider
import com.mahmoud.attendify.attendance.config.AttendancePolicyProvider

/* ================= CHANNEL ================= */
import com.mahmoud.attendify.channel.AttendanceMethodChannel

/**
 * =============================================================================
 * 🧠 MainActivity — Composition Root + Reactive UI + Execution Gateway
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL ROLE
 * -----------------------------------------------------------------------------
 *
 * This class implements three major responsibilities:
 *
 * 1. Dependency Composition:
 *      Builds complete object graph (manual dependency injection)
 *
 * 2. UI Projection Layer:
 *      Maps system states → human-readable feedback
 *
 * 3. Execution Gateway:
 *      Connects Flutter / UI → Orchestrator
 *
 * -----------------------------------------------------------------------------
 * 📊 SYSTEM FLOW
 * -----------------------------------------------------------------------------
 *
 *   App Launch
 *       │
 *       ▼
 *   Disclosure Gate
 *       │
 *       ▼
 *   Initialize Systems
 *       │
 *       ▼
 *   Build Orchestrator
 *       │
 *       ▼
 *   Start Camera
 *       │
 *       ▼
 *   UI reacts to StateFlow
 *
 * -----------------------------------------------------------------------------
 * 🧠 UI MODEL (STATE-DRIVEN)
 * -----------------------------------------------------------------------------
 *
 *   AttendanceUiState
 *        │
 *        ▼
 *   UI Controller
 *        │
 *        ▼
 *   Text + Progress + Animation
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * Activity is UNTRUSTED:
 *   - Cannot make decisions
 *
 * Orchestrator is TRUSTED:
 *   - Executes verified pipeline
 *
 * -----------------------------------------------------------------------------
 * 🎬 UX GOAL
 * -----------------------------------------------------------------------------
 *
 * Transform raw execution into:
 *
 *   → Perceived intelligence
 *   → Smooth transitions
 *   → Trustworthy interface
 *
 * =============================================================================
 */
class MainActivity : FlutterActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: CameraManager
    private lateinit var orchestrator: AttendanceRuntimeOrchestrator

    private val uiController = AttendanceUiController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ================================================================
         * 🔐 DISCLOSURE GATE
         * ================================================================ */

        if (!isDisclosureAccepted()) {
            startActivity(Intent(this, DisclosureActivity::class.java))
            finish()
            return
        }

        /* ================================================================
         * 🧠 CORE INITIALIZATION
         * ================================================================ */

        AppContextProvider.initialize(applicationContext)
        ReplayProtectionGuard.initialize { applicationContext }

        val walManager =
            WalManager(WalDatabase.get(applicationContext).walDao())

        /* ================================================================
         * 🎥 UI LAYER (CAMERA + OVERLAY)
         * ================================================================ */

        val root = FrameLayout(this)

        previewView = PreviewView(this)

        val statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(40)
            alpha = 1f
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        root.addView(previewView)
        root.addView(statusText)
        root.addView(progress)

        setContentView(root)

        /* ================================================================
         * 📸 CAMERA
         * ================================================================ */

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = SystemStatusReporter {}
        )

        /* ================================================================
         * 🗄 FORENSIC LAYER
         * ================================================================ */

        val db = ForensicDatabase.getInstance(applicationContext)

        val auditWriter =
            ForensicAuditTrailWriter(
                ForensicAuditRepository(db.auditDao())
            )

        val legalWriter = LegalEvidenceWriter()

        /* ================================================================
         * 🧠 ORCHESTRATOR
         * ================================================================ */

        orchestrator = buildRuntime(
            cameraManager,
            auditWriter,
            legalWriter,
            walManager
        )

        /* ================================================================
         * 🔄 RECOVERY
         * ================================================================ */

        lifecycleScope.launch(Dispatchers.IO) {
            AttemptRecoveryManager(
                orchestrator.lifecycleManager,
                auditWriter
            ).runRecoveryCheck()
        }

        /* ================================================================
         * 🎬 REACTIVE UI BINDING (MODERN / SAFE)
         * ================================================================ */

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                var lastState: AttendanceUiState? = null

                uiController.state.collect { state ->

                    if (state == lastState) return@collect
                    lastState = state

                    val text = mapState(state)

                    animateText(statusText, text)
                    animateProgress(progress, state)
                }
            }
        }

        /* ================================================================
         * 🚀 START CAMERA
         * ================================================================ */

        cameraManager.startCamera(previewView.surfaceProvider)
    }

    /* =========================================================================
     * 🎬 TEXT ANIMATION
     * ========================================================================= */

    private fun animateText(view: TextView, newText: String) {

        view.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {

                view.text = newText

                view.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
    }

    /* =========================================================================
     * 🔄 PROGRESS ANIMATION
     * ========================================================================= */

    private fun animateProgress(progress: ProgressBar, state: AttendanceUiState) {

        val show =
            state !is AttendanceUiState.Idle &&
                    state !is AttendanceUiState.Success

        if (show && progress.visibility != View.VISIBLE) {

            progress.visibility = View.VISIBLE

            progress.scaleX = 0.8f
            progress.scaleY = 0.8f
            progress.alpha = 0f

            progress.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

        } else if (!show && progress.isVisible) {

            progress.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .withEndAction {
                    progress.visibility = View.GONE
                }
                .start()
        }
    }

    /* =========================================================================
     * 🔌 FLUTTER BRIDGE
     * ========================================================================= */

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)

        MethodChannel(
            engine.dartExecutor.binaryMessenger,
            "attendance_channel"
        ).setMethodCallHandler(
            AttendanceMethodChannel(orchestrator)
        )
    }

    /* =========================================================================
     * 🧱 DEPENDENCY GRAPH
     * ========================================================================= */

    private fun buildRuntime(
        camera: CameraManager,
        audit: ForensicAuditTrailWriter,
        legal: LegalEvidenceWriter,
        wal: WalManager
    ): AttendanceRuntimeOrchestrator {

        return AttendanceRuntimeOrchestrator(
            this,
            PhysicalRealityBuilder(
                camera,
                LocationIntegrityGuard(
                    this,
                    AttendancePolicyProvider.locationPolicy(),
                    null,
                    LocationSource(this),
                    SecureLocationAnchorStorage(this)
                )
            ),
            FaceDetector(this),
            MobileFaceNet(this),
            null,
            FacialMetricsEngine(),
            FaceMatchingOrchestrator(
                MatchingPolicy(1.1, ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE),
                ReferenceAccessPolicy.LOCAL_ONLY,
                LocalEncryptedEmployeeReferenceRepository(this)
            ),
            AttendanceUseCase(
                TimeIntegrityGuard(
                    this,
                    AttendancePolicyProvider.timePolicy(),
                    SecureTimeAnchorStorage(this)
                ),
                AttendanceTimeProofFactory(
                    SecureTimeAnchorStorage(this),
                    GpsTimeProvider(this)
                ),
                SecureTimeAnchorStorage(this),
                AttendancePolicyProvider.zonePolicy(),
                WorkingTimeEvaluator(
                    AttendancePolicyProvider.workingTimePolicy()
                )
            ),
            audit,
            legal,
            wal,
            AttemptLifecycleManager(applicationContext)
        )
    }

    /* =========================================================================
     * 🧠 STATE → TEXT
     * ========================================================================= */

    private fun mapState(state: AttendanceUiState): String =
        when (state) {
            AttendanceUiState.Idle -> "Ready"
            AttendanceUiState.Securing -> "Securing..."
            AttendanceUiState.Capturing -> "Capturing..."
            AttendanceUiState.Processing -> "Verifying..."
            AttendanceUiState.Success -> "✅ Success"
            is AttendanceUiState.Error -> "❌ ${state.message}"
        }

    /* =========================================================================
     * 🔐 DISCLOSURE CHECK
     * ========================================================================= */

    private fun isDisclosureAccepted(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getBoolean("disclosure_accepted", false)
    }
}