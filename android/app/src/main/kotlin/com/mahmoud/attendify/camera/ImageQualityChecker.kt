package com.mahmoud.attendify.camera

import android.graphics.Bitmap
import com.mahmoud.attendify.camera.SystemStatus
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ImageQualityChecker
 *
 * Arabic:
 * طبقة فحص جودة الصورة قبل أي معالجة ML.
 * هدفها:
 * - منع تمرير صور غير صالحة للـ Face Detection / Recognition
 * - تقليل الأخطاء والنتائج غير الموثوقة
 * - إبلاغ Flutter بحالة واضحة تُرشد المستخدم
 *
 * English:
 * Image quality gate executed BEFORE any ML step.
 * Ensures only usable frames reach face detection / recognition.
 */
object ImageQualityChecker {

    // =====================================================
    // ✅ Tunable thresholds (يمكن تعديلها لاحقًا بسهولة)
    // =====================================================

    // Brightness thresholds (0..255)
    private const val MIN_BRIGHTNESS = 40.0
    private const val MAX_BRIGHTNESS = 215.0

    // Contrast threshold (variance of brightness)
    private const val MIN_CONTRAST = 500.0

    // Blur threshold (variance of Laplacian)
    private const val MIN_SHARPNESS = 80.0

    // Face size thresholds (percentage of image)
    private const val MIN_FACE_RATIO = 0.12  // face too far
    private const val MAX_FACE_RATIO = 0.70  // face too close

    // =====================================================
    // ✅ Public API
    // =====================================================

    /**
     * checkFrame
     *
     * Arabic:
     * يفحص جودة الصورة كاملة (قبل كشف الوجه).
     * يُستخدم في FrameAnalyzer مباشرة بعد تحويل الصورة إلى Bitmap.
     *
     * English:
     * Checks full-frame quality BEFORE face detection.
     */
    fun checkFrame(bitmap: Bitmap): SystemStatus {

        // ---------- Basic sanity ----------
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return SystemStatus.FRAME_CORRUPTED
        }

        // ---------- Brightness ----------
        val brightness = calculateMeanBrightness(bitmap)
        if (brightness < MIN_BRIGHTNESS) {
            return SystemStatus.IMAGE_TOO_DARK
        }
        if (brightness > MAX_BRIGHTNESS) {
            return SystemStatus.IMAGE_TOO_BRIGHT
        }

        // ---------- Contrast ----------
        val contrast = calculateBrightnessVariance(bitmap)
        if (contrast < MIN_CONTRAST) {
            return SystemStatus.IMAGE_LOW_CONTRAST
        }

        // ---------- Blur ----------
        val sharpness = calculateLaplacianVariance(bitmap)
        if (sharpness < MIN_SHARPNESS) {
            return SystemStatus.IMAGE_BLURRY
        }

        // ✅ Frame acceptable
        return SystemStatus.OK
    }

    /**
     * checkFaceSize
     *
     * Arabic:
     * يفحص حجم الوجه بعد كشفه (بعد BlazeFace).
     * يمنع:
     * - الوجه البعيد جدًا
     * - الوجه القريب جدًا والمقصوص
     *
     * English:
     * Checks detected face size relative to frame.
     */
    fun checkFaceSize(
        faceWidth: Int,
        faceHeight: Int,
        frameWidth: Int,
        frameHeight: Int
    ): SystemStatus {

        val faceArea = faceWidth * faceHeight
        val frameArea = frameWidth * frameHeight

        if (frameArea <= 0) {
            return SystemStatus.FRAME_CORRUPTED
        }

        val ratio =
            faceArea.toDouble() / frameArea.toDouble()

        return when {
            ratio < MIN_FACE_RATIO ->
                SystemStatus.FACE_TOO_FAR

            ratio > MAX_FACE_RATIO ->
                SystemStatus.FACE_TOO_CLOSE

            else ->
                SystemStatus.OK
        }
    }

    // =====================================================
    // ✅ Internal calculations
    // =====================================================

    /**
     * calculateMeanBrightness
     *
     * Arabic:
     * حساب متوسط الإضاءة باستخدام luminance (Y).
     * سريع ومستقر على كل الأجهزة.
     *
     * English:
     * Computes average luminance of image.
     */
    private fun calculateMeanBrightness(bitmap: Bitmap): Double {

        var sum = 0.0
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height

        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Standard luminance approximation
                val yVal =
                    0.299 * r + 0.587 * g + 0.114 * b

                sum += yVal
            }
        }

        // Sampling reduces cost; adjust scale
        val samples =
            (width / 4) * (height / 4)

        return if (samples > 0) sum / samples else 0.0
    }

    /**
     * calculateBrightnessVariance
     *
     * Arabic:
     * يقيس التباين العام للصورة.
     * انخفاض التباين يعني:
     * - غبار
     * - ضباب
     * - Exposure سيئ
     *
     * English:
     * Measures brightness variance (global contrast).
     */
    private fun calculateBrightnessVariance(bitmap: Bitmap): Double {

        val mean =
            calculateMeanBrightness(bitmap)

        var varianceSum = 0.0
        val width = bitmap.width
        val height = bitmap.height

        var samples = 0

        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val yVal =
                    0.299 * r + 0.587 * g + 0.114 * b

                varianceSum += (yVal - mean).pow(2)
                samples++
            }
        }

        return if (samples > 0)
            varianceSum / samples
        else
            0.0
    }

    /**
     * calculateLaplacianVariance
     *
     * Arabic:
     * أفضل طريقة صناعية لكشف الضبابية.
     * تعتمد على تباين الحواف.
     *
     * English:
     * Computes variance of Laplacian (sharpness metric).
     */
    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {

        var laplacianSum = 0.0
        var laplacianSqSum = 0.0
        var count = 0

        for (y in 1 until bitmap.height - 1 step 2) {
            for (x in 1 until bitmap.width - 1 step 2) {

                val center =
                    luminance(bitmap.getPixel(x, y))
                val left =
                    luminance(bitmap.getPixel(x - 1, y))
                val right =
                    luminance(bitmap.getPixel(x + 1, y))
                val top =
                    luminance(bitmap.getPixel(x, y - 1))
                val bottom =
                    luminance(bitmap.getPixel(x, y + 1))

                // Laplacian operator
                val lap =
                    abs(4 * center - left - right - top - bottom)

                laplacianSum += lap
                laplacianSqSum += lap * lap
                count++
            }
        }

        if (count == 0) return 0.0

        val mean = laplacianSum / count
        return (laplacianSqSum / count) - mean * mean
    }

    /**
     * luminance
     *
     * Arabic:
     * حساب luminance لبكسل واحد.
     *
     * English:
     * Computes luminance of a single pixel.
     */
    private fun luminance(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}