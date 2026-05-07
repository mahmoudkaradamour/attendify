package com.mahmoud.attendify.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.math.min
import com.mahmoud.attendify.util.BitmapSafeUtils

/**
 * =============================================================================
 * 🧠 FaceCropper — Deterministic & Memory-Safe Spatial Normalization Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ABSTRACT MODEL (FORMAL DEFINITION)
 * -----------------------------------------------------------------------------
 *
 * This component performs a transformation:
 *
 *     F(x, y) → R(x, y) → T(n × n)
 *
 * Where:
 *
 *   F(x, y) = original image (pixel space)
 *   R(x, y) = region of interest (cropped + expanded)
 *   T(n, n) = normalized tensor fed into ML model
 *
 * -----------------------------------------------------------------------------
 * 📊 FULL PIPELINE
 * -----------------------------------------------------------------------------
 *
 *        ┌──────────────────────────────┐
 *        │ Raw Camera Frame (F)         │
 *        └──────────────┬───────────────┘
 *                       ▼
 *        ┌──────────────────────────────┐
 *        │ Face Bounding Box (Detector)│
 *        └──────────────┬───────────────┘
 *                       ▼
 *        ┌──────────────────────────────┐
 *        │ Expansion Layer (Security)   │
 *        └──────────────┬───────────────┘
 *                       ▼
 *        ┌──────────────────────────────┐
 *        │ Boundary Clamping            │
 *        └──────────────┬───────────────┘
 *                       ▼
 *        ┌──────────────────────────────┐
 *        │ Cropping Operation           │
 *        └──────────────┬───────────────┘
 *                       ▼
 *        ┌──────────────────────────────┐
 *        │ Resize → Fixed Tensor (T)    │
 *        └──────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🔬 SCIENTIFIC FOUNDATION
 * -----------------------------------------------------------------------------
 *
 * WHY EXPANSION?
 *
 * Tight face crops (face-only region) REMOVE:
 *   ❌ background illumination gradients
 *   ❌ edge artifacts
 *   ❌ contextual signals
 *
 * Expanded crops RETAIN:
 *   ✅ photometric variations
 *   ✅ structural edges
 *   ✅ environment context
 *
 * → These signals are critical for:
 *    - Liveness Detection
 *    - Anti-Spoofing (FAS)
 *    - Robust Embedding Stability
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * ✅ Prevents adversarial tight-cropping attacks
 * ✅ Preserves context for spoof detection
 * ✅ Eliminates out-of-bounds memory access
 * ✅ Enforces deterministic geometry
 *
 * -----------------------------------------------------------------------------
 * ⚙️ MEMORY SAFETY MODEL
 * -----------------------------------------------------------------------------
 *
 * Bitmap lifecycle:
 *
 *   sourceBitmap → (NO TOUCH)
 *         │
 *         ▼
 *      cropped (ALLOC)
 *         │
 *         ▼
 *      resized (FINAL)
 *         │
 *         ▼
 *   cropped MUST be recycled ✅
 *
 * -----------------------------------------------------------------------------
 */
object FaceCropper {

    private const val TAG = "FaceCropper"

    /**
     * =============================================================================
     * 🎯 cropAndResize — Deterministic + Safe Processing
     * =============================================================================
     *
     * Core invariant:
     *
     *   Output image ALWAYS:
     *   - square
     *   - fixed size
     *   - derived from spatially expanded region
     *
     */
    fun cropAndResize(
        sourceBitmap: Bitmap,
        faceBox: Rect,
        targetSize: Int,
        expansionFactor: Float = 2.5f
    ): Bitmap? {

        /* =======================================================================
         * 🧪 STEP 1 — INPUT VALIDATION
         * ======================================================================= */
        if (targetSize <= 0) {
            Log.e(TAG, "Invalid target size: $targetSize")
            return null
        }

        val frameWidth = sourceBitmap.width
        val frameHeight = sourceBitmap.height

        if (frameWidth <= 0 || frameHeight <= 0) {
            Log.e(TAG, "Invalid bitmap dimensions")
            return null
        }

        if (faceBox.width() <= 0 || faceBox.height() <= 0) {
            Log.e(TAG, "Invalid face bounding box")
            return null
        }

        /* =======================================================================
         * 📏 STEP 2 — EXPANSION (CRITICAL FOR SECURITY)
         * =======================================================================
         *
         * Mathematical model:
         *
         *   expansion = (size * (factor - 1)) / 2
         *
         * This ensures symmetric growth around center.
         */
        val boxWidth = faceBox.width()
        val boxHeight = faceBox.height()

        val expandW = ((boxWidth * (expansionFactor - 1f)) / 2f).toInt()
        val expandH = ((boxHeight * (expansionFactor - 1f)) / 2f).toInt()

        val left = max(0, faceBox.left - expandW)
        val top = max(0, faceBox.top - expandH)
        val right = min(frameWidth, faceBox.right + expandW)
        val bottom = min(frameHeight, faceBox.bottom + expandH)

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(TAG, "Invalid crop region after expansion")
            return null
        }

        /* =======================================================================
         * ✂️ STEP 3 — SAFE CROPPING (ALLOCATION POINT)
         * ======================================================================= */
        val cropped = try {
            Bitmap.createBitmap(
                sourceBitmap,
                left,
                top,
                cropWidth,
                cropHeight
            )
        } catch (e: Exception) {
            Log.e(TAG, "Crop failed", e)
            return null
        }

        /* =======================================================================
         * 🔄 STEP 4 — RESIZE (NORMALIZATION)
         * =======================================================================
         *
         * Resize is mandatory because:
         *
         *   ML models require fixed-size tensors
         *
         * We use bilinear filtering (filter = true)
         * to preserve spatial smoothness.
         */
        val resized = try {
            cropped.scale(
                width = targetSize,
                height = targetSize,
                filter = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Resize failed", e)
            BitmapSafeUtils.safeRecycle(cropped)
            return null
        }

        /* =======================================================================
         * 🧹 STEP 5 — MEMORY CLEANUP
         * =======================================================================
         *
         * IMPORTANT:
         *
         * cropped is intermediate buffer and MUST be released.
         * Failure to do so → native memory leak → OOM crash.
         */
        BitmapSafeUtils.safeRecycle(cropped)

        /* =======================================================================
         * ✅ FINAL OUTPUT
         * ======================================================================= */
        return resized
    }
}