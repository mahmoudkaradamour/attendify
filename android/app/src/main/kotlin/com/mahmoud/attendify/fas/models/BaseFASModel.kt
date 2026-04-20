package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.ml.InterpreterFactory
import com.mahmoud.attendify.ml.GpuPolicy
import com.mahmoud.attendify.metrics.FasRuntimeMetrics
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * BaseFASModel
 *
 * ===========================================
 * Base class لكل نماذج Face Anti‑Spoofing.
 *
 * المسؤوليات:
 * ✅ تحميل نموذج TFLite من assets (مرة واحدة)
 * ✅ إنشاء Interpreter حسب سياسة GPU/NNAPI
 * ✅ قياس زمن تحضير الـ Interpreter (Benchmark)
 * ✅ توفير أدوات مشتركة (resize / input buffer)
 *
 * ❌ لا يعرف:
 * - preprocessing بالتفصيل
 * - normalization
 * - تفسير المخرجات
 *
 * هذا كله مسؤولية الـ Wrapper المختص بكل نموذج.
 */
abstract class BaseFASModel(
    protected val context: Context
) : FASModel {

    protected lateinit var interpreter: Interpreter
    private lateinit var modelBuffer: ByteBuffer

    private val interpreterFactory = InterpreterFactory()

    /**
     * تحميل ملف النموذج من assets إلى الذاكرة.
     *
     * يجب استدعاؤها مرة واحدة فقط داخل init{} للـ Wrapper.
     */
    protected fun loadModel(assetPath: String) {
        val assetFileDescriptor =
            context.assets.openFd(assetPath)

        val inputStream =
            FileInputStream(assetFileDescriptor.fileDescriptor)

        val fileChannel: FileChannel =
            inputStream.channel

        modelBuffer =
            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )
    }

    /**
     * prepare
     *
     * تهيئة الـ Interpreter حسب سياسة GPU.
     * يتم استدعاؤها من FASOrchestrator قبل كل تحليل.
     *
     * @param useGpu
     * قرار الإدارة (Policy) باستخدام GPU أم لا.
     */
    override fun prepare(useGpu: Boolean) {

        val policy =
            if (useGpu && supportsGpu)
                GpuPolicy.FORCED_ON
            else
                GpuPolicy.FORCED_OFF

        val startNs = System.nanoTime()

        interpreter =
            interpreterFactory.createInterpreter(
                model = modelBuffer,
                policy = policy,
                userPrefersGpu = useGpu
            )

        val durationMs =
            (System.nanoTime() - startNs) / 1_000_000

        // ✅ تسجيل زمن التحضير (Benchmark)
        FasRuntimeMetrics.log(
            modelId = id,
            useGpu = useGpu,
            stage = "prepare",
            durationMs = durationMs
        )
    }

    /**
     * إنشاء Input Buffer بحجم:
     * [1, size, size, 3] Float32
     */
    protected fun createInputBuffer(size: Int): ByteBuffer =
        ByteBuffer
            .allocateDirect(1 * size * size * 3 * 4)
            .order(ByteOrder.nativeOrder())

    /**
     * تصغير الصورة إلى حجم النموذج.
     */
    protected fun resizeBitmap(
        bitmap: Bitmap,
        size: Int
    ): Bitmap =
        Bitmap.createScaledBitmap(
            bitmap,
            size,
            size,
            true
        )
}