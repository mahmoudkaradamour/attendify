package com.mahmoud.attendify
//55
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

/* ================= Liveness ================= */
import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.result.LivenessResult

/* ================= Attendance ================= */
import com.mahmoud.attendify.attendance.AttendanceUseCase
import com.mahmoud.attendify.attendance.AttendanceSession
import com.mahmoud.attendify.fas.debug.FASStaticImageTester

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
 * ✅ نسخة Debug للاختبار والقياس فقط
 * ✅ حل Timeout: Cannot complete surfaceList within 5000
 * ✅ منع ضغط الـ Main Thread (تشغيل FAS static test خارج الـ UI thread)
 * ✅ بدء الكاميرا فقط عند جاهزية الـ Window + وجود الصلاحية
 */
class MainActivity : FlutterActivity() {

    private lateinit var attendanceUseCase: AttendanceUseCase
    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceNet: MobileFaceNet
    private lateinit var statusReporter: SystemStatusReporter

    /**
     * ⚠️ ملاحظة مهمة:
     * في كودك السابق كان livenessOrchestrator lateinit ولم يتم تهيئته داخل الملف الذي أرسلته،
     * وهذا قد يسبب Crash لاحقًا عند بدء الـ analyzer.
     *
     * لذلك جعلناه nullable + تشغيله اختياري (Fail-safe) لحين تهيئته بالطريقة الصحيحة من مشروعك.
     * عندما تكون جاهزًا، ضع تهيئته في provideLivenessOrchestrator() أو initLivenessOrchestrator().
     */
    private var livenessOrchestrator: LivenessOrchestrator? = null
    private val facialMetricsEngine = FacialMetricsEngine()

    private val inferenceExecutor = Executors.newSingleThreadExecutor()

    // PreviewView Overlay فوق Flutter حتى لا نعتمد على FlutterSurfaceView أثناء الإقلاع (حيث يكون 0x0)
    private var previewView: PreviewView? = null

    // Flags لضمان عدم تشغيل الكاميرا أكثر من مرة
    private var hasWindowFocusNow: Boolean = false
    private var cameraStarted: Boolean = false

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

        // MethodChannel موجود ولكن غير مستخدم الآن (Debug)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            ATTENDANCE_CHANNEL
        )
    }

    private fun provideFaceMatchingUseCase(): FaceMatchingUseCase {
        val repository = LocalEncryptedEmployeeReferenceRepository(this)

        return FaceMatchingUseCase(
            policy = MatchingPolicy(
                defaultThreshold = 1.2,
                referenceValidationPolicy = ReferenceValidationPolicy.VALIDATE_ONCE_AT_ENROLLMENT
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
                // ❌ FastFASNet مستبعد مؤقتًا
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

        // ✅ لا نبدأ الكاميرا هنا مباشرة لتجنب surfaceList timeout أثناء startup
        // سنبدأها فقط عندما:
        // 1) Window أخذ focus
        // 2) Permission موجودة
        tryStartCameraWhenReady()

        /* =====================================================
         * ✅ DEBUG ONLY: Static FAS benchmark (SAFE)
         * تشغيله على inferenceExecutor لتخفيف الضغط عن Main Thread
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

        // ✅ يمكنك لاحقًا اختبار صور spoof هنا (أيضًا على executor)
        /*
        inferenceExecutor.execute {
            FASStaticImageTester.runTest(
                context = this,
                model = MiniFASNetV2Model(this),
                assetImagePath = "test_faces/printed_photo.jpg",
                useGpu = false
            )
            FASStaticImageTester.runTest(
                context = this,
                model = MiniFASNetV2Model(this),
                assetImagePath = "test_faces/screen_attack.jpg",
                useGpu = false
            )
        }
        */
    }

    /**
     * ✅ أفضل مكان لالتقاط أن الواجهة أصبحت جاهزة
     * لأن FlutterSurfaceView يكون أحيانًا 0x0 في البداية.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocusNow = hasFocus
        tryStartCameraWhenReady()
    }

    /**
     * ✅ عند منح صلاحية الكاميرا نبدأ مباشرة بدون إعادة تشغيل التطبيق
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Camera permission result: granted=$granted")
            tryStartCameraWhenReady()
        }
    }

    /**
     * ✅ Gate موحد: لا يبدأ الكاميرا إلا إذا:
     * - Window جاهز (hasWindowFocusNow)
     * - Permission موجودة
     * - ولم نبدأ الكاميرا سابقًا
     */
    private fun tryStartCameraWhenReady() {
        if (cameraStarted) return

        if (!hasWindowFocusNow) {
            Log.d(TAG, "Camera not started: window has no focus yet.")
            return
        }

        if (!hasCameraPermission()) {
            Log.d(TAG, "Camera not started: permission missing → requesting.")
            requestCameraPermission()
            return
        }

        // الآن الشروط مكتملة
        startCameraPipeline()
    }

    private fun startCameraPipeline() {
        if (cameraStarted) return
        cameraStarted = true

        // ✅ PreviewView Overlay فوق Flutter لضمان وجود Surface حقيقي بسرعة
        val pv = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // يمكنك لاحقًا ضبط ScaleType أو ImplementationMode لو احتجت
            // scaleType = PreviewView.ScaleType.FILL_CENTER
            // implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        previewView = pv
        addContentView(pv, pv.layoutParams)

        faceDetector = FaceDetector(this)
        faceNet = MobileFaceNet(this)

        // ✅ تهيئة الكاميرا
        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = statusReporter
        )

        // ✅ (اختياري Fail-safe) تهيئة livenessOrchestrator إن كانت لديك طريقة معروفة
        // حالياً نتركها null لتجنب Crash إذا كانت تتطلب constructor خاص.
        // إذا كان لديك constructor افتراضي يمكنك تفعيله:
        // livenessOrchestrator = LivenessOrchestrator()

        val analyzer = FrameAnalyzer { imageProxy ->
            try {
                val bitmap = ImageConverter.imageProxyToBitmap(imageProxy)

                inferenceExecutor.execute {
                    try {
                        if (ImageQualityChecker.checkFrame(bitmap) != SystemStatus.OK) {
                            return@execute
                        }

                        val detection = faceDetector.detectBestFace(bitmap) ?: return@execute

                        val faceBitmap = FaceCropper.cropFace(bitmap, detection.box) ?: return@execute

                        AttendanceSession.updateFace(faceBitmap)

                        // ✅ Liveness (Fail-safe)
                        val metrics = facialMetricsEngine.computeFrameFromBitmap(faceBitmap)
                        val live = livenessOrchestrator
                        if (live != null) {
                            live.onFrame(metrics)

                            if (live.evaluate() == LivenessResult.SpoofDetected) {
                                statusReporter.report(SystemStatus.SPOOF_DETECTED)
                                return@execute
                            }
                        } else {
                            // إذا لم يتم تهيئة liveness بعد، لا نكسر السير
                            // ويمكنك لاحقًا إرسال Status خاص أو Log فقط
                            // statusReporter.report(SystemStatus.OK)
                        }

                        val embedding = faceNet.getEmbedding(faceBitmap)

                        val decision = attendanceUseCase.attemptAttendance(
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
                // ✅ مهم جدًا: إغلاق الـ ImageProxy دائمًا لتجنب تسرب الموارد
                imageProxy.close()
            }
        }

        // ✅ نربط الكاميرا بعد أن يصبح الـ PreviewView جاهزًا فعليًا (layout + surface)
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