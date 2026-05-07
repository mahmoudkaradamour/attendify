package com.mahmoud.attendify.camera

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.pow

/**
 * =============================================================================
 * 🧠 ImageQualityChecker — Deterministic Visual Quality Gate
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 SYSTEM ROLE
 * -----------------------------------------------------------------------------
 *
 * This component acts as a **pre-ML validation filter**:
 *
 *   Input → Quality Gate → ML Pipeline
 *
 * Only valid frames proceed further.
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE
 * -----------------------------------------------------------------------------
 *
 *   Raw Frame
 *      │
 *      ▼
 *   Brightness Check
 *      │
 *      ▼
 *   Contrast Check
 *      │
 *      ▼
 *   Sharpness Check
 *      │
 *      ▼
 *   ACCEPT / REJECT
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY + STABILITY IMPACT
 * -----------------------------------------------------------------------------
 *
 * Prevents:
 * - Garbage inputs to ML models
 * - False detection spikes
 * - Model instability
 * - Adversarial low-quality exploits
 *
 */
object ImageQualityChecker {

    /* =========================================================================
     * 🎛 THRESHOLDS
     * ========================================================================= */

    private const val MIN_BRIGHTNESS = 40.0
    private const val MAX_BRIGHTNESS = 215.0

    private const val MIN_CONTRAST = 500.0

    private const val MIN_SHARPNESS = 80.0

    private const val MIN_FACE_RATIO = 0.12
    private const val MAX_FACE_RATIO = 0.70

    /* =========================================================================
     * 🚪 ENTRY — FULL FRAME VALIDATION
     * ========================================================================= */

    fun checkFrame(bitmap: Bitmap): SystemStatus {

        /* ------------------------------------------------------------
         * ✅ 1. SANITY CHECK
         * ------------------------------------------------------------ */
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            return SystemStatus.FRAME_CORRUPTED
        }

        /* ------------------------------------------------------------
         * ✅ 2. BRIGHTNESS
         * ------------------------------------------------------------ */
        val brightness = calculateMeanBrightness(bitmap)

        if (brightness < MIN_BRIGHTNESS) {
            return SystemStatus.IMAGE_TOO_DARK
        }

        if (brightness > MAX_BRIGHTNESS) {
            return SystemStatus.IMAGE_TOO_BRIGHT
        }

        /* ------------------------------------------------------------
         * ✅ 3. CONTRAST
         * ------------------------------------------------------------ */
        val contrast = calculateBrightnessVariance(bitmap)

        if (contrast < MIN_CONTRAST) {
            return SystemStatus.IMAGE_LOW_CONTRAST
        }

        /* ------------------------------------------------------------
         * ✅ 4. SHARPNESS
         * ------------------------------------------------------------ */
        val sharpness = calculateLaplacianVariance(bitmap)

        if (sharpness < MIN_SHARPNESS) {
            return SystemStatus.IMAGE_BLURRY
        }

        return SystemStatus.OK
    }

    /* =========================================================================
     * 👤 FACE SIZE VALIDATION (POST-DETECTION)
     * ========================================================================= */

    /**
     * NOTE:
     * This is intentionally not used yet.
     * Will be integrated after detection stage is finalized.
     */
    @Suppress("unused")
    fun checkFaceSize(
        faceWidth: Int,
        faceHeight: Int,
        frameWidth: Int,
        frameHeight: Int
    ): SystemStatus {

        val frameArea = frameWidth * frameHeight

        if (frameArea <= 0) {
            return SystemStatus.FRAME_CORRUPTED
        }

        val faceArea = faceWidth * faceHeight

        val ratio = faceArea.toDouble() / frameArea.toDouble()

        return when {
            ratio < MIN_FACE_RATIO -> SystemStatus.FACE_TOO_FAR
            ratio > MAX_FACE_RATIO -> SystemStatus.FACE_TOO_CLOSE
            else -> SystemStatus.OK
        }
    }

    /* =========================================================================
     * 🔬 CORE METRICS
     * ========================================================================= */

    private fun calculateMeanBrightness(bitmap: Bitmap): Double {

        var sum = 0.0
        var samples = 0

        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y) // ✅ faster for loops
                sum += luminance(pixel)
                samples++
            }
        }

        return if (samples > 0) sum / samples else 0.0
    }

    private fun calculateBrightnessVariance(bitmap: Bitmap): Double {

        val mean = calculateMeanBrightness(bitmap)

        var varianceSum = 0.0
        var samples = 0

        val width = bitmap.width
        val height = bitmap.height

        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {

                val pixel = bitmap.getPixel(x, y)
                val value = luminance(pixel)

                varianceSum += (value - mean).pow(2)
                samples++
            }
        }

        return if (samples > 0) varianceSum / samples else 0.0
    }

    private fun calculateLaplacianVariance(bitmap: Bitmap): Double {

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        val width = bitmap.width
        val height = bitmap.height

        for (y in 1 until height - 1 step 2) {
            for (x in 1 until width - 1 step 2) {

                val center = luminance(bitmap.getPixel(x, y))
                val left = luminance(bitmap.getPixel(x - 1, y))
                val right = luminance(bitmap.getPixel(x + 1, y))
                val top = luminance(bitmap.getPixel(x, y - 1))
                val bottom = luminance(bitmap.getPixel(x, y + 1))

                val lap = abs(4 * center - left - right - top - bottom)

                sum += lap
                sumSq += lap * lap
                count++
            }
        }

        if (count == 0) return 0.0

        val mean = sum / count

        return (sumSq / count) - mean * mean
    }

    private fun luminance(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}