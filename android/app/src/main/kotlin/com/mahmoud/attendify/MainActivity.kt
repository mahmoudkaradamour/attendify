package com.mahmoud.attendify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/* ================= Camera & Image ================= */
import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.FrameAnalyzer
import com.mahmoud.attendify.camera.ImageConverter
import com.mahmoud.attendify.camera.ImageQualityChecker
import com.mahmoud.attendify.camera.SystemStatus
import com.mahmoud.attendify.camera.SystemStatusReporter

/* ================= Face & ML ================= */
import com.mahmoud.attendify.face.FaceCropper
import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet

/* ================= Static Debug Tests ================= */
import com.mahmoud.attendify.fas.debug.FASStaticImageTester
import com.mahmoud.attendify.face.debug.MobileFaceNetStaticTester

/* ================= Liveness ================= */
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* ================= Attendance ================= */
import com.mahmoud.attendify.attendance.AttendanceUseCase
import com.mahmoud.attendify.attendance.AttendanceSession

/* ================= Matching ================= */
import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy
import com.mahmoud.attendify.repository.local.LocalEncryptedEmployeeReferenceRepository

/* ================= FAS ================= */
import com.mahmoud.attendify.fas.runtime.FASOrchestrator
import com.mahmoud.attendify.fas.models.MiniFASNetV2Model
import com.mahmoud.attendify.fas.models.MiniFASNetV1SEModel
// ❌ FastFASNetV3Model مستبعد مؤقتًا من الاختبار

/**
 * MainActivity
 *
 * Arabic:
 * ✅ نسخة Debug منظمة للاختبار المرحلي
 * ✅ لا تغيّر أي منطق إنتاجي
 * ✅ كل الاختبارات الثقيلة خارج Main Thread
 *
 * English:
 * ✅ Safe debug-oriented activity for incremental testing
 * ✅ No production logic changes
 * ✅ All heavy inference runs off the UI thread
 */
class MainActivity : FlutterActivity() {

    private lateinit var attendanceUseCase: AttendanceUseCase
    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceNet: MobileFaceNet
    private lateinit var statusReporter: SystemStatusReporter

    /**
     * ⚠️ LivenessOrchestrator is optional in debug
     *
     * Arabic:
     * نجعلها nullable لتجنب أي Crash
     * إلى أن يتم ربط سياسة Liveness النهائية.
     *
     * English:
     * Nullable for fail-safe debug operation
     * until final policy wiring is complete.
     */
    private var livenessOrchestrator: LivenessOrchestrator? = null
    private val facialMetricsEngine = FacialMetricsEngine()

    /**
     * ✅ Executor موحد لكل اختبارات ML
     *
     * Arabic:
     * يمنع الضغط على UI ويضمن تسلسل التنفيذ.
     *
     * English:
     * Single-thread executor for deterministic ML execution.
     */
    private val inferenceExecutor = Executors.newSingleThreadExecutor()

    // PreviewView Overlay فوق Flutter لضمان Surface حقيقي فورًا
    private var previewView: PreviewView? = null

    // Flags لمنع تشغيل الكاميرا أكثر من مرة
    private var hasWindowFocusNow = false
    private var cameraStarted = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val ATTENDANCE_CHANNEL = "attendance_channel"
        private const val TAG = "MainActivity"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        statusReporter = SystemStatusReporter { status ->
            Log.d("SystemStatus", status.name)
        }

        attendanceUseCase = provideAttendanceUseCase()

