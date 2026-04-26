package com.mahmoud.attendify.system.device

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * TextureQualityAnalyzer
 *
 * ---------------------------------------------------------------------------
 * PURPOSE (Why this file exists):
 * ---------------------------------------------------------------------------
 * Estimates the **micro-texture richness** of a face image.
 *
 * In plain words:
 * ----------------
 * This component answers ONE critical question:
 *
 *   ❓ "Does this image still contain natural skin texture,
 *      or has it been smoothed / beautified by the camera HAL?"
 *
 * ---------------------------------------------------------------------------
 * WHY THIS IS A SECURITY COMPONENT (NOT IMAGE QUALITY):
 * ---------------------------------------------------------------------------
 * Many OEMs (Xiaomi, OPPO, Vivo, etc.) apply **beautification filters
 * at the hardware level (ISP / HAL)**.
 *
 * Effects of such filters:
 * ------------------------
 * - Remove micro skin details
 * - Blur pore-level variations
 * - Make REAL faces look "flat"
 *
 * Resulting danger:
 * -----------------
 * - Liveness / FAS models rely on micro-textures
 * - Smoothed real faces may be falsely rejected
 * - Or worse: spoof detection logic becomes unstable
 *
 * This analyzer:
 * --------------
 * ✅ Detects texture loss without knowing OEM or model
 * ✅ Works purely on pixel variance (device-agnostic)
 * ✅ Allows adaptive behavior instead of blind rejection
 *
 * ---------------------------------------------------------------------------
 * IMPORTANT ARCHITECTURAL RULE:
 * ---------------------------------------------------------------------------
 * TextureQualityAnalyzer DOES NOT:
 *  ❌ Decide acceptance or rejection
 *  ❌ Block attendance
 *  ❌ Know about liveness policies
 *
 * It ONLY provides a **signal**.
 *
 * Decision-making happens elsewhere.
 */
object TextureQualityAnalyzer {

    /**
     * estimate
     *
     * -----------------------------------------------------------------------
     * Computes a **relative texture variance score** from a bitmap.
     *
     * Higher score  -> richer texture (natural skin)
     * Lower score   -> flattened image (beautification / blur / noise reduction)
     *
     * -----------------------------------------------------------------------
     * DESIGN CONSTRAINTS:
     * -----------------------------------------------------------------------
     * - CPU only
     * - Extremely cheap
     * - No allocation inside inner loops
     * - Safe for low-end devices
     *
     * -----------------------------------------------------------------------
     * @param bitmap Face bitmap (already cropped & resized)
     * @return Float score in range ~[0.0 .. 1.0+]
     */
    fun estimate(bitmap: Bitmap): Float {

        val width = bitmap.width
        val height = bitmap.height

        /**
         * We do NOT scan every pixel.
         *
         * WHY?
         * - Full scan is expensive
         * - Micro-texture can be sampled sparsely
         *
         * Sampling every 3-4 pixels is sufficient to detect smoothing.
         */
        val step = 4

        var totalDelta = 0L
        var comparisons = 0

        /**
         * We compare neighboring luminance values.
         *
         * Reason:
         * --------
         * Beautification filters reduce local contrast.
         * This directly reduces per-pixel luminance variation.
         */
        for (y in step until height step step) {
            for (x in step until width step step) {

                val currentPixel = bitmap.getPixel(x, y)
                val neighborPixel = bitmap.getPixel(x - step, y)

                val currentLuma = luma(currentPixel)
                val neighborLuma = luma(neighborPixel)

                totalDelta += abs(currentLuma - neighborLuma)
                comparisons++
            }
        }

        /**
         * Defensive fallback.
         * Should never happen, but NEVER trust assumptions.
         */
        if (comparisons == 0) return 0.0f

        /**
         * Normalize by number of comparisons and luminance scale (0..255).
         *
         * Result:
         * - Stable across resolutions
         * - Comparable across devices
         */
        return (totalDelta.toFloat() / comparisons) / 255f
    }

    /**
     * luma
     *
     * -----------------------------------------------------------------------
     * Computes approximate luminance (grayscale intensity).
     *
     * Using ITU BT.601 coefficients.
     *
     * We intentionally AVOID:
     * - HSV
     * - HSL
     * - Floating-point heavy math
     *
     * Reason:
     * --------
     * Luma contrast is what beautification kills first.
     */
    private fun luma(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF

        // Integer math approximation (fast & sufficient)
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}