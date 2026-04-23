package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap

/**
 * MiniFASNetV1SEModel
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * First‑generation MiniFASNet with Squeeze‑and‑Excitation (SE).
 *
 * Characteristics:
 * - Lightweight passive Face Anti‑Spoofing model
 * - Optimized for speed over maximum security
 * - Suitable for low‑end and mid‑range devices
 *
 * Architectural role:
 * - Defines model contract (ID, input size, threshold, etc.)
 * - Loads the TFLite model
 * - Performs PREPROCESSING ONLY
 *
 * ✅ Inference, Softmax, Thresholding, and Decision Logic
 *    are fully handled by BaseFASModel.
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * الجيل الأول من MiniFASNet مع Squeeze‑and‑Excitation.
 *
 * الخصائص:
 * - نموذج خفيف لمكافحة تزوير الوجه
 * - مهيأ للسرعة أكثر من أعلى مستوى أمان
 * - مناسب للأجهزة الضعيفة والمتوسطة
 *
 * الدور المعماري:
 * - تعريف عقد النموذج (المعرّف، الحجم، العتبة…)
 * - تحميل نموذج TFLite
 * - تنفيذ المعالجة المسبقة فقط
 *
 * ✅ التنبؤ، Softmax، العتبة، والقرار
 *    كلها مسؤولية BaseFASModel حصراً.
 */
class MiniFASNetV1SEModel(
    context: Context
) : BaseFASModel(context) {

    /* =====================================================
     * 🔐 MODEL CONTRACT
     * =====================================================
     *
     * English:
     * These values inform the BaseFASModel
     * how to interpret and run this model.
     *
     * عربي:
     * هذه القيم تمثل عقد النموذج مع النظام،
     * وهي ثابتة ولا تتغير أثناء التشغيل.
     */

    /** Unique identifier for this model */
    override val id: String =
        "minifasnet_v1se_80x80_light"

    /** Model input size: 80 x 80 RGB */
    override val inputSize: Int = 80

    /**
     * Default threshold
     *
     * English:
     * Lower than V2 to favor recall and speed.
     *
     * عربي:
     * عتبة أقل من V2 لتحقيق سرعة أعلى
     * وتقليل الرفض الخاطئ.
     */
    override val defaultThreshold: Float = 0.80f

    /**
     * GPU is not required nor recommended
     * for this lightweight model.
     */
    override val supportsGpu: Boolean = false

    /** Output tensor mapping:
     *  index 0 → Spoof
     *  index 1 → Real */
    override val realIndex: Int = 1
    override val spoofIndex: Int = 0

    /** Model outputs logits → requires Softmax */
    override val requiresSoftmax: Boolean = true

    /** Model is ACTIVE in production */
    override val isTemporarilyDisabled: Boolean = false

    /* ===================================================== */

    /**
     * Load TFLite model once during initialization
     */
    init {
        loadModel("models/fas/minifasnet_v1se_80x80_light.tflite")
    }

    /**
     * PREPROCESSING
     *
     * =====================================================
     * English:
     * Converts input face Bitmap into model tensor:
     * 1. Resize to 80x80
     * 2. Extract RGB pixel values
     * 3. Normalize each channel to [-1, 1]
     * 4. Pack into Float32 ByteBuffer
     *
     * ✅ Uses reusable buffers:
     * - inputBuffer (Float32)
     * - pixelArray (IntArray)
     *
     * ✅ ZERO allocations per frame.
     *
     * =====================================================
     * عربي:
     * تحويل صورة الوجه إلى Tensor مطابق للنموذج:
     * 1. تصغير الصورة إلى 80×80
     * 2. استخراج قيم RGB
     * 3. تطبيع القيم إلى [-1, 1]
     * 4. تعبئة البيانات في ByteBuffer
     *
     * ✅ بدون أي إنشاء ذاكرة جديد مع كل Frame.
     */
    override fun preprocess(bitmap: Bitmap) {

        // Resize only if needed to avoid unnecessary Bitmap allocation
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

        // Extract pixels into the reusable pixel buffer (NO allocation)
        resized.getPixels(
            pixelArray,
            0,
            inputSize,
            0,
            0,
            inputSize,
            inputSize
        )

        // Prepare input tensor buffer
        inputBuffer.rewind()

        // Normalize and write RGB values
        for (pixel in pixelArray) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Clean up temporary bitmap if created
        if (resized !== bitmap) {
            resized.recycle()
        }
    }
}