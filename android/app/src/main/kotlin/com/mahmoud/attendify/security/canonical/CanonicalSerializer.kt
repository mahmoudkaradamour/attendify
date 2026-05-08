package com.mahmoud.attendify.security.canonical

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * =============================================================================
 * 🧠 CanonicalSerializer — Deterministic Binary Encoding System
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 ABSTRACT (FORMAL VIEW)
 * -----------------------------------------------------------------------------
 *
 * This component defines a canonical (unique and deterministic) mapping:
 *
 *     f : Input Domain → Byte Sequence
 *
 * such that:
 *
 *     ∀ x ∈ Domain:
 *         f(x) is stable, reproducible, and identical across all platforms
 *
 * -----------------------------------------------------------------------------
 * 🎯 CORE OBJECTIVE
 * -----------------------------------------------------------------------------
 *
 * Eliminate ALL ambiguity in data representation before hashing/signing.
 *
 * -----------------------------------------------------------------------------
 * ❗ WHY THIS EXISTS
 * -----------------------------------------------------------------------------
 *
 * Traditional serialization methods introduce ambiguity:
 *
 *   1. Floating point precision differences
 *   2. Locale-dependent string formatting
 *   3. JVM-dependent object stringification (toString())
 *   4. Platform endianness inconsistencies
 *
 * Result:
 *
 *   Same logical data → Different byte representation ❌
 *   Different hash → Verification failure ❌
 *
 * -----------------------------------------------------------------------------
 * ✅ SOLUTION MODEL
 * -----------------------------------------------------------------------------
 *
 * This serializer enforces:
 *
 *   • Fixed-width encoding (no ambiguity)
 *   • Network byte order (Big Endian)
 *   • Explicit length-prefixing
 *   • Floating-point normalization via scaling
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * Guarantees:
 *
 *   ✅ Determinism:
 *       Same input → identical bytes across devices
 *
 *   ✅ Hash Stability:
 *       hash(f(x)) is invariant
 *
 *   ✅ Replay Integrity:
 *       No alternate encoding yields same semantic value
 *
 *   ✅ Injection Resistance:
 *       Prevents ambiguity-based attacks
 *
 * -----------------------------------------------------------------------------
 * 🚫 CRITICAL RULE (NON-NEGOTIABLE)
 * -----------------------------------------------------------------------------
 *
 *   ⚠️ NEVER:
 *     - Use toString() for hashing
 *     - Serialize primitives outside this class
 *     - Encode floating numbers directly
 *
 * -----------------------------------------------------------------------------
 * 📊 DATA PIPELINE POSITION
 * -----------------------------------------------------------------------------
 *
 *     Sensors → CanonicalSerializer → Hash → Signature → Verification
 *
 * -----------------------------------------------------------------------------
 * 🧩 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * "Representation must be unique before it can be trusted."
 *
 * =============================================================================
 */
object CanonicalSerializer {

    /* =========================================================================
     * 🔢 NUMERIC ENCODING (FIXED WIDTH, BIG-ENDIAN)
     * =========================================================================
     */

    /**
     * -------------------------------------------------------------------------
     * Long → 8 bytes (64-bit, Big Endian)
     * -------------------------------------------------------------------------
     *
     * Bit layout:
     *
     *   [ MSB ............................................... LSB ]
     *
     * Properties:
     *   - Fixed-width → deterministic
     *   - No compression → stable hashing
     */
    fun longToBytes(v: Long): ByteArray =
        ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(v)
            .array()

    /**
     * -------------------------------------------------------------------------
     * Int → 4 bytes (32-bit, Big Endian)
     * -------------------------------------------------------------------------
     */
    fun intToBytes(v: Int): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(v)
            .array()

    /**
     * -------------------------------------------------------------------------
     * Float → IEEE 754 normalized encoding
     * -------------------------------------------------------------------------
     *
     * Problem:
     *   Float representation may vary in string form
     *
     * Solution:
     *   Convert to raw IEEE 754 bits → treat as integer
     *
     * Guarantees:
     *   Bit-level determinism
     */
    fun floatToBytes(v: Float): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(java.lang.Float.floatToIntBits(v))
            .array()

    /* =========================================================================
     * 🔘 BOOLEAN ENCODING
     * =========================================================================
     */

    /**
     * Boolean → 1 byte
     *
     * Mapping:
     *   true  → 0x01
     *   false → 0x00
     *
     * Minimal representation without ambiguity.
     */
    fun booleanToBytes(v: Boolean): ByteArray =
        byteArrayOf(if (v) 1 else 0)

    /* =========================================================================
     * 🔤 STRING ENCODING (LENGTH-PREFIXED UTF-8)
     * =========================================================================
     */

    /**
     * Encoding Strategy:
     *
     *   [length (4 bytes)] + [UTF-8 bytes]
     *
     * Example:
     *
     *   "abc" →
     *     [0x00 0x00 0x00 0x03] + [61 62 63]
     *
     * Guarantees:
     *   - No delimiter ambiguity
     *   - Supports binary-safe concatenation
     */
    fun stringToBytes(value: String?): ByteArray {
        val safe = value ?: ""
        val raw = safe.toByteArray(Charsets.UTF_8)
        return intToBytes(raw.size) + raw
    }

    /* =========================================================================
     * 🌍 GEO ENCODING (FLOAT NORMALIZATION VIA SCALING)
     * =========================================================================
     */

    /**
     * -------------------------------------------------------------------------
     * Latitude Encoding
     * -------------------------------------------------------------------------
     *
     * Problem:
     *   Double precision values are NOT stable across platforms.
     *
     * Solution:
     *
     *   lat_scaled = lat * 10^7
     *   → stored as Long
     *
     * Why 1e7?
     *   ~1 cm precision (sufficient for geolocation integrity)
     *
     * Flow:
     *
     *   Double → Scaled Integer → Long → Bytes
     *
     * -----------------------------------------------------------------------------
     * 📊 Visualization
     * -----------------------------------------------------------------------------
     *
     *   32.1234567
     *       ↓ *1e7
     *   321234567
     *       ↓ toLong
     *   [8 byte representation]
     */
    fun encodeLatitude(lat: Double?): ByteArray {
        val scaled = ((lat ?: 0.0) * 1e7).toLong()
        return longToBytes(scaled)
    }

    /**
     * Same mechanism applied to longitude.
     */
    fun encodeLongitude(lon: Double?): ByteArray {
        val scaled = ((lon ?: 0.0) * 1e7).toLong()
        return longToBytes(scaled)
    }

    /* =========================================================================
     * 🧪 OPTIONAL UTILITY (DEBUGGING ONLY)
     * =========================================================================
     */

    /**
     * Converts byte array → hex string (for debugging only).
     *
     * ⚠️ MUST NOT be used in hashing pipeline
     */
    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}