        // ✅ MethodChannel موجود فقط كجسر (غير مستخدم الآن)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            ATTENDANCE_CHANNEL
        )
    }

    /* =========================================================
     * Dependency Providers
     * ========================================================= */

    private fun provideFaceMatchingUseCase(): FaceMatchingUseCase {
        val repository = LocalEncryptedEmployeeReferenceRepository(this)

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

    private fun provideFASOrchestrator(): FASOrchestrator {
        return FASOrchestrator(
            mapOf(
                "minifasnet_v2_80x80_default" to MiniFASNetV2Model(this),
                "minifasnet_v1se_80x80_light" to MiniFASNetV1SEModel(this)
            )
        )
    }

    private fun provideAttendanceUseCase(): AttendanceUseCase {
        return AttendanceUseCase(
            faceMatchingUseCase = provideFaceMatchingUseCase(),
            fasOrchestrator = provideFASOrchestrator()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* =====================================================
         * ✅ Gate الكاميرا (لا تشغيل مبكر)
         * ===================================================== */
        tryStartCameraWhenReady()

        /* =====================================================
         * ✅ Static FAS test (مرحلة 1)
         * ===================================================== */
        inferenceExecutor.execute {
            try {
                FASStaticImageTester.runTest(
                    context = this,
                    model = MiniFASNetV2Model(this),
                    assetImagePath = "test_faces/real_face.jpg",
                    useGpu = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Static FAS test failed", e)
            }
        }

        /* =====================================================
         * ✅ Static MobileFaceNet test (مرحلة 2)
         *
         * Arabic:
         * نفس منهج FAS:
         * صور ثابتة → كشف وجه → Embedding → قياس المسافات
         *
         * English:
         * Static embedding contract test using 3 known images.
         * ===================================================== */
        inferenceExecutor.execute {
            try {
                MobileFaceNetStaticTester.run3ImageSuite(
                    context = this,
                    imageReal = "test_faces/real_face.jpg",
                    imagePrinted = "test_faces/printed_photo.jpg",
                    imageScreen = "test_faces/screen_attack.jpg"
                )
            } catch (e: Exception) {
                Log.e(TAG, "MobileFaceNet static suite failed", e)
            }
        }
    }

    /* =========================================================
     * Camera lifecycle control
     * ========================================================= */

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocusNow = hasFocus
        tryStartCameraWhenReady()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            val granted =
                grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Camera permission result: granted=$granted")
            tryStartCameraWhenReady()
        }
    }

    /**
     * Arabic:
     * Gate موحد يمنع تشغيل الكاميرا قبل:
     * - جاهزية Window
     * - وجود Permission
     *
     * English:
     * Unified gate to safely start CameraX.
     */
    private fun tryStartCameraWhenReady() {
        if (cameraStarted) return
        if (!hasWindowFocusNow) return

        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }

        startCameraPipeline()
    }

    private fun startCameraPipeline() {
        if (cameraStarted) return
        cameraStarted = true

        val pv = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        previewView = pv
        addContentView(pv, pv.layoutParams)

        faceDetector = FaceDetector(this)
        faceNet = MobileFaceNet(this)

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = statusReporter
        )

        val analyzer = FrameAnalyzer { imageProxy ->
            try {
                val bitmap = ImageConverter.imageProxyToBitmap(imageProxy)

                inferenceExecutor.execute {
                    try {
                        if (ImageQualityChecker.checkFrame(bitmap) != SystemStatus.OK)
                            return@execute

                        val detection =
                            faceDetector.detectBestFace(bitmap) ?: return@execute

                        val faceBitmap =
                            FaceCropper.cropFace(bitmap, detection.box)
                                ?: return@execute

                        AttendanceSession.updateFace(faceBitmap)

                        val metrics =
                            facialMetricsEngine.computeFrameFromBitmap(faceBitmap)

                        val live = livenessOrchestrator
                        if (live != null) {
                            live.onFrame(metrics)
                            if (live.evaluate() == LivenessResult.SpoofDetected) {
                                statusReporter.report(SystemStatus.SPOOF_DETECTED)
                                return@execute
                            }
                        }

                        val embedding = faceNet.getEmbedding(faceBitmap)

                        val decision =
                            attendanceUseCase.attemptAttendance(
                                faceBitmap = faceBitmap,
                                liveEmbedding = embedding,
                                employeeId = "DEBUG_EMPLOYEE",
                                employeePolicy = null,
                                groupPolicy = null,
                                orgPolicy = null
                            )

                        Log.d("DEBUG_ATTENDANCE", "Decision=$decision")

                    } catch (_: Exception) {
                        statusReporter.report(SystemStatus.INTERNAL_ERROR)
                    }
                }

            } catch (_: Exception) {
                statusReporter.report(SystemStatus.INTERNAL_ERROR)
            } finally {
                // ✅ ImageProxy يُغلق هنا فقط (منع double-close)
                imageProxy.close()
            }
        }

        pv.post {
            try {
                cameraManager.startCamera(
                    preview = pv.surfaceProvider,
                    analyzer = analyzer
                )
                Log.d(TAG, "Camera started successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera.", e)
                statusReporter.report(SystemStatus.INTERNAL_ERROR)
            }
        }
    }

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

    override fun onDestroy() {
        super.onDestroy()
        inferenceExecutor.shutdown()

        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }

        previewView = null
    }
}