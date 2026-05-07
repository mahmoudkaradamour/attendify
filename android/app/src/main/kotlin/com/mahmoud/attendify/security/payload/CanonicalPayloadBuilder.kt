package com.mahmoud.attendify.security.payload

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * =============================================================================
 * 🛡 CanonicalPayloadBuilder — Deterministic Evidence Encoding
 * =============================================================================
 *
 * ✅ Guarantees:
 * - Same input → same bytes ALWAYS
 * - No ordering ambiguity
 * - No encoding ambiguity
 *
 */
object CanonicalPayloadBuilder {

    /**
     * ✅ Build canonical payload bytes
     */
    fun build(
        image: Bitmap,
        timestamp: Long,
        latitude: Double,
        longitude: Double
    ): ByteArray {

        val imageHash = hashBitmap(image)

        val buffer = ByteBuffer.allocate(
            32 + 8 + 8 + 8
        )

        buffer.put(imageHash)         // 32 bytes
        buffer.putLong(timestamp)     // 8 bytes
        buffer.putDouble(latitude)    // 8 bytes
        buffer.putDouble(longitude)   // 8 bytes

        return buffer.array()
    }

    /**
     * ✅ Stable bitmap hashing
     */
    private fun hashBitmap(bitmap: Bitmap): ByteArray {

        val stream = ByteArrayOutputStream()

        // ✅ IMPORTANT: force deterministic format
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)

        val bytes = stream.toByteArray()

        val digest = MessageDigest.getInstance("SHA-256")

        return digest.digest(bytes)
    }

    /**
     * ✅ Final payload hash
     */
    fun hash(payload: ByteArray): ByteArray {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(payload)
    }
}