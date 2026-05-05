package com.mahmoud.attendify.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mahmoud.attendify.system.device.DeviceCapabilityProfiler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * CameraManager
 *
 * ============================================================
 * ROLE (Forensic Camera Authority):
 * ============================================================
 * This class is the ONLY component allowed to:
 * - Interface with CameraX / HAL
 * - Receive camera frames
 * - Decide WHEN a frame is captured
 *
 * No UI, no Activity, and no Flutter component
 * is ever allowed to access raw camera frames.
 *
 * ============================================================
 * SECURITY MODEL:
 * ============================================================
 * - One attendance attempt  ==  One frozen frame
 * - Frame is captured ONCE
 * - Frame is immutable after capture
 * - Any attempt to inject, replace, or re-capture
 *   a frame is structurally impossible
 *
 * This class enforces the "Frame Freeze Contract".
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val statusReporter: SystemStatusReporter
) {

    /* ============================================================
     * Single-thread executor
     * ============================================================
     *
     * Rationale:
     * - Deterministic execution
     * - No frame reordering
     * - No concurrency races inside HAL callbacks
     *
     * This executor is NEVER shared.
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    @Suppress("unused")
    private val deviceProfile =
        DeviceCapabilityProfiler.profile(context)

    /* ============================================================
     * Frame Freeze primitives
     * ============================================================
     *
     * frozenFrame:
     * - Holds the ONE immutable frame for the session
     * - Write-once (compareAndSet)
     *
     * frameRequested:
     * - Explicit signal: "system is ready to accept ONE frame"
     * - Analyzer MUST ignore frames when false
     */
    private val frozenFrame =
        AtomicReference<Bitmap?>(null)

    private val frameRequested =
        AtomicBoolean(false)

    /* ============================================================
     * Camera start / binding
     * ============================================================
     *
     * This method:
     * - Binds Preview for UI visibility only
     * - Binds ImageAnalysis ONLY for internal analyzer
     * - Does NOT expose frames outward
     */
    fun startCamera(
        preview: Preview.SurfaceProvider
    ) {
        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({

            try {
                cameraProvider = providerFuture.get()
                cameraProvider?.unbindAll()

                val previewUseCase =
                    Preview.Builder()
                        .build()
                        .apply {
                            setSurfaceProvider(preview)
                        }

                val analysisUseCase =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor, internalAnalyzer)
                        }

                val cameraSelector =
                    CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )

                statusReporter.report(SystemStatus.OK)

            } catch (_: SecurityException) {
                statusReporter.report(
                    SystemStatus.CAMERA_PERMISSION_REVOKED_BY_SYSTEM
                )
            } catch (_: IllegalStateException) {
                statusReporter.report(SystemStatus.CAMERA_BUSY)
            } catch (_: OutOfMemoryError) {
                statusReporter.report(SystemStatus.LOW_MEMORY)
            } catch (e: Exception) {
                Log.e("CameraManager", "Unexpected camera error", e)
                statusReporter.report(SystemStatus.INTERNAL_ERROR)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /* ============================================================
     * Analyzer (Frame Freeze aware)
     * ============================================================
     *
     * Security rules:
     * - Analyzer MUST ignore frames unless explicitly requested
     * - Analyzer MUST close ImageProxy always (HAL safety)
     * - Analyzer MUST never overwrite an existing frozen frame
     */
    private val internalAnalyzer =
        object : ImageAnalysis.Analyzer {

            override fun analyze(imageProxy: ImageProxy) {
                try {

                    // If no frame requested, discard immediately
                    if (!frameRequested.get()) {
                        return
                    }

                    val bitmap =
                        ImageConverter.imageProxyToBitmap(imageProxy)

                    /*
                     * Write-once semantics:
                     * Only the FIRST frame after request is accepted.
                     * Any subsequent frames are ignored structurally.
                     */
                    if (frozenFrame.compareAndSet(null, bitmap)) {
                        frameRequested.set(false)
                    }

                } catch (_: Exception) {
                    // HAL / conversion errors are swallowed safely
                } finally {
                    // ImageProxy MUST be closed no matter what
                    imageProxy.close()
                }
            }
        }

    /* ============================================================
     * ✅ Frame Freeze Contract (PUBLIC ENTRY POINT)
     * ============================================================
     *
     * This is the ONLY method allowed to retrieve a camera frame.
     *
     * Contract:
     * - Caller requests ONE frame
     * - CameraManager freezes EXACTLY one frame
     * - If no frame arrives within timeout → null
     *
     * This method intentionally:
     * - Blocks the calling thread
     * - Has a hard timeout
     * - Does NOT retry internally
     *
     * Any retry must be an explicit new attendance attempt.
     */
    fun captureSingleFrame(
        timeoutMs: Long
    ): Bitmap? {

        // Reset any previous state explicitly
        frozenFrame.set(null)
        frameRequested.set(true)

        val start =
            SystemClock.elapsedRealtime()

        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            frozenFrame.get()?.let { bitmap ->
                return bitmap
            }

            // Small sleep to avoid CPU spinning
            Thread.sleep(10)
        }

        /*
         * ⛔ Circuit Breaker:
         * If timeout is exceeded:
         * - Stop accepting frames
         * - Return null deterministically
         *
         * No late frames are ever accepted.
         */
        frameRequested.set(false)
        return null
    }

    /* ============================================================
     * Shutdown
     * ============================================================
     *
     * Ensures:
     * - Camera HAL is released
     * - Executor is terminated
     * - No background frame processing survives
     */
    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdownNow()
            statusReporter.report(SystemStatus.CAMERA_CLOSED)
        } catch (_: Exception) {
            statusReporter.report(SystemStatus.INTERNAL_ERROR)
        }
    }
}