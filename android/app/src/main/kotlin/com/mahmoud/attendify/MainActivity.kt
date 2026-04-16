package com.mahmoud.attendify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/* =========================================================
 * Camera & Image Quality
 * ================================================================ */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.FrameAnalyzer
import com.mahmoud.attendify.camera.ImageConverter
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus
import com.mahmoud.attendify.camera.SystemStatusReporter

/* =========================================================
 * Face & ML
 * ========================================================= */
import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

/* =========================================================
 * Liveness
 * ========================================================= */
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.policy.LivenessPolicy
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* =========================================================
 * Matching & Attendance
 * ========================================================= */
import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy
import com.mahmoud.attendify.repository.local.LocalEncryptedEmployeeReferenceRepository
import com.mahmoud.attendify.attendance.AttendanceUseCase

/* =========================================================
 * Audit Logging
 * ========================================================= */
import com.mahmoud.attendify.audit.local.LocalEncryptedAttendanceAuditLogger

/* =========================================================
 * MethodChannel Bridge
 * ========================================================= */
import com.mahmoud.attendify.channel.AttendanceMethodChannel

/**
 * MainActivity
 *
 * ====================== IMPORTANT ======================
 *
 * هذا الكلاس هو:
 * ✅ نقطة التجميع (Composition Root) لكل Native Core
 * ✅ المكان الوحيد الذي يتم فيه:
 *    - إنشاء الكائنات
 *    - حقن الاعتمادات (Dependency Injection)
 *    - ربط Flutter مع Native
 *
 * ❌ لا يحتوي منطق أعمال
 * ❌ لا يقرر حضور
 * ❌ لا يعرف الوقت أو الموقع
 *
 * Flutter هو من يقرر:
 * - متى نبدأ
 * - هل السياق صالح
 *
 * Native:
 * - ينفّذ التحقق البيومتري فقط
 * - ويُعيد قرارًا نهائيًا
 */
class MainActivity : FlutterActivity() {

    /* =========================================================
     * Core Runtime Components
     * ========================================================= */

    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceNet: MobileFaceNet
    private lateinit var statusReporter: SystemStatusReporter

    /* =========================================================
     * Executors
     * ========================================================= */

    /**
     * Executor مخصص لكل ما هو ثقيل:
     * - Face detection
     * - Liveness
     * - Embedding
     *
     * ❗ لا ML على thread الكاميرا
     */
    private val inferenceExecutor =
        Executors.newSingleThreadExecutor()

    /* =========================================================
     * Liveness
     * ========================================================= */

    private lateinit var livenessOrchestrator: LivenessOrchestrator
    private val facialMetricsEngine = FacialMetricsEngine()

