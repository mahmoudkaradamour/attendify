package com.mahmoud.attendify

/* 22=========================================================
 * Android & Flutter
 * ========================================================= */
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
import com.mahmoud.attendify.attendance.AttendanceSession
/* =========================================================
 * ML Runtime (CPU / GPU)
 * ========================================================= */
import com.mahmoud.attendify.ml.InterpreterFactory
import com.mahmoud.attendify.ml.GpuPolicy
import com.mahmoud.attendify.ml.GpuStatus
import com.mahmoud.attendify.ml.InterpreterCapabilities

/* =========================================================
 * Camera & Image Quality
 * ========================================================= */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.FrameAnalyzer
import com.mahmoud.attendify.camera.ImageConverter
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus
import com.mahmoud.attendify.camera.SystemStatusReporter

/* =========================================================
 * Face & Feature Extraction
 * ========================================================= */
import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

/* =========================================================
 * Passive / Active Liveness
 * ========================================================= */
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* =========================================================
 * Face Anti-Spoofing (FAS)
 * ========================================================= */
import com.mahmoud.attendify.fas.models.MiniFASNetV2Model
import com.mahmoud.attendify.fas.runtime.FASOrchestrator

/* =========================================================
 * Matching & Attendance
 * ========================================================= */
import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.policy.*
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
import com.mahmoud.attendify.fas.models.FastFASNetV3Model
import com.mahmoud.attendify.fas.models.MiniFASNetV1SEModel

/**
 * MainActivity
 *
 * هذا الكلاس هو:
 * ✅ نقطة التجميع الوحيدة (Composition Root)
 * ✅ المكان الوحيد لإنشاء وربط كل Native Core
 *
 * ❌ لا يحتوي منطق أعمال
 * ❌ لا يقرر نجاح أو فشل حضور
 *
 * Flutter:
 * - يحدد السياق
 * - يرسل السياسات
 *
 * Native:
 * - ينفذ
 * - ويرجع قرارًا نهائيًا
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
     * Executor لكل العمليات الثقيلة:
     * - Face Detection
     * - FAS
     * - Liveness
     * - Embedding
     *
     * ❗ ممنوع تنفيذ أي ML على Thread الكاميرا
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

        /* ---------------------------------------------------------
         * 1) System Status Reporter
         * ---------------------------------------------------------
         * ينقل أي حالة من Native → Flutter
         * Flutter هو من يقرر كيف يعرضها للمستخدم
         */
        statusReporter = SystemStatusReporter { status ->
            Log.d("SystemStatus", status.name)
        }

        /* ---------------------------------------------------------
         * 2) إنشاء AttendanceUseCase
         * ---------------------------------------------------------
         * هذه هي نقطة الإغلاق المعماري:
         * كل ما يحتاجه النظام يُنشأ هنا
         */
        val attendanceUseCase = provideAttendanceUseCase()

        /* ---------------------------------------------------------
         * 3) MethodChannel
         * ---------------------------------------------------------
         * Flutter:
         * - يستدعي startAttendance
         * - يمرر employeeId والسياسات
         *
         * Native:
         * - ينفذ
         * - يعيد AttendanceDecision
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
     * Dependency Providers (Manual Dependency Injection)
     * ========================================================= */

    /**
     * FaceMatchingUseCase
     *
     * - Repository مشفر
     * - Matching policy
     * - Access strategy
     */
    private fun provideFaceMatchingUseCase(): FaceMatchingUseCase {

        val repository =
            LocalEncryptedEmployeeReferenceRepository(this)

        return FaceMatchingUseCase(
            policy = MatchingPolicy(
                defaultThreshold = 1.2,
                referenceValidationPolicy =
                    ReferenceValidationPolicy.VALIDATE_ONCE_AT_ENROLLMENT
            ),
            referenceAccessPolicy = ReferenceAccessPolicy.HYBRID,
            repository = repository,
            groupThreshold = null
        )
    }

    /**
     * FAS Orchestrator
     *
     * هنا نربط كل نماذج FAS المتاحة
     * بدون أي منطق قرار
     */
    private fun provideFASOrchestrator(): FASOrchestrator {

        val fasModels = mapOf(

            "minifasnet_v2_80x80_default" to MiniFASNetV2Model(applicationContext),
            "minifasnet_v1se_80x80_light" to MiniFASNetV1SEModel(applicationContext),
            "fastfasnet_v3_128x128_highsec" to FastFASNetV3Model(applicationContext)

        )

        return FASOrchestrator(fasModels)
    }

    /**
     * AttendanceUseCase
     *
     * ✅ هذا هو Gate القرار النهائي للحضور
     * ✅ كل الانتهاكات الأمنية تُفلتر هنا
     */
    private fun provideAttendanceUseCase(): AttendanceUseCase {
        return AttendanceUseCase(
            faceMatchingUseCase = provideFaceMatchingUseCase(),
            fasOrchestrator = provideFASOrchestrator()
        )
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

                    /* 1) Quality Check */
                    if (
                        ImageQualityChecker.checkFrame(bitmap)
                        != SystemStatus.OK
                    ) return@execute



                    /* 2) Face Detection */
                    val detection =
                        faceDetector.detectBestFace(bitmap)
                            ?: return@execute

                    /* 3) Crop Face */
                    val faceBitmap =
                        FaceCropper.cropFace(
                            bitmap,
                            detection.box
                        ) ?: return@execute
// ✅ تخزين آخر وجه صالح للجلسة الحالية
                    AttendanceSession.updateFace(faceBitmap)
                    /* 4) Active Liveness */
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

                    /* 5) Embedding Extraction */
                    val embedding =
                        faceNet.getEmbedding(faceBitmap)

                    // ✅ الوجه جاهز
                    // ✅ FAS + Matching سيتمان عبر AttendanceUseCase
                    // ✅ القرار النهائي يأتي من Flutter

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