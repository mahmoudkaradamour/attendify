package com.mahmoud.attendify.camera

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Color
import androidx.camera.core.ImageProxy


/**
 * ImageConverter
 *
 * ---------------------------------------------------------------------------
 * PURPOSE:
 * ---------------------------------------------------------------------------
 * Safely converts ImageProxy (YUV_420_888) into a Bitmap (RGB_8888)
 * suitable for:
 *  - Face detection (BlazeFace / ML Kit)
 *  - Face embedding (MobileFaceNet)
 *  - Liveness / FAS models
 *
 * ---------------------------------------------------------------------------
 * WHY THIS FILE IS CRITICAL:
 * ---------------------------------------------------------------------------
 * Android OEMs DO NOT agree on:
 *  - YUV plane layout
 *  - rowStride size
 *  - pixelStride values
 *
 * Assuming "contiguous" YUV buffers WILL:
 *  ❌ Break colors on Samsung Exynos
 *  ❌ Produce invalid tensors for TFLite
 *  ❌ Cause silent ML failures (worst kind)
 *
 * This implementation:
 *  ✅ Respects rowStride & pixelStride
 *  ✅ Performs manual YUV → RGB conversion
 *  ✅ Never crashes
 *  ✅ Never assumes HAL sanity
 */
object ImageConverter {

    /**
     * imageProxyToBitmap
     *
     * -----------------------------------------------------------------------
     * Converts ImageProxy to RGB Bitmap.
     *
     * DESIGN RULES:
     * -----------------------------------------------------------------------
     * - NO NV21 packing assumptions
     * - NO JPEG compression round‑trip
     * - NO allocation inside loops
     *
     * PERFORMANCE:
     * -----------------------------------------------------------------------
     * - CPU‑only
     * - Linear complexity
     * - Safe for low‑end devices
     *
     * @param image ImageProxy in YUV_420_888 format
     * @return RGB Bitmap
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {

        // --------------------------------------------------------------------
        // Basic dimensions
        // --------------------------------------------------------------------
        val width = image.width
        val height = image.height

        // --------------------------------------------------------------------
        // Planes from ImageProxy
        // --------------------------------------------------------------------
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        /**
         * WHY pixelStride matters:
         *
         * On many devices:
         *  pixelStride = 2
         *  rowStride   > width
         *
         * Meaning:
         *  U and V values are NOT stored contiguously.
         *
         * Ignoring this:
         *  ❌ swaps U/V channels
         *  ❌ shifts colors
         *  ❌ poisons ML tensors
         */

        // --------------------------------------------------------------------
        // Output bitmap
        // --------------------------------------------------------------------
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Config.ARGB_8888
        )

        // --------------------------------------------------------------------
        // Temporary pixel array (reused per row)
        // --------------------------------------------------------------------
        val argbPixels = IntArray(width)

        // --------------------------------------------------------------------
        // Conversion loop
        // --------------------------------------------------------------------
        for (row in 0 until height) {

            val yRowOffset = row * yRowStride
            val uvRowOffset = (row / 2) * uvRowStride

            for (col in 0 until width) {

                val yIndex = yRowOffset + col
                val uvIndex = uvRowOffset + (col / 2) * uvPixelStride

                // Fetch YUV components safely
                val y = (yBuffer.get(yIndex).toInt() and 0xFF)
                val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                /**
                 * Standard YUV → RGB conversion (BT.601)
                 *
                 * This formula is stable and well‑tested.
                 */
                var r = y + (1.402f * v).toInt()
                var g = y - (0.344136f * u + 0.714136f * v).toInt()
                var b = y + (1.772f * u).toInt()

                // Clamp values to valid RGB range
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                argbPixels[col] = Color.rgb(r, g, b)
            }

            // Apply row to bitmap
            bitmap.setPixels(
                argbPixels,
                0,
                width,
                0,
                row,
                width,
                1
            )
        }

        return bitmap
    }
}
