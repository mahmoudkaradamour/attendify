package com.mahmoud.attendify.face

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * FaceCropper
 *
 * Arabic:
 * مسؤول عن قص الوجه من الصورة الأصلية
 * بطريقة آمنة بدون التسبب في انهيار
 *
 * English:
 * Safely crops face bitmap from source image
 */
object FaceCropper {

    /**
     * cropFace
     *
     * @return Bitmap?  (null إذا كان القص غير صالح)
     */
    fun cropFace(
        source: Bitmap,
        box: FloatArray
    ): Bitmap? {

        val imgW = source.width
        val imgH = source.height

        // Basic sanity check
        if (box.size < 4) return null

        // تحويل من نسب إلى بكسل
        var left = (box[0] * imgW).toInt()
        var top = (box[1] * imgH).toInt()
        var right = ((box[0] + box[2]) * imgW).toInt()
        var bottom = ((box[1] + box[3]) * imgH).toInt()

        // Clamp داخل حدود الصورة
        left = max(0, left)
        top = max(0, top)
        right = min(imgW, right)
        bottom = min(imgH, bottom)

        val width = right - left
        val height = bottom - top

        // ✅ شرط أمان مهم جدًا
        if (width <= 0 || height <= 0) {
            return null
        }

        return Bitmap.createBitmap(
            source,
            left,
            top,
            width,
            height
        )
    }
}