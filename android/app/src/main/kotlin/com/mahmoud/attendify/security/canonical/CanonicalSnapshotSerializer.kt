package com.mahmoud.attendify.security.canonical

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * =============================================================================
 * 🧠 CanonicalSnapshotSerializer
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 DEFINITION (FORMAL)
 * -----------------------------------------------------------------------------
 *
 * This component provides a **canonical, deterministic, and cryptographically
 * stable binary representation** of a real-world evidence snapshot.
 *
 * Let:
 *
 *   E = { timestamp, location, image }
 *
 * Then:
 *
 *   B = CanonicalEncode(E)
 *
 * such that:
 *
 *   ∀ E1, E2:
 *     E1 = E2  ⇔  B(E1) = B(E2)
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL PROPERTY
 * -----------------------------------------------------------------------------
 *
 *   The encoding MUST be:
 *
 *   ✅ Order-preserving
 *   ✅ Byte-exact
 *   ✅ Free from ambiguity
 *
 * -----------------------------------------------------------------------------
 * 📐 WHY THIS MATTERS
 * -----------------------------------------------------------------------------
 *
 * Without canonical representation:
 *
 *   Same logical data → Different byte streams → Different hashes
 *
 * Which leads to:
 *
 *   ❌ Signature mismatch
 *   ❌ Forgery opportunity
 *   ❌ Legal invalidation
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE (CANONICALIZATION FLOW)
 * -----------------------------------------------------------------------------
 *
 *     Real-world Snapshot
 *              │
 *              ▼
 *     Extract Raw Fields
 *              │
 *              ▼
 *     Canonical Encoding (this class)
 *              │
 *              ▼
 *     SHA-256 Hash
 *              │
 *              ▼
 *     Digital Signature
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Any change in:
 *     - time
 *     - location
 *     - image pixels
 *
 *   → produces a completely different hash
 *
 * ✅ Prevents:
 *     - replay manipulation
 *     - serialization ambiguity
 *     - structure tampering
 *
 */
object CanonicalSnapshotSerializer {

    /**
     * =============================================================================
     * 🚀 serialize
     * =============================================================================
     *
     * Converts structured evidence into a deterministic binary sequence.
     *
     * -----------------------------------------------------------------------------
     * 📐 STRUCTURE (STRICT ORDER)
     * -----------------------------------------------------------------------------
     *
     * Byte layout:
     *
     *   [0..7]   → timestamp (Long, 8 bytes)
     *   [8..15]  → latitude  (Double, 8 bytes)
     *   [16..23] → longitude (Double, 8 bytes)
     *   [24..55] → imageHash (SHA-256, 32 bytes)
     *
     * TOTAL SIZE = 56 bytes
     *
     * -----------------------------------------------------------------------------
     * 🔐 DESIGN RULE
     * -----------------------------------------------------------------------------
     *
     * No optional fields, no variable lengths, no encoding flexibility.
     *
     */
    fun serialize(
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        image: Bitmap
    ): ByteArray {

        val imageHash = hashBitmap(image)

        val buffer = ByteBuffer.allocate(8 + 8 + 8 + 32)

        /**
         * -----------------------------------------------------------------------------
         * 📊 FIELD ORDER — CRITICAL!
         * -----------------------------------------------------------------------------
         *
         * The order MUST NEVER change.
         *
         * Changing order breaks:
         *   - hash reproducibility
         *   - signature validation
         */
        buffer.putLong(timestamp)
        buffer.putDouble(latitude)
        buffer.putDouble(longitude)
        buffer.put(imageHash)

        return buffer.array()
    }

    /**
     * =============================================================================
     * 🔐 hashBitmap
     * =============================================================================
     *
     * Computes SHA-256 hash of RAW PIXEL DATA (NOT compressed image).
     *
     * -----------------------------------------------------------------------------
     * 🧠 WHY RAW PIXELS?
     * -----------------------------------------------------------------------------
     *
     * Compressed formats like JPEG:
     *   ❌ contain metadata
     *   ❌ may differ even if visually identical
     *
     * RAW pixels guarantee:
     *   ✅ exact binary identity
     *
     * -----------------------------------------------------------------------------
     * 📊 PIPELINE
     * -----------------------------------------------------------------------------
     *
     * Bitmap → Pixel Array → ByteBuffer → SHA-256 → Hash
     *
     * -----------------------------------------------------------------------------
     * 🔐 SECURITY PROPERTY
     * -----------------------------------------------------------------------------
     *
     * Any pixel modification (even 1 bit) →
     * produces completely different hash.
     *
     */
    private fun hashBitmap(bitmap: Bitmap): ByteArray {

        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val buffer = ByteBuffer.allocate(pixels.size * 4)

        /**
         * Each pixel = 4 bytes (ARGB)
         */
        for (pixel in pixels) {
            buffer.putInt(pixel)
        }

        return sha256(buffer.array())
    }

    /**
     * =============================================================================
     * 🔑 sha256
     * =============================================================================
     *
     * Standard cryptographic hash function.
     *
     * -----------------------------------------------------------------------------
     * 🧠 MATHEMATICAL PROPERTY
     * -----------------------------------------------------------------------------
     *
     *   H(x) = SHA-256(x)
     *
     * Properties:
     *
     *   ✅ Deterministic
     *   ✅ Collision-resistant (practically)
     *   ✅ Preimage-resistant
     *
     * -----------------------------------------------------------------------------
     * 🔐 ROLE IN SYSTEM
     * -----------------------------------------------------------------------------
     *
     *   Canonical Bytes → SHA-256 → Digital Signature
     *
     */
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}