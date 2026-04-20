package com.mahmoud.attendify
//d
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
import com.mahmoud.attendify.fas.models.FastFASNetV3Model

/**
 * MainActivity
 *
 * ✅ نسخة Debug للاختبار والقياس فقط
 * ✅ Flutter UI قد يكون أسود (طبيعي)
 */
class MainActivity : FlutterActivity() {

    private lateinit var attendanceUseCase: AttendanceUseCase
    private lateinit var cameraManager: CameraManager
    private lateinit var faceDetector: FaceDetector
    private lateinit var faceNet: MobileFaceNet
    private lateinit var statusReporter: SystemStatusReporter

    private lateinit var livenessOrchestrator: LivenessOrchestrator
    private val facialMetricsEngine = FacialMetricsEngine()

    private val inferenceExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 101
        private const val ATTENDANCE_CHANNEL = "attendance_channel"
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

    private fun provideFASOrchestrator(): FASOrchestrator {
        return FASOrchestrator(
            mapOf(
                "minifasnet_v2_80x80_default" to MiniFASNetV2Model(this),
                "minifasnet_v1se_80x80_light" to MiniFASNetV1SEModel(this),
                "fastfasnet_v3_128x128_highsec" to FastFASNetV3Model(this)
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

        if (hasCameraPermission()) {
            startCameraPipeline()
        } else {
            requestCameraPermission()
        }


// ✅ DEBUG ONLY: Static FAS benchmarks
        FASStaticImageTester.runTest(
            context = this,
            model = MiniFASNetV2Model(this),
            assetImagePath = "test_faces/real_face.jpg",
            useGpu = false
        )

        FASStaticImageTester.runTest(
            context = this,
            model = FastFASNetV3Model(this),
            assetImagePath = "test_faces/real_face.jpg",
            useGpu = false
        )

        FASStaticImageTester.runTest(
            context = this,
            model = FastFASNetV3Model(this),
            assetImagePath = "test_faces/real_face.jpg",
            useGpu = true
        )

    }

    private fun startCameraPipeline() {

        val previewView = PreviewView(this) // ✅ مهم لحل SurfaceProvider

        faceDetector = FaceDetector(this)
        faceNet = MobileFaceNet(this)

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = statusReporter
        )

        val analyzer = FrameAnalyzer { imageProxy ->
            val bitmap = ImageConverter.imageProxyToBitmap(imageProxy)

            inferenceExecutor.execute {
                try {
                    if (ImageQualityChecker.checkFrame(bitmap) != SystemStatus.OK) return@execute

                    val detection = faceDetector.detectBestFace(bitmap) ?: return@execute
                    val faceBitmap = FaceCropper.cropFace(bitmap, detection.box) ?: return@execute

                    AttendanceSession.updateFace(faceBitmap)

                    val metrics =
                        facialMetricsEngine.computeFrameFromBitmap(faceBitmap)

                    livenessOrchestrator.onFrame(metrics)

                    if (livenessOrchestrator.evaluate() == LivenessResult.SpoofDetected) {
                        statusReporter.report(SystemStatus.SPOOF_DETECTED)
                        return@execute
                    }

                    val embedding = faceNet.getEmbedding(faceBitmap)

                    // ✅ DEBUG ONLY – Trigger Attendance
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

            imageProxy.close()
        }

        cameraManager.startCamera(
            preview = previewView.surfaceProvider,
            analyzer = analyzer
        )
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
    }
}