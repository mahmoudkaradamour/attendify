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
 * Hardened version with:
 * - Frame Freeze
 * - Camera HAL Circuit Breaker
 * - Native memory safety
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val statusReporter: SystemStatusReporter
) {

    /* ============================================================
     * Single-thread executor (deterministic & safe)
     * ============================================================ */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    @Suppress("unused")
    private val deviceProfile =
        DeviceCapabilityProfiler.profile(context)

    /* ============================================================
     * Frame freeze primitives
     * ============================================================ */

    private val frozenFrame =
        AtomicReference<Bitmap?>(null)

    private val frameRequested =
        AtomicBoolean(false)

    /* ============================================================
     * Camera start / binding
     * ============================================================ */

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
     * Analyzer (frame-freeze aware)
     * ============================================================ */

    private val internalAnalyzer =
        object : ImageAnalysis.Analyzer {

            override fun analyze(imageProxy: ImageProxy) {
                try {

                    if (!frameRequested.get()) {
                        imageProxy.close()
                        return
                    }


                    val bitmap =
                        ImageConverter.imageProxyToBitmap(imageProxy)


                    // write-once semantics
                    if (frozenFrame.compareAndSet(null, bitmap)) {
                        frameRequested.set(false)
                    }

                } catch (_: Exception) {
                    // swallow HAL errors safely
                } finally {
                    imageProxy.close()
                }
            }
        }

    /* ============================================================
     * ✅ Frame Freeze + Circuit Breaker
     * ============================================================ */

    fun captureSingleFrame(
        timeoutMs: Long
    ): Bitmap? {

        frozenFrame.set(null)
        frameRequested.set(true)

        val start =
            SystemClock.elapsedRealtime()

        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            frozenFrame.get()?.let { bitmap ->
                return bitmap
            }
            Thread.sleep(10)
        }

        // ⛔ Circuit breaker
        frameRequested.set(false)
        return null
    }

    /* ============================================================
     * Shutdown
     * ============================================================ */

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
