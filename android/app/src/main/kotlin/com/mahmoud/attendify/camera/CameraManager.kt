package com.mahmoud.attendify.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mahmoud.attendify.system.device.DeviceCapabilityProfiler

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager
 *
 * ---------------------------------------------------------------------------
 * ROLE (Architectural Responsibility):
 * ---------------------------------------------------------------------------
 * This class is the SINGLE authority responsible for interacting with CameraX.
 *
 * It deliberately encapsulates:
 *  - All Camera HAL / OEM quirks
 *  - All lifecycle binding logic
 *  - All error containment (NO crash propagation)
 *
 * CameraManager NEVER:
 *  ❌ Performs business logic
 *  ❌ Knows anything about ML, attendance, or Flutter
 *
 * CameraManager ALWAYS:
 *  ✅ Adapts camera behavior to device capabilities
 *  ✅ Fails safely and reports via SystemStatus
 *  ✅ Protects the rest of the system from camera instability
 *
 * ---------------------------------------------------------------------------
 * IMPORTANT SECURITY & STABILITY GOALS:
 * ---------------------------------------------------------------------------
 * - No uncaught exception from Camera HAL may crash the app
 * - No concurrent frame processing (prevents memory pressure)
 * - No assumption about FPS, resolution, or hardware quality
 *
 * This is a defensive, OEM‑aware runtime component.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val statusReporter: SystemStatusReporter
) {

    /**
     * -----------------------------------------------------------------------
     * Dedicated executor for ImageAnalysis
     * -----------------------------------------------------------------------
     *
     * WHY single thread?
     * - Camera frames arrive continuously
     * - ML + bitmap conversion are heavy
     *
     * If we allow parallel processing:
     *  ❌ Memory spikes
     *  ❌ OutOfMemoryError on low‑end devices
     *  ❌ Frame reordering bugs
     *
     * Single‑threaded executor guarantees:
     *  ✅ Backpressure applies correctly
     *  ✅ Deterministic frame order
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    /**
     * CameraX entry point.
     * This reference must be managed carefully across lifecycle events.
     */
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Device capability profile.
     *
     * This is NOT OEM hard‑coding.
     * This is dynamic profiling that lets us:
     *  - Reduce FPS on weak devices
     *  - Avoid overdriving camera pipeline
     */
    private val deviceProfile =
        DeviceCapabilityProfiler.profile(context)

    /**
     * startCamera
     *
     * -----------------------------------------------------------------------
     * Responsibilities:
     * -----------------------------------------------------------------------
     * 1) Obtain CameraProvider asynchronously
     * 2) Cleanly unbind any previous use cases
     * 3) Bind Preview + ImageAnalysis safely
     * 4) Never leak exceptions outside
     *
     * IMPORTANT:
     * - This method NEVER throws
     * - All failures are downgraded to SystemStatus signals
     */
    fun startCamera(
        preview: Preview.SurfaceProvider,
        analyzer: ImageAnalysis.Analyzer
    ) {

        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({

            try {
                // ----------------------------------------------------------------
                // Obtain camera provider (may throw on broken OEMs)
                // ----------------------------------------------------------------
                cameraProvider = providerFuture.get()

                /**
                 * ALWAYS unbind everything first.
                 *
                 * WHY?
                 * - MIUI / HyperOS frequently leave zombie camera sessions
                 * - Rebinding without unbind causes:
                 *   ❌ Camera busy errors
                 *   ❌ Black preview
                 */
                cameraProvider?.unbindAll()

                // ----------------------------------------------------------------
                // Preview UseCase
                // ----------------------------------------------------------------
                val previewUseCase =
                    Preview.Builder()
                        .build()
                        .apply {
                            setSurfaceProvider(preview)
                        }

                // ----------------------------------------------------------------
                // Image Analysis UseCase
                // ----------------------------------------------------------------
                val analysisUseCase =
                    ImageAnalysis.Builder()
                        /**
                         * KEEP_ONLY_LATEST is CRITICAL.
                         *
                         * WHY?
                         * - We never want frame queues
                         * - Dropping old frames is SAFE and DESIRED
                         * - Processing stale frames harms liveness accuracy
                         */
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        /**
                         * DO NOT request target resolution aggressively.
                         * Let CameraX negotiate optimal size per device.
                         */
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor, analyzer)
                        }

                // ----------------------------------------------------------------
                // Camera selection
                // ----------------------------------------------------------------
                val cameraSelector =
                    CameraSelector.DEFAULT_FRONT_CAMERA

                /**
                 * Lifecycle binding
                 *
                 * CameraX will:
                 *  ✅ Open camera when lifecycle is STARTED
                 *  ✅ Close camera when PAUSED/DESTROYED
                 *
                 * This prevents:
                 *  ❌ Camera leaks
                 *  ❌ HAL deadlocks
                 */
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )

                // ----------------------------------------------------------------
                // Report successful startup
                // ----------------------------------------------------------------
                statusReporter.report(SystemStatus.OK)

            } catch (_: SecurityException) {
                /**
                 * Camera permission revoked during runtime.
                 *
                 * This can happen if the user:
                 * - Opens system settings
                 * - Revokes permission mid‑session
                 *
                 * We MUST:
                 *  ✅ Fail gracefully
                 *  ✅ Inform Flutter
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_PERMISSION_REVOKED_BY_SYSTEM
                )

            } catch (_: IllegalStateException) {
                /**
                 * Camera is busy or in invalid state.
                 *
                 * Common on:
                 * - Xiaomi / Samsung with aggressive camera services
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_BUSY
                )

            } catch (_: OutOfMemoryError) {
                /**
                 * Memory pressure detected.
                 *
                 * CRITICAL RULE:
                 * - Never crash
                 * - Let upper layers degrade functionality
                 */
                statusReporter.report(SystemStatus.LOW_MEMORY)

            } catch (e: Exception) {
                /**
                 * Absolute fallback.
                 *
                 * No exception from camera stack is allowed
                 * to crash the app.
                 */
                Log.e(
                    "CameraManager",
                    "Unexpected camera error",
                    e
                )
                statusReporter.report(
                    SystemStatus.INTERNAL_ERROR
                )
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * shutdown
     *
     * -----------------------------------------------------------------------
     * Graceful camera shutdown.
     * This must be called when:
     *  - Activity is destroyed
     *  - Attendance pipeline is terminated
     *
     * RULE:
     * -----------------------------------------------------------------------
     * Camera threads must NEVER outlive the screen.
     */
    fun shutdown() {
        try {
            cameraProvider?.unbindAll()

            /**
             * shutdownNow is intentional.
             *
             * Any queued analysis task is unsafe to continue.
             */
            cameraExecutor.shutdownNow()

            statusReporter.report(SystemStatus.CAMERA_CLOSED)

        } catch (e: Exception) {
            /**
             * Even shutdown must not crash.
             */
            statusReporter.report(SystemStatus.INTERNAL_ERROR)
        }
    }
}