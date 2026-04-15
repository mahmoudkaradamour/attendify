package com.mahmoud.attendify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.mahmoud.attendify.liveness.*
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.policy.LivenessPolicy
import com.mahmoud.attendify.liveness.result.LivenessResult
import java.util.concurrent.Executors

/**
 * MainActivity
 *
 * هذه النسخة:
 * ✅ تعمل Android فقط
 * ✅ جاهزة للربط مع Flutter بدون تغيير
 * ✅ لا تحتوي منطق UI
 * ✅ تدير تدفق:
 * Camera → Metrics → Liveness → (Match لاحقًا)
 */
class MainActivity : FlutterActivity() {

    /* =========================================================
     * Flutter Channel (جاهز)
     * ========================================================= */

    private lateinit var channel: MethodChannel
    private val CHANNEL_NAME = "attendify/liveness"

    /* =========================================================
     * Camera
     * ========================================================= */

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    /* =========================================================
     * Liveness
     * ========================================================= */

    private lateinit var livenessOrchestrator: LivenessOrchestrator
    private lateinit var metricsEngine: FacialMetricsEngine

    private var livenessStartTimeMs = 0L
    private var livenessWindowMs = 3_000L // افتراضي (يُغيّر من Flutter لاحقًا)

    /* =========================================================
     * Lifecycle
     * ========================================================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        metricsEngine = FacialMetricsEngine()

        initDefaultLiveness() // Android default

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                100
            )
        }
    }

    /* =========================================================
     * Flutter Integration (READY)
     * ========================================================= */

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_NAME
        )

        /**
         * لاحقًا Flutter سيفعل:
         * - إرسال LivenessPolicy
         * - التحكم بالقيم
         * - التحكم بالنافذة الزمنية
         */
        channel.setMethodCallHandler { call, result ->
            when (call.method) {

                "updateLivenessPolicy" -> {
                    val policyMap = call.arguments as Map<String, Any>
                    updatePolicyFromFlutter(policyMap)
                    result.success(null)
                }

                "resetLiveness" -> {
                    resetLivenessSession()
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }

    /* =========================================================
     * Liveness Setup
     * ========================================================= */

    /**
     * سياسة افتراضية للعمل Android‑only
     * (سيتم استبدالها من Flutter لاحقًا)
     */
    private fun initDefaultLiveness() {

        val policy = LivenessPolicy(
            requireBlink = true,
            minBlinkCount = 1,
            allowSimultaneousBlink = false,
            eyeClosedThreshold = 0.21,

            requireSmile = true,
            minSmileScore = 0.45,
            minSmileDurationMs = 300,

            requireMouthOpen = false,
            minMouthOpenFrames = 0,

            requireYaw = true,
            minYawDegrees = 18.0,
            requirePitch = false,
            minPitchDegrees = 0.0,

            requirePhotometricResponse = true,
            luminanceVarianceThreshold = 15.0
        )

        livenessOrchestrator = LivenessOrchestrator(policy)
        livenessStartTimeMs = System.currentTimeMillis()
    }

    /**
     * تحديث السياسة من Flutter
     */
    private fun updatePolicyFromFlutter(data: Map<String, Any>) {

        // هنا يمكنك parse القيم القادمة من Flutter
        // وتحويلها إلى LivenessPolicy

        // مثال فقط:
        livenessWindowMs = (data["windowMs"] as? Int)?.toLong() ?: 3000L

        livenessOrchestrator.reset()
        livenessStartTimeMs = System.currentTimeMillis()
    }

    private fun resetLivenessSession() {
        livenessOrchestrator.reset()
        livenessStartTimeMs = System.currentTimeMillis()
    }

    /* =========================================================
     * CameraX
     * ========================================================= */

    private fun startCamera() {

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({

            val cameraProvider = providerFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->

                try {
                    val now = System.currentTimeMillis()

                    // ===========================
                    // 1) Extract Face Landmarks
                    // ===========================
                    val landmarks = extractFaceLandmarks(imageProxy)
                        ?: run {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                    // ===========================
                    // 2) Build Metrics Frame
                    // ===========================
                    val frame = metricsEngine.buildFrame(
                        timestampMs = now,

                        rightEyeEAR = landmarks.rightEyeEAR,
                        leftEyeEAR = landmarks.leftEyeEAR,
                        rightEyeClosed =
                            landmarks.rightEyeEAR < 0.21,
                        leftEyeClosed =
                            landmarks.leftEyeEAR < 0.21,

                        mouthAspectRatio = landmarks.mouthAR,
                        smileScore = landmarks.smileScore,
                        mouthOpen = landmarks.mouthAR > 0.6,

                        yaw = landmarks.yaw,
                        pitch = landmarks.pitch,
                        roll = landmarks.roll,

                        meanLuminance = landmarks.meanLuminance,
                        luminanceVariance = landmarks.luminanceVariance
                    )

                    // ===========================
                    // 3) Liveness ingestion
                    // ===========================
                    livenessOrchestrator.onFrame(frame)

                    // ===========================
                    // 4) Evaluate after window
                    // ===========================
                    if (now - livenessStartTimeMs >= livenessWindowMs) {

                        val result =
                            livenessOrchestrator.evaluate()

                        when (result) {

                            LivenessResult.Alive -> {
                                notifyFlutter("LIVENESS_OK")
                                // هنا ننتقل لاحقًا
                                // إلى Face Matching
                            }

                            LivenessResult.SpoofDetected -> {
                                notifyFlutter("LIVENESS_FAILED")
                                resetLivenessSession()
                            }
                        }
                    }

                } catch (ex: Exception) {
                    Log.e("MainActivity", ex.message ?: "")
                } finally {
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    /* =========================================================
     * Flutter notifications
     * ========================================================= */

    private fun notifyFlutter(event: String) {
        if (::channel.isInitialized) {
            runOnUiThread {
                channel.invokeMethod("onLivenessEvent", event)
            }
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

    /* =========================================================
     * Landmarks (Placeholder)
     * ========================================================= */

    private fun extractFaceLandmarks(
        imageProxy: ImageProxy
    ): FaceLandmarksData? {
        // TODO: ML Kit / MediaPipe integration
        return null
    }
}

/**
 * تمثيل مؤقت للمعالم – استبدله بالمصدر الحقيقي
 */
data class FaceLandmarksData(
    val rightEyeEAR: Double,
    val leftEyeEAR: Double,
    val mouthAR: Double,
    val smileScore: Double,
    val yaw: Double,
    val pitch: Double,
    val roll: Double,
    val meanLuminance: Double,
    val luminanceVariance: Double
)