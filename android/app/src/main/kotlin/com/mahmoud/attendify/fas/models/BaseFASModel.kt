package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.metrics.FasRuntimeMetrics
import com.mahmoud.attendify.ml.InterpreterFactory
import com.mahmoud.attendify.ml.GpuPolicy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max

/**
 * BaseFASModel
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * Central, final, production‑grade implementation of the
 * Face Anti‑Spoofing (FAS) inference pipeline.
 *
 * This class:
 * - Enforces the Template Method Pattern
 * - Acts as the Single Source of Truth
 * - Centralizes:
 *   - Interpreter lifecycle (CPU / GPU)
 *   - Memory buffers
 *   - Softmax (numerically stable)
 *   - Thresholding & decision logic
 *   - Performance metrics
 *
 * Subclasses are intentionally LIMITED to preprocessing only.
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * القلب المعماري النهائي لنظام مكافحة تزوير الوجه.
 *
 * هذا الكلاس:
 * - يفرض Template Method Pattern بشكل صارم
 * - يمثل المصدر الوحيد للحقيقة (Single Source of Truth)
 * - يحتكر:
 *   - دورة حياة Interpreter (CPU / GPU)
 *   - إدارة الذاكرة
 *   - Softmax الآمن رياضيًا
 *   - منطق القرار والـ Threshold
 *   - تسجيل الأداء
 *
 * النماذج الفرعية يُسمح لها فقط بالمعالجة المسبقة.
 */
