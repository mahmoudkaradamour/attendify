package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap

/**
 * MiniFASNetV2Model
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * Second‑generation MiniFASNet Face Anti‑Spoofing model.
 *
 * Characteristics:
 * - Higher accuracy than V1SE
 * - Still lightweight and CPU‑friendly
 * - Designed for RGB, single‑frame FAS
 *
 * Architectural role:
 * - Defines the model contract (ID, size, threshold, etc.)
 * - Loads the TFLite model
 * - Performs preprocessing ONLY
 *
 * ✅ Inference, Softmax, Thresholding, Decision logic
 *    are ALL handled by BaseFASModel.
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * الجيل الثاني من MiniFASNet لمكافحة تزوير الوجه.
 *
 * الخصائص:
 * - أدق من الإصدار V1SE
 * - خفيف ومناسب للأجهزة المتوسطة
 * - يعتمد على إطار واحد (Single‑frame RGB FAS)
 *
 * الدور المعماري:
 * - تعريف عقد النموذج (المعرّف، الحجم، العتبة…)
 * - تحميل نموذج TFLite
 * - تنفيذ المعالجة المسبقة فقط
 *
 * ✅ التنبؤ، Softmax، العتبة، والقرار
 *    كلها مسؤولية BaseFASModel حصراً.
 */
class MiniFASNetV2Model(
    context: Context
) : BaseFASModel(context) {

    /* =====================================================
     * 🔐 MODEL CONTRACT
     * =====================================================
     *
     * English:
     * These properties define how the Base class
     * will run and interpret this model.
     *
     * عربي:
     * هذه الخصائص تمثل عقد النموذج مع النظام،
     * ولا يمكن تجاوزها أو تغييرها أثناء التشغيل.
     */

    /** Unique model identifier */
    override val id: String =
        "minifasnet_v2_80x80_default"

    /** Model input size (80 x 80 RGB) */
    override val inputSize: Int = 80

    /**
     * Default decision threshold
     *
     * English:
     * Tuned during model training.
     *
     * عربي:
     * عتبة القرار الافتراضية
     * مضبوطة أثناء تدريب النموذج.
     */
    override val defaultThreshold: Float = 0.85f

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
        loadModel("models/fas/minifasnet_v2_80x80_default.tflite")
    }

    /**
     * PREPROCESSING
     *
     * =====================================================
     * English:
     * Converts face Bitmap into model input tensor:
     * 1. Resize to 80x80
     * 2. Extract RGB pixels
     * 3. Normalize to [-1, 1]
     * 4. Pack into Float32 ByteBuffer
     *
     * ✅ Uses reused buffers:
     * - inputBuffer (ByteBuffer)
     * - pixelArray (IntArray)
     *
     * ✅ ZERO memory allocation per frame.
     *
     * =====================================================
     * عربي:
     * تحويل صورة الوجه إلى Tensor مطابق للنموذج:
     * 1. تصغير الصورة إلى 80×80
     * 2. استخراج ألوان RGB
     * 3. تطبيع القيم إلى [-1, 1]
     * 4. تعبئة البيانات في ByteBuffer
     *
     * ✅ بدون أي إنشاء ذاكرة جديد لكل Frame.
     */
    override fun preprocess(bitmap: Bitmap) {

        // Resize only if necessary to avoid extra Bitmap allocation
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

        // Extract pixels into reusable buffer (NO allocation)
        resized.getPixels(
            pixelArray,
            0,
            inputSize,
            0,
            0,
            inputSize,
            inputSize
        )

        // Reset input buffer before writing
        inputBuffer.rewind()

        // Normalize RGB values to [-1, 1] and pack to ByteBuffer
        for (pixel in pixelArray) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Recycle temporary resized bitmap if it was created
        if (resized !== bitmap) {
            resized.recycle()
        }
    }
}