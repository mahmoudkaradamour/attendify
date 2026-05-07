package com.mahmoud.attendify.security.privacy

import android.graphics.Bitmap
import com.mahmoud.attendify.util.BitmapSafeUtils

/**
 * =============================================================================
 * 🧠 BiometricPrivacyGuard — Privacy Enforcement for Biometric Data
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 CORE PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * This component enforces:
 *
 *   DATA MINIMIZATION + EPHEMERAL PROCESSING
 *
 * Meaning:
 *
 *   Input  → Use → Dispose → No Residual Data
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE MODEL
 * -----------------------------------------------------------------------------
 *
 *     Face Image
 *         │
 *         ▼
 *   Feature Extraction
 *         │
 *         ▼
 *   Immediate Disposal (Recycle)
 *
 * -----------------------------------------------------------------------------
 * 🔐 PRIVACY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ No raw face image is persisted
 * ✅ Only derived embeddings are used
 * ✅ Temporary data is explicitly destroyed
 *
 * -----------------------------------------------------------------------------
 * ⚠️ BIOMETRIC DATA IS HIGHLY SENSITIVE
 * -----------------------------------------------------------------------------
 *
 * Face images are classified as:
 *
 *   "Special Category Data" (GDPR Art. 9)
 *
 * Therefore:
 *
 * ✅ Must not be stored unless necessary
 * ✅ Must not leave memory scope
 *
 */
object BiometricPrivacyGuard {

    /**
     * =============================================================================
     * 🔥 secureDispose — Explicit Memory Erasure
     * =============================================================================
     *
     * Ensures:
     *   Bitmap memory is released immediately after use.
     */
    fun secureDispose(bitmap: Bitmap?) {

        BitmapSafeUtils.safeRecycle(bitmap)
    }
}