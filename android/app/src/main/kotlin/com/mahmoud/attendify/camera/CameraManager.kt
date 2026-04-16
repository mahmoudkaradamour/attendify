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
     * نستخدم Thread واحد فقط لتفادي:
     * - ضغط الذاكرة
     * - تداخل frames
     */
    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    /**
     * المتحكم الفعلي بالكاميرا (CameraX entry point)
     */
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * startCamera
     *
     * مسؤول عن:
     * - تهيئة CameraX
     * - ربط Preview
     * - ربط ImageAnalysis
     *
     * ⚠️ مهم:
     * هذه الدالة لا ترمي أي exception للخارج
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
                 * نفصل أي UseCases سابقة
                 * (حل مشاكل MIUI / إعادة فتح الكاميرا)
                 */
                cameraProvider?.unbindAll()

                // -------- Preview --------
                val previewUseCase =
                    Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(preview)
                        }

                // -------- Image Analysis --------
                val analysisUseCase =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, analyzer)
                        }

                // -------- Camera Selector --------
                val cameraSelector =
                    CameraSelector.DEFAULT_FRONT_CAMERA

                /**
                 * ربط الكاميرا بدورة حياة الـ Activity
                 * CameraX يدير الإيقاف والتشغيل تلقائيًا
                 */
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )

                // ✅ التشغيل ناجح
                statusReporter.report(SystemStatus.OK)

            } catch (_: SecurityException) {
                /**
                 * صلاحية الكاميرا سُحبت أثناء التشغيل
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_PERMISSION_REVOKED_BY_SYSTEM
                )

            } catch (_: IllegalStateException) {
                /**
                 * الكاميرا مشغولة أو غير متاحة
                 */
                statusReporter.report(
                    SystemStatus.CAMERA_BUSY
                )

            } catch (_: OutOfMemoryError) {
                /**
                 * ضغط ذاكرة مرتفع
                 */
                statusReporter.report(
                    SystemStatus.LOW_MEMORY
                )

            } catch (e: Exception) {
                /**
                 * أي خطأ غير متوقع
                 * لا ننهار أبدًا
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
     * إغلاق الكاميرا بطريقة آمنة.
     * تُستدعى من onDestroy أو عند إيقاف الـ pipeline.
     */
    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()

            statusReporter.report(
                SystemStatus.CAMERA_CLOSED
            )

        } catch (_: Exception) {
            /**
             * حتى أثناء الإغلاق لا نسمح بحدوث crash
             */
            statusReporter.report(
                SystemStatus.INTERNAL_ERROR
            )
        }
    }
}