abstract class BaseFASModel(
    protected val context: Context
) : FASModel {

    /* =====================================================
     * 🔐 MODEL CONTRACT (Enforced by FASModel)
     * =====================================================
     *
     * English:
     * These properties define the immutable identity
     * and expected behavior of each FAS model.
     *
     * عربي:
     * هذه الخصائص تمثل عقد النموذج وهويته الثابتة،
     * ولا يمكن تجاوزها أو العبث بها.
     */

    abstract override val id: String
    abstract override val inputSize: Int
    abstract override val defaultThreshold: Float
    abstract override val supportsGpu: Boolean

    /** Output tensor indices
     * English: Class indices in model output
     * عربي: ترتيب الفئات في مخرجات النموذج */
    abstract val realIndex: Int
    abstract val spoofIndex: Int

    /** English: Does model output logits instead of probabilities?
     * عربي: هل مخرجات النموذج Logits تحتاج Softmax؟ */
    abstract val requiresSoftmax: Boolean

    /** English: Safe architectural disable switch
     * عربي: مفتاح تعطيل معماري آمن للنموذج */
    abstract val isTemporarilyDisabled: Boolean

    /* ===================================================== */

    /** English: TFLite Interpreter instance
     * عربي: كائن Interpreter الخاص بـ TensorFlow Lite */
    protected var interpreter: Interpreter? = null

    protected var isGpuEnabled: Boolean = false

    /** English: Memory‑mapped TFLite model
     * عربي: النموذج المحمّل من assets إلى الذاكرة */
    protected lateinit var modelBuffer: ByteBuffer

    /* =====================================================
     * ♻️ REUSABLE BUFFERS (Zero allocation during runtime)
     * =====================================================
     *
     * English:
     * All buffers are allocated ONCE and reused to:
     * - prevent GC pressure
     * - avoid thermal throttling
     *
     * عربي:
     * جميع الـ buffers تُنشأ مرة واحدة فقط
     * لمنع GC وارتفاع حرارة الجهاز.
     */

    /** Input tensor buffer: [1, SIZE, SIZE, 3] Float32 */
    protected val inputBuffer: ByteBuffer by lazy {
        ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
    }

    /** Output tensor buffer: [1, Classes] */
    private val outputBuffer by lazy {
        val size = maxOf(realIndex, spoofIndex) + 1
        Array(1) { FloatArray(size) }
    }

    /**
     * ✅ CRITICAL FIX (from expert review)
     *
     * English:
     * Reusable pixel buffer to avoid allocating IntArray
     * on every frame during preprocessing.
     *
     * عربي:
     * مصفوفة Pixels مُعاد استخدامها لمنع إنشاء
     * IntArray جديد مع كل Frame.
     *
     * This eliminates OOM & GC churn during streaming.
     */
    protected val pixelArray: IntArray by lazy {
        IntArray(inputSize * inputSize)
    }

    /* ===================================================== */

    /**
     * Load TFLite model from assets
     *
     * English:
     * Memory‑maps the model once.
     *
     * عربي:
     * تحميل النموذج إلى الذاكرة مرة واحدة فقط.
     */
    protected fun loadModel(assetPath: String) {
        val fd = context.assets.openFd(assetPath)
        FileInputStream(fd.fileDescriptor).use { input ->
            val channel = input.channel
            modelBuffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    /**
     * PREPROCESS ONLY
     *
     * English:
     * Subclasses must ONLY implement preprocessing:
     * - resize
     * - normalization
     * - channel ordering
     *
     * ❌ No inference
     * ❌ No decision logic
     *
     * عربي:
     * النماذج الفرعية مسؤولة فقط عن المعالجة المسبقة.
     */
    protected abstract fun preprocess(bitmap: Bitmap)

    /**
     * ✅ FINAL INFERENCE PIPELINE
     *
     * English:
     * This method is FINAL to protect system integrity.
     *
     * عربي:
     * هذه الدالة نهائية ولا يمكن تجاوزها.
     */
    final override fun analyze(faceBitmap: Bitmap): FASResult {

        if (isTemporarilyDisabled) {
            return FASResult.Inconclusive(
                "Model $id is temporarily disabled"
            )
        }

        val localInterpreter = interpreter
            ?: return FASResult.Inconclusive(
                "Interpreter for model $id not initialized"
            )

        val startNs = System.nanoTime()

        return try {
            inputBuffer.rewind()
            preprocess(faceBitmap)

            localInterpreter.run(inputBuffer, outputBuffer)

            val durationMs =
                (System.nanoTime() - startNs) / 1_000_000

            FasRuntimeMetrics.log(
                modelId = id,
                useGpu = isGpuEnabled,
                stage = "inference",
                durationMs = durationMs
            )

            interpretOutput(outputBuffer[0])

        } catch (e: Exception) {
            FASResult.Inconclusive(
                "Inference failed in $id: ${e.message}"
            )
        }
    }

    /**
     * DECISION LOGIC (Centralized)
     *
     * English:
     * Converts raw model output into a safe FASResult.
     *
     * عربي:
     * تحويل مخرجات النموذج إلى نتيجة آمنة ومفسَّرة.
     */
    private fun interpretOutput(output: FloatArray): FASResult {

        var realVal = output.getOrNull(realIndex)
            ?: return FASResult.Inconclusive("Invalid REAL index")

        var spoofVal = output.getOrNull(spoofIndex)
            ?: return FASResult.Inconclusive("Invalid SPOOF index")

        // ✅ Numerically‑stable Softmax
        if (requiresSoftmax) {
            val maxLogit = max(realVal, spoofVal)
            val expReal = exp(realVal - maxLogit)
            val expSpoof = exp(spoofVal - maxLogit)
            val sum = expReal + expSpoof

            if (sum == 0f || !sum.isFinite()) {
                return FASResult.Inconclusive("Softmax numerical failure")
            }

            realVal = expReal / sum
            spoofVal = expSpoof / sum
        }

        if (!realVal.isFinite() || !spoofVal.isFinite()) {
            return FASResult.Inconclusive("Non‑finite output values")
        }

        return when {
            realVal >= defaultThreshold ->
                FASResult.Real(confidence = realVal)

            spoofVal >= defaultThreshold ->
                FASResult.Spoof(confidence = spoofVal)

            else ->
                FASResult.Inconclusive(
                    "Low confidence (real=$realVal spoof=$spoofVal)"
                )
        }
    }

    /**
     * Interpreter preparation
     *
     * English:
     * Safely creates or recreates the Interpreter
     * using InterpreterFactory.
     *
     * عربي:
     * تهيئة Interpreter بشكل آمن مع CPU / GPU.
     */
    override fun prepare(useGpu: Boolean) {

        if (interpreter != null && isGpuEnabled == useGpu) {
            return
        }

        interpreter?.close()
        interpreter = null

        isGpuEnabled = useGpu

        val policy =
            if (useGpu && supportsGpu)
                GpuPolicy.FORCED_ON
            else
                GpuPolicy.FORCED_OFF

        val interpreterFactory = InterpreterFactory()

        val startNs = System.nanoTime()

        interpreter =
            interpreterFactory.createInterpreter(
                model = modelBuffer,
                policy = policy,
                userPrefersGpu = useGpu
            )

        val durationMs =
            (System.nanoTime() - startNs) / 1_000_000

        FasRuntimeMetrics.log(
            modelId = id,
            useGpu = useGpu,
            stage = "prepare",
            durationMs = durationMs
        )
    }

    /**
     * Native resource cleanup
     *
     * English:
     * Must be called when model is no longer needed.
     *
     * عربي:
     * تحرير موارد TFLite الأصلية.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}