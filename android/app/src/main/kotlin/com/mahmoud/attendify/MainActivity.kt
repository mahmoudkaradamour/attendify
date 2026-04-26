package com.mahmoud.attendify
//5556
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.camera.FrameAnalyzer
import com.mahmoud.attendify.camera.ImageConverter
import com.mahmoud.attendify.camera.SystemStatusReporter

import com.mahmoud.attendify.attendance.AttendanceSession
import com.mahmoud.attendify.attendance.orchestration.AttendanceRuntimeOrchestrator
import com.mahmoud.attendify.attendance.usecase.AttendanceUseCase

import com.mahmoud.attendify.face.FaceDetector
import com.mahmoud.attendify.face.MobileFaceNet
import com.mahmoud.attendify.matching.FaceMatchingUseCase
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy
import com.mahmoud.attendify.repository.local.LocalEncryptedEmployeeReferenceRepository

import com.mahmoud.attendify.liveness.LivenessOrchestrator
import com.mahmoud.attendify.liveness.engine.FacialMetricsEngine
import com.mahmoud.attendify.liveness.policy.LivenessPolicy

import com.mahmoud.attendify.system.time.*
import com.mahmoud.attendify.system.location.*
import com.mahmoud.attendify.system.time.working.*

import com.mahmoud.attendify.config.AttendancePolicyProvider
import com.mahmoud.attendify.channel.AttendanceMethodChannel

/**
 * MainActivity
 *
 * ✅ Entry point
 * ✅ Composition Root only
 * ❌ No business logic
 * ❌ No policy authoring
 */
class MainActivity : FlutterActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraManager: CameraManager
    private lateinit var orchestrator: AttendanceRuntimeOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        previewView = PreviewView(this)
        setContentView(previewView)

        orchestrator = buildAttendanceRuntime()

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            statusReporter = SystemStatusReporter { }
        )

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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    private fun startCamera() {
        cameraManager.startCamera(
            preview = previewView.surfaceProvider,
            analyzer = FrameAnalyzer { frame ->
                processFrame(frame)
            }
        )
    }

    private fun processFrame(frame: ImageProxy) {
        try {
            val bitmap: Bitmap =
                ImageConverter.imageProxyToBitmap(frame)
            AttendanceSession.updateFace(bitmap)
        } finally {
            frame.close()
        }
    }

    private fun buildAttendanceRuntime(): AttendanceRuntimeOrchestrator {

        val faceDetector = FaceDetector(this)
        val faceNet = MobileFaceNet(this)

        val repository =
            LocalEncryptedEmployeeReferenceRepository(this)

        val matchingUseCase = FaceMatchingUseCase(
            policy = MatchingPolicy(
                defaultThreshold = 1.1,
                referenceValidationPolicy =
                    ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE
            ),
            referenceAccessPolicy = ReferenceAccessPolicy.LOCAL_ONLY,
            repository = repository,
            groupThreshold = null
        )

        val attendanceUseCase = AttendanceUseCase(
            timeIntegrityGuard =
                TimeIntegrityGuard(
                    context = this,
                    policy = AttendancePolicyProvider.timePolicy(),
                    anchorStorage = SecureTimeAnchorStorage(this)
                ),
            timeProofFactory =
                AttendanceTimeProofFactory(
                    anchorStorage = SecureTimeAnchorStorage(this),
                    gpsTimeProvider = GpsTimeProvider(this)
                ),
            timeAnchorStorage =
                SecureTimeAnchorStorage(this),
            locationIntegrityGuard =
                LocationIntegrityGuard(
                    context = this,
                    policy = AttendancePolicyProvider.locationPolicy(),
                    zonesPolicy = null,
                    locationSource = LocationSource(this),
                    anchorStorage = SecureLocationAnchorStorage(this)
                ),
            workingTimeEvaluator =
                WorkingTimeEvaluator(
                    policy = AttendancePolicyProvider.workingTimePolicy()
                )
        )

        return AttendanceRuntimeOrchestrator(
            faceDetector = faceDetector,
            faceNet = faceNet,
            livenessOrchestrator = null,
            facialMetricsEngine = FacialMetricsEngine(),
            faceMatchingUseCase = matchingUseCase,
            attendanceUseCase = attendanceUseCase
        )
    }
}
//22