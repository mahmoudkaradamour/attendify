package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap

/**
 * FastFASNetV3Model
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * High‑Security FastFASNet V3 Face Anti‑Spoofing model.
 *
 * Characteristics:
 * - Higher security and stricter threshold
 * - Designed for environments requiring strong spoof resistance
 * - Slower than MiniFASNet, but more conservative
 *
 * Architectural role:
 * - Defines model contract (ID, size, threshold, output mapping)
 * - Loads the TFLite model
 * - Performs PREPROCESSING ONLY
 *
 * ✅ Inference, Softmax, Thresholding, and Decision Logic
 *    are fully centralized in BaseFASModel.
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * نموذج FastFASNet V3 عالي الأمان لمكافحة تزوير الوجه.
 *
 * الخصائص:
 * - مستوى أمان أعلى وعتبة قرار صارمة
 * - مخصص للبيئات الحساسة أمنيًا
 * - أبطأ نسبيًا من MiniFASNet لكنه أكثر تحفظًا
 *
 * الدور المعماري:
 * - تعريف عقد النموذج (المعرّف، الحجم، العتبة، ترتيب المخرجات)
 * - تحميل نموذج TFLite
 * - تنفيذ المعالجة المسبقة فقط
 *
 * ✅ التنبؤ، Softmax، العتبة، ومنطق القرار
 *    كلها مسؤولية BaseFASModel حصراً.
 */
class FastFASNetV3Model(
    context: Context
) : BaseFASModel(context) {

    /* =====================================================
     * 🔐 MODEL CONTRACT
     * =====================================================
     *
     * English:
     * These values describe how BaseFASModel
     * should interpret the output of this model.
     *
     * عربي:
     * هذه القيم تمثل عقد النموذج مع النظام،
     * وهي مضبوطة وفق تدريب النموذج.
     */

    /** Unique identifier for this high‑security model */
    override val id: String =
        "fastfasnet_v3_128x128_highsec"

    /** Model input size: 128 x 128 RGB */
    override val inputSize: Int = 128

    /**
     * Default threshold
     *
     * English:
     * High threshold to minimize false acceptance.
     *
     * عربي:
     * عتبة قرار مرتفعة لتقليل القبول الخاطئ
     * خاصة في البيئات الحساسة.
     */
    override val defaultThreshold: Float = 0.90f

    /**
     * GPU support:
     *
     * English:
     * Disabled here to ensure deterministic behavior
     * and avoid GPU‑related inconsistencies.
     *
     * عربي:
     * معطّل لتفادي تباين النتائج على الأجهزة المختلفة.
     */
    override val supportsGpu: Boolean = false

    /** Output tensor mapping:
     *  index 0 → Spoof
     *  index 1 → Real */
    override val realIndex: Int = 1
    override val spoofIndex: Int = 0

    /** Model outputs logits → requires Softmax */
    override val requiresSoftmax: Boolean = true

    /**
     * Model is ACTIVE
     *
     * English:
     * This model is enabled for production use
     * through policy selection.
     *
     * عربي:
     * النموذج مفعّل وجاهز للاستخدام الإنتاجي
     * ويتم اختياره عبر الـ Policy فقط.
     */
    override val isTemporarilyDisabled: Boolean = false

    /* ===================================================== */

    /**
     * Load FastFASNet V3 TFLite model once
     */
    init {
        loadModel("models/fas/fastfasnet_v3_128x128_highsec.tflite")
    }

    /**
     * PREPROCESSING
     *
     * =====================================================
     * English:
     * Converts face Bitmap into model tensor:
     * 1. Resize to 128x128
     * 2. Extract RGB values
     * 3. Normalize channels to [-1, 1]
     * 4. Pack Float32 values into input buffer
     *
     * ✅ Uses reusable buffers:
     * - inputBuffer (Float32)
     * - pixelArray (IntArray)
     *
     * ✅ ZERO heap allocation per frame.
     *
     * =====================================================
     * عربي:
     * تحويل صورة الوجه إلى Tensor مطابق لنموذج FastFASNet V3:
     * 1. تصغير الصورة إلى 128×128
     * 2. استخراج قيم RGB
     * 3. تطبيع القيم إلى [-1, 1]
     * 4. تعبئة البيانات في ByteBuffer
     *
     * ✅ بدون أي إنشاء ذاكرة جديد مع كل Frame.
     */
    override fun preprocess(bitmap: Bitmap) {

        // Resize only if required to reduce Bitmap allocations
        val resized =
            if (bitmap.width == inputSize && bitmap.height == inputSize) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(
                    bitmap,
                    inputSize,
                    inputSize,
                    true
                )
            }

        // Extract pixels into reusable pixel buffer (NO allocation)
        resized.getPixels(
            pixelArray,
            0,
            inputSize,
            0,
            0,
            inputSize,
            inputSize
        )

        // Reset input tensor buffer
        inputBuffer.rewind()

        // Normalize RGB channels and pack Float32 values
        for (pixel in pixelArray) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Recycle temporary bitmap if created
        if (resized !== bitmap) {
            resized.recycle()
        }
    }
}