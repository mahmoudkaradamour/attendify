package com.mahmoud.attendify.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager
 *
 * Arabic:
 * كلاس مركزي مسؤول عن:
 * - تشغيل CameraX بطريقة آمنة
 * - ربط Preview + ImageAnalysis
 * - احتواء جميع أخطاء الكاميرا بدون أي crash
 * - تحويل كل مشكلة إلى SystemStatus وإبلاغ Flutter
 *
 * English:
 * Central manager that:
 * - Starts CameraX safely
 * - Binds Preview & ImageAnalysis
 * - Never crashes on camera errors
 * - Reports all issues via SystemStatus
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val statusReporter: SystemStatusReporter
) {

    /**
     * Executor مخصص لتحليل الصور
     *
     * Arabic:
     * نستخدم Thread واحد فقط لتفادي ضغط الذاكرة
     *
     * English:
     * Single-thread executor for image analysis
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    /**
     * CameraProvider هو المتحكم الفعلي بالكاميرا
     */
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * startCamera
     *
     * Arabic:
     * تشغيل الكاميرا وربطها مع Preview و Analyzer
     * هذه الدالة لا يجب أن ترمي أي Exception إلى الخارج
     *
     * English:
     * Starts camera and binds preview & analyzer safely
     */
    fun startCamera(
        preview: Preview.SurfaceProvider,
        analyzer: ImageAnalysis.Analyzer
    ) {

        val providerFuture =
            ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({

            try {
                // الحصول على CameraProvider
                cameraProvider = providerFuture.get()

                /**
                 * Arabic:
                 * مهم جدًا: نفصل أي UseCases قديمة
                 * لتفادي تضارب مع MIUI أو إعادة الدخول للتطبيق
                 *
                 * English:
                 * Always unbind previous use cases
                 */
                cameraProvider?.unbindAll()

                // ---------- Preview ----------
                val previewUseCase = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(preview)
                    }

                // ---------- Image Analysis ----------
                val analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                // ---------- Camera Selector ----------
                val cameraSelector =
                    CameraSelector.DEFAULT_FRONT_CAMERA

                /**
                 * Arabic:
                 * ربط الكاميرا بدورة حياة الـ Activity
                 * CameraX سيتكفل بالإيقاف والتشغيل التلقائي
                 *
                 * English:
                 * Bind camera to lifecycle
                 */
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )

                // ✅ كل شيء يعمل
                statusReporter.report(SystemStatus.OK)

            } catch (e: SecurityException) {
                /**
                 * Arabic:
                 * صلاحية الكاميرا سُحبت (يدويًا أو من النظام)
                 *
                 * English:
                 * Camera permission revoked
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_PERMISSION_REVOKED_BY_SYSTEM
                )

            } catch (e: IllegalStateException) {
                /**
                 * Arabic:
                 * الكاميرا مشغولة (تطبيق آخر / MIUI / HAL)
                 *
                 * English:
                 * Camera is busy or unavailable
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_BUSY
                )

            } catch (e: OutOfMemoryError) {
                /**
                 * Arabic:
                 * ضغط ذاكرة عالي – لا نكمل التحليل
                 *
                 * English:
                 * Memory pressure detected
                 */
                statusReporter.report(
                    SystemStatus.LOW_MEMORY
                )

            } catch (e: Exception) {
                /**
                 * Arabic:
                 * أي خطأ غير متوقع
                 * لا ننهار أبدًا
                 *
                 * English:
                 * Any unexpected internal error
                 */
                Log.e(
                    "CameraManager",
                    "Internal camera error",
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
     * Arabic:
     * إغلاق الكاميرا بطريقة آمنة
     * تُستدعى في onDestroy أو عند إيقاف الـ pipeline
     *
     * English:
     * Safely shuts down camera and executor
     */
    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()

            statusReporter.report(
                SystemStatus.CAMERA_CLOSED
            )

        } catch (e: Exception) {
            /**
             * Arabic:
             * حتى أثناء الإغلاق لا نسمح بحدوث crash
             *
             * English:
             * Shutdown must never crash
             */
            statusReporter.report(
                SystemStatus.INTERNAL_ERROR
            )
        }
    }
}