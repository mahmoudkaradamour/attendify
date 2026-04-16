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
 * Imports من طبقات المشروع المختلفة
 * ========================================================= */

// 1) طبقة الكاميرا + جودة الصورة (Hardware & Pre‑ML)
import com.mahmoud.attendify.camera.*

// 2) طبقة الذكاء الاصطناعي للوجوه (Detection & Embedding)
import com.mahmoud.attendify.face.*

// 3) تحليل الجهاز والتكيّف مع قدراته
import com.mahmoud.attendify.device.DeviceProfiler
import com.mahmoud.attendify.config.AdaptiveConfig
import com.mahmoud.attendify.config.AdaptiveConfigResolver

// 4) جمع قياسات الأداء (observability)
import com.mahmoud.attendify.metrics.RuntimeMetricsCollector

// 5) نظام Liveness الزمني الحقيقي
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.policy.LivenessPolicy
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/**
 * MainActivity
 *
 * هذا الملف هو "منسّق النظام" (Coordinator).
 *
 * ✅ ما الذي يفعله؟
 * - يدير دورة حياة الكاميرا.
 * - ينسّق الـ Pipeline بالترتيب الصحيح.
 * - يطبّق Throttling و Async execution.
 * - يربط كل الطبقات معًا بدون حسابات داخلية.
 *
 * ❌ ما الذي لا يفعله؟
 * - لا يحسب EAR.
 * - لا يحسب yaw / pitch.
 * - لا يقرر إذا كان المستخدم حيًا.
 * - لا يقرر تطابق الوجه.
 *
 * كل حساب أو قرار موجود في ملفه الصحيح.
 */
class MainActivity : FlutterActivity() {

    /* =========================================================
     * Core runtime components
     * ========================================================= */

    // مدير الكاميرا: مسؤول فقط عن CameraX + lifecycle
    private lateinit var cameraManager: CameraManager

    // كاشف الوجه (BlazeFace أو مكافئه)
    private lateinit var faceDetector: FaceDetector

    // نموذج MobileFaceNet لاستخراج الـ embedding
    private lateinit var faceNet: MobileFaceNet

    // إرسال حالات النظام (أخطاء، تحذيرات…) للطبقة الأعلى
    private lateinit var statusReporter: SystemStatusReporter


    /* =========================================================
     * Adaptive system configuration
     * ========================================================= */

    /**
     * هذه القيم تُبنى مرة واحدة عند التشغيل
     * بناءً على قدرة الجهاز (CPU / RAM).
     *
     * لا تُغيّر أثناء التشغيل.
     */
    private lateinit var adaptiveConfig: AdaptiveConfig

    /**
     * يُستخدم لعملية Throttling.
     * يمنع معالجة كل frame.
     */
    private var lastProcessedFrameTime = 0L


    /* =========================================================
     * Executors
     * ========================================================= */

    /**
     * Executor مخصص لكل ما هو ثقيل:
     * - Face detection
     * - Liveness
     * - Embedding
     *
     * ❗ مهم: لا يتم تشغيل أي ML على Thread الكاميرا.
     */
    private val inferenceExecutor =
        Executors.newSingleThreadExecutor()


    /* =========================================================
     * Liveness system
     * ========================================================= */

    /**
     * orchestrator يجمع frames عبر الزمن
     * ويطبّق السياسة (Blink, Smile, Pose…).
     */
    private lateinit var livenessOrchestrator: LivenessOrchestrator

    /**
     * محرّك حساب القياسات.
     * هو المكان الوحيد المسموح له بحساب:
     * EAR / Pose / Lighting…
     */
    private val facialMetricsEngine = FacialMetricsEngine()


    /* =========================================================
     * Flutter bridge (واجهة فقط – بدون منطق)
     * ========================================================= */

    private lateinit var methodChannel: MethodChannel


    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val CHANNEL_NAME = "attendify/system"
    }


    /* =========================================================
     * Flutter engine setup
     * ========================================================= */

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // القناة تُستخدم فقط لإرسال حالات النظام
        methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME
        )

        statusReporter = SystemStatusReporter { status ->
            methodChannel.invokeMethod(
                "onSystemStatus",
                status.name
            )
        }
    }


    /* =========================================================
     * Activity lifecycle
     * ========================================================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /* -----------------------------------------
         * (1) تحليل الجهاز والتكيّف معه
         * ----------------------------------------- */

        val deviceProfile = DeviceProfiler.profile(this)
        adaptiveConfig = AdaptiveConfigResolver.resolve(deviceProfile)

        Log.d("DeviceProfile", deviceProfile.toString())
        Log.d("AdaptiveConfig", adaptiveConfig.toString())

        /* -----------------------------------------
         * (2) تعريف سياسة الـ Liveness
         * ----------------------------------------- */

        val livenessPolicy = LivenessPolicy(
            requireBlink = true,
            minBlinkCount = 1,
            allowSimultaneousBlink = false,
            eyeClosedThreshold = 0.25,

            requireSmile = false,
            minSmileScore = 0.6,
            minSmileDurationMs = 400,

            requireMouthOpen = false,
            minMouthOpenFrames = 6,

            requireYaw = false,
            minYawDegrees = 12.0,

            requirePitch = false,
            minPitchDegrees = 10.0,

            requirePhotometricResponse = false,
            luminanceVarianceThreshold = 18.0
        )

        livenessOrchestrator =
            LivenessOrchestrator(livenessPolicy)

        /* -----------------------------------------
         * (3) التحقق من صلاحية الكاميرا
         * ----------------------------------------- */

        if (hasCameraPermission()) {
            startCameraPipeline()
        } else {
            requestCameraPermission()
        }
    }


    /* =========================================================
     * Camera + Face pipeline
     * ========================================================= */

    private fun startCameraPipeline() {

        val previewView =
            findViewById<PreviewView>(R.id.previewView)

        // إنشاء مكونات الذكاء الاصطناعي
        faceDetector = FaceDetector(this)
        faceNet = MobileFaceNet(this)

        // ربط CameraX بالـ lifecycle
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = statusReporter
        )

        val analyzer = FrameAnalyzer { imageProxy ->

            /* -----------------------------------------
             * Throttling
             * ----------------------------------------- */
            if (!shouldProcessFrame()) {
                RuntimeMetricsCollector.recordDrop()
                imageProxy.close()
                return@FrameAnalyzer
            }

            // تحويل الإطار إلى Bitmap
            // (مرحلة يمكن تحسينها مستقبلًا بتجاوز Bitmap)
            val bitmap =
                ImageConverter.imageProxyToBitmap(imageProxy)

            /* -----------------------------------------
             * Async ML pipeline
             * ----------------------------------------- */
            inferenceExecutor.execute {

                val start = System.currentTimeMillis()

                try {
                    /* (1) فحص جودة الإطار */
                    val frameStatus =
                        ImageQualityChecker.checkFrame(bitmap)
                    if (frameStatus != SystemStatus.OK) {
                        statusReporter.report(frameStatus)
                        return@execute
                    }

                    /* (2) كشف الوجه */
                    val detection =
                        faceDetector.detectBestFace(bitmap)
                            ?: return@execute

                    /* (3) قص الوجه */
                    val faceBitmap =
                        FaceCropper.cropFace(
                            bitmap,
                            detection.box
                        ) ?: return@execute

                    /* (4) فحص حجم الوجه */
                    val faceStatus =
                        ImageQualityChecker.checkFaceSize(
                            faceBitmap.width,
                            faceBitmap.height,
                            bitmap.width,
                            bitmap.height
                        )
                    if (faceStatus != SystemStatus.OK) {
                        statusReporter.report(faceStatus)
                        return@execute
                    }

                    /* (5) حساب قياسات الوجه */
                    val metricsFrame =
                        facialMetricsEngine
                            .computeFrameFromBitmap(faceBitmap)

                    /* (6) تمرير القياسات إلى Liveness */
                    livenessOrchestrator.onFrame(metricsFrame)

                    val livenessResult =
                        livenessOrchestrator.evaluate()

                    if (livenessResult == LivenessResult.SpoofDetected) {
                        statusReporter.report(
                            SystemStatus.SPOOF_DETECTED
                        )
                        return@execute
                    }

                    /* (7) استخراج الـ embedding */
                    faceNet.getEmbedding(faceBitmap)

                    RuntimeMetricsCollector.recordInference(
                        System.currentTimeMillis() - start
                    )

                } catch (ex: Exception) {
                    Log.e("Pipeline", "Fatal error", ex)
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
     * Frame throttling
     * ========================================================= */

    private fun shouldProcessFrame(): Boolean {
        val now = System.currentTimeMillis()
        val minInterval =
            1000 / adaptiveConfig.processingFps

        return if (now - lastProcessedFrameTime >= minInterval) {
            lastProcessedFrameTime = now
            true
        } else {
            false
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraPipeline()
        } else {
            statusReporter.report(
                SystemStatus.NO_CAMERA_PERMISSION
            )
        }
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