    /* =========================================================
     * Constants
     * ========================================================= */

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val ATTENDANCE_CHANNEL = "attendance_channel"
    }

    /* =========================================================
     * Flutter Engine Setup
     * ========================================================= */

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        /**
         * ✅ 1) تجهيز SystemStatusReporter
         *
         * هذا الكلاس:
         * - ينقل أي حالة من Native → Flutter
         * - Flutter يبني UX بناءً عليه
         */
        statusReporter = SystemStatusReporter { status ->
            Log.d("SystemStatus", status.name)
        }

        /**
         * ✅ 2) حقن كل Native Core هنا
         *
         * هذه هي نقطة الإغلاق المعماري:
         * بعد هذا السطر لا يوجد TODO
         */
        val attendanceUseCase = provideAttendanceUseCase()

        /**
         * ✅ 3) إنشاء MethodChannel
         *
         * Flutter هو من:
         * - يستدعي startAttendance
         * - يمرر employeeId
         *
         * Native:
         * - ينفّذ
         * - ويُعيد AttendanceDecision
         */
        val channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            ATTENDANCE_CHANNEL
        )

        channel.setMethodCallHandler(
            AttendanceMethodChannel(attendanceUseCase)
        )
    }

    /* =========================================================
     * Dependency Providers (Manual DI)
     * ========================================================= */

    /**
     * MatchingPolicy
     *
     * سياسة افتراضية على مستوى Native
     * سيتم override لاحقًا من Flutter
     */
    private fun provideMatchingPolicy(): MatchingPolicy {
        return MatchingPolicy(
            defaultThreshold = 1.2,
            referenceValidationPolicy =
                ReferenceValidationPolicy.VALIDATE_ONCE_AT_ENROLLMENT
        )
    }

    /**
     * FaceMatchingUseCase
     *
     * هنا يتم حقن:
     * - Repository مشفّر
     * - Policy
     * - Access strategy (LOCAL / REMOTE / HYBRID)
     */
    private fun provideFaceMatchingUseCase(): FaceMatchingUseCase {

        val repository =
            LocalEncryptedEmployeeReferenceRepository(this)

        return FaceMatchingUseCase(
            policy = provideMatchingPolicy(),
            referenceAccessPolicy = ReferenceAccessPolicy.HYBRID,
            repository = repository,
            groupThreshold = null
        )
    }

    /**
     * AttendanceUseCase
     *
     * ✅ هذا هو القرار النهائي للحضور داخل Native
     */
    private fun provideAttendanceUseCase(): AttendanceUseCase {
        return AttendanceUseCase(
            faceMatchingUseCase = provideFaceMatchingUseCase()
        )
    }

    /**
     * Audit Logger (Encrypted)
     *
     * Native يسجل فقط
     * Flutter سيقرر متى يزامن
     */
    private fun provideAuditLogger(): LocalEncryptedAttendanceAuditLogger {
        return LocalEncryptedAttendanceAuditLogger(this)
    }

    /* =========================================================
     * Activity Lifecycle
     * ========================================================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasCameraPermission()) {
            startCameraPipeline()
        } else {
            requestCameraPermission()
        }
    }

    /* =========================================================
     * Camera Pipeline
     * ========================================================= */

    private fun startCameraPipeline() {

        val previewView =
            findViewById<PreviewView>(R.id.previewView)

        faceDetector = FaceDetector(this)
        faceNet = MobileFaceNet(this)

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = statusReporter
        )

        val analyzer = FrameAnalyzer { imageProxy ->

            val bitmap =
                ImageConverter.imageProxyToBitmap(imageProxy)

            inferenceExecutor.execute {
                try {
                    if (
                        ImageQualityChecker.checkFrame(bitmap)
                        != SystemStatus.OK
                    ) {
                        return@execute
                    }

                    val detection =
                        faceDetector.detectBestFace(bitmap)
                            ?: return@execute

                    val faceBitmap =
                        FaceCropper.cropFace(
                            bitmap,
                            detection.box
                        ) ?: return@execute

                    val metrics =
                        facialMetricsEngine
                            .computeFrameFromBitmap(faceBitmap)

                    livenessOrchestrator.onFrame(metrics)

                    if (
                        livenessOrchestrator.evaluate()
                        == LivenessResult.SpoofDetected
                    ) {
                        statusReporter.report(
                            SystemStatus.SPOOF_DETECTED
                        )
                        return@execute
                    }

                    val embedding =
                        faceNet.getEmbedding(faceBitmap)

                    // ✅ embedding جاهز
                    // ✅ القرار سيأتي لاحقًا من Flutter

                } catch (e: Exception) {
                    statusReporter.report(
                        SystemStatus.INTERNAL_ERROR
                    )
                }
            }

            imageProxy.close()
        }

        cameraManager.startCamera(
            preview = previewView.surfaceProvider,
            analyzer = analyzer
        )
    }

    /* =========================================================
     * Permissions
     * ========================================================= */

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    /* =========================================================
     * Cleanup
     * ========================================================= */

    override fun onDestroy() {
        super.onDestroy()
        inferenceExecutor.shutdown()
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
    }
}