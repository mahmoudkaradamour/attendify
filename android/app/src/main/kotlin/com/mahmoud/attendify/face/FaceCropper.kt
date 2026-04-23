package com.mahmoud.attendify.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * FaceCropper
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * Central utility responsible for cropping and resizing
 * face regions from a full camera frame.
 *
 * This implementation is SECURITY‑AWARE and FAS‑READY:
 * - Expands the crop area beyond the tight face box
 * - Preserves background context (essential for FAS)
 * - Prevents out‑of‑bounds cropping
 *
 * This design significantly improves resistance against:
 * - Printed photo attacks
 * - Screen replay attacks
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * أداة مركزية مسؤولة عن قص وإعادة تحجيم منطقة الوجه
 * من إطار الكاميرا الكامل.
 *
 * هذا التنفيذ مُصمم بوعي أمني:
 * - يوسّع منطقة القص خارج حدود الوجه الضيقة
 * - يحافظ على جزء من الخلفية (مهم جدًا لـ FAS)
 * - يمنع أي قص خارج حدود الصورة
 *
 * هذا الأسلوب يعزز مقاومة:
 * - الصور المطبوعة
 * - هجمات العرض عبر الشاشة
 */
object FaceCropper {

    private const val TAG = "FaceCropper"

    /**
     * Crop and resize face region for ML models.
     *
     * =====================================================
     * English:
     * Crops a face region using an EXPANDED bounding box
     * strategy (not a tight crop), then resizes it to
     * the target model input size.
     *
     * The expansion factor is critical for FAS accuracy.
     *
     * =====================================================
     * عربي:
     * قص الوجه باستخدام Bounding Box موسّع
     * (وليس قصًا ضيقًا)، ثم إعادة تحجيمه
     * إلى الحجم المطلوب للنموذج.
     *
     * توسيع منطقة القص عامل أساسي
     * لرفع دقة نماذج مكافحة التزوير.
     *
     * @param sourceBitmap  Full camera frame
     * @param faceBox       Face bounding box (from detector)
     * @param targetSize    Target model input size (e.g. 80, 128)
     * @param expansionFactor
     *        English: Amount of expansion applied around face.
     *        Arabic: معامل توسيع منطقة القص حول الوجه.
     *
     *        Recommended values:
     *        - 2.0f → minimal safe expansion
     *        - 2.5f → balanced (✅ recommended)
     *        - 3.0f → maximum security
     */
    fun cropAndResize(
        sourceBitmap: Bitmap,
        faceBox: Rect,
        targetSize: Int,
        expansionFactor: Float = 2.5f
    ): Bitmap? {

        if (targetSize <= 0) {
            Log.e(TAG, "Invalid target size: $targetSize")
            return null
        }

        val frameWidth = sourceBitmap.width
        val frameHeight = sourceBitmap.height

        if (frameWidth <= 0 || frameHeight <= 0) {
            Log.e(TAG, "Invalid source bitmap dimensions")
            return null
        }

        /* --------------------------------------------------
         * 1️⃣ Compute expanded bounding box
         * --------------------------------------------------
         *
         * English:
         * Expand face box equally in all directions.
         *
         * عربي:
         * توسيع صندوق الوجه بالتساوي في كل الاتجاهات.
         */
        val boxWidth = faceBox.width()
        val boxHeight = faceBox.height()

        val expansionWidth = (boxWidth * (expansionFactor - 1f) / 2f).toInt()
        val expansionHeight = (boxHeight * (expansionFactor - 1f) / 2f).toInt()

        val left = max(0, faceBox.left - expansionWidth)
        val top = max(0, faceBox.top - expansionHeight)
        val right = min(frameWidth, faceBox.right + expansionWidth)
        val bottom = min(frameHeight, faceBox.bottom + expansionHeight)

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(TAG, "Invalid expanded crop region")
            return null
        }

        /* --------------------------------------------------
         * 2️⃣ Crop expanded region
         * --------------------------------------------------
         */
        val cropped = try {
            Bitmap.createBitmap(
                sourceBitmap,
                left,
                top,
                cropWidth,
                cropHeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop bitmap", e)
            return null
        }

        /* --------------------------------------------------
         * 3️⃣ Resize to model input size
         * --------------------------------------------------
         *
         * English:
         * Final resize to target ML input dimensions.
         *
         * عربي:
         * إعادة تحجيم الصورة إلى حجم إدخال النموذج.
         */
        val resized = try {
            Bitmap.createScaledBitmap(
                cropped,
                targetSize,
                targetSize,
                true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resize cropped bitmap", e)
            cropped.recycle()
            return null
        }

        // Free intermediate bitmap to avoid native memory leaks
        cropped.recycle()

        return resized
    }
}