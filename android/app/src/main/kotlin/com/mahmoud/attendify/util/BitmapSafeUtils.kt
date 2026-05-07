package com.mahmoud.attendify.util

import android.graphics.Bitmap

/**
 * =============================================================================
 * 🛡 BitmapSafeUtils — Memory Safety Utilities
 * =============================================================================
 *
 * Centralized safe operations for Bitmaps
 */
object BitmapSafeUtils {

    /**
     * ✅ Safe recycle (no crash if already recycled)
     */
    fun safeRecycle(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * ✅ Safe copy (defensive cloning)
     */
    fun safeCopy(bitmap: Bitmap?): Bitmap? {
        return try {
            bitmap?.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        }
    }
}