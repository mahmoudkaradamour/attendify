package com.mahmoud.attendify.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mahmoud.attendify.system.device.DeviceCapabilityProfiler
import com.mahmoud.attendify.util.BitmapSafeUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * =============================================================================
 * 🎥 CameraManager — Deterministic Single-Frame Acquisition System
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 SYSTEM MODEL
 * -----------------------------------------------------------------------------
 *
 * This component implements:
 *
 *   Event-Driven, Single-Consumption Frame Acquisition
 *
 * Formal model:
 *
 *   Request(t) → Capture → Deliver exactly one frame → Release
 *
 * -----------------------------------------------------------------------------
 * 📊 EXECUTION FLOW (FORMALIZED)
 * -----------------------------------------------------------------------------
 *
 *                Coroutine (Caller)
 *                      │
 *                      ▼
 *        captureSingleFrameSuspend()
 *                      │
 *                      ▼
 *        frameRequested = true
 *                      │
 *                      ▼
 *            CameraX Analyzer
 *                      │
 *                      ▼
 *      Check request flag → TRUE
 *                      │
 *                      ▼
 *          Convert to Bitmap
 *                      │
 *                      ▼
 *        Deliver + Close Session
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * ✅ No frame reuse
 * ✅ No buffering
 * ✅ No temporal leakage
 * ✅ One request → one frame
 *
 * -----------------------------------------------------------------------------
 * ⚙️ STABILITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ No main-thread blocking
 * ✅ Backpressure handled (CameraX latest-frame strategy)
 * ✅ Coroutine cancellation-safe
 * ✅ Memory safe (Bitmap lifecycle managed)
 * ✅ No race conditions (single-thread executor + atomic flags)
 *
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val statusReporter: SystemStatusReporter
) {

    /* =========================================================================
     * 🧵 EXECUTION CONTROL
     * ========================================================================= */

    /**
     * Single-thread executor ensures:
     *
     * ✅ sequential frame processing
     * ✅ prevents concurrent analyzer execution
     * ✅ avoids race conditions
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    @Suppress("unused")
    private val deviceProfile =
        DeviceCapabilityProfiler.profile(context)

    /* =========================================================================
     * 📸 CAPTURE STATE MACHINE
     * ========================================================================= */

    /**
     * Indicates whether a frame is currently requested.
     */
    private val frameRequested = AtomicBoolean(false)

    /**
     * ✅ prevents double capture (extra safety layer)
     */
    @Volatile
    private var isCapturing = false

    /**
     * Deferred callback used to deliver result to coroutine.
     */
    @Volatile
    private var pendingContinuation: ((Bitmap?) -> Unit)? = null

    /* =========================================================================
     * 🚀 CAMERA INITIALIZATION
     * ========================================================================= */

    fun startCamera(preview: Preview.SurfaceProvider) {

        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({

            try {

                cameraProvider = providerFuture.get()
                cameraProvider?.unbindAll()

                val previewUseCase =
                    Preview.Builder()
                        .build()
                        .apply { setSurfaceProvider(preview) }

                val analysisUseCase =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor, internalAnalyzer)
                        }

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    previewUseCase,
                    analysisUseCase
                )

                statusReporter.report(SystemStatus.OK)

            } catch (_: SecurityException) {
                statusReporter.report(SystemStatus.CAMERA_PERMISSION_REVOKED_BY_SYSTEM)
            } catch (_: IllegalStateException) {
                statusReporter.report(SystemStatus.CAMERA_BUSY)
            } catch (_: OutOfMemoryError) {
                statusReporter.report(SystemStatus.LOW_MEMORY)
            } catch (e: Exception) {
                Log.e("CameraManager", "Unexpected error", e)
                statusReporter.report(SystemStatus.INTERNAL_ERROR)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /* =========================================================================
     * 🔥 ANALYZER — CORE PIPELINE NODE
     * ========================================================================= */

    private val internalAnalyzer =
        ImageAnalysis.Analyzer { imageProxy ->

            var producedBitmap: Bitmap? = null

            try {

                /* ---------------------------------------------------------
                 * ✅ Fast exit if no request (critical for performance)
                 * --------------------------------------------------------- */
                if (!frameRequested.get()) {
                    return@Analyzer
                }

                /* ---------------------------------------------------------
                 * ✅ Prevent overlapping capture (race guard)
                 * --------------------------------------------------------- */
                if (isCapturing) {
                    return@Analyzer
                }

                isCapturing = true

                producedBitmap =
                    ImageConverter.imageProxyToBitmap(imageProxy)

                val continuation = pendingContinuation

                if (continuation != null) {

                    /* -----------------------------------------------------
                     * ✅ Close session deterministically
                     * ----------------------------------------------------- */
                    pendingContinuation = null
                    frameRequested.set(false)

                    continuation(producedBitmap)
                    producedBitmap = null // ownership transferred ✅
                    return@Analyzer
                }

            } catch (_: Exception) {

                BitmapSafeUtils.safeRecycle(producedBitmap)

            } finally {

                isCapturing = false
                imageProxy.close()
            }
        }

    /* =========================================================================
     * ✅ PUBLIC API — SUSPEND CAPTURE
     * ========================================================================= */

    suspend fun captureSingleFrameSuspend(
        timeoutMs: Long
    ): Bitmap? = withTimeoutOrNull(timeoutMs) {

        suspendCancellableCoroutine { cont ->

            /* ------------------------------------------------------------
             * ✅ SINGLE-FLIGHT GUARANTEE
             * ------------------------------------------------------------ */
            if (frameRequested.getAndSet(true)) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            /* ------------------------------------------------------------
             * ✅ Register continuation
             * ------------------------------------------------------------ */
            pendingContinuation = { bitmap ->

                if (cont.isActive) {
                    cont.resume(bitmap)
                } else {
                    BitmapSafeUtils.safeRecycle(bitmap)
                }
            }

            /* ------------------------------------------------------------
             * ✅ Cancellation-safe cleanup
             * ------------------------------------------------------------ */
            cont.invokeOnCancellation {
                frameRequested.set(false)
                pendingContinuation = null
            }
        }
    }

    /* =========================================================================
     * 🛑 CLEAN SHUTDOWN
     * ========================================================================= */

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