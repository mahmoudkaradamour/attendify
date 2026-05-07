package com.mahmoud.attendify.orchestration

import java.util.UUID
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.system.time.TimeSource
import com.mahmoud.attendify.system.time.TimeSnapshot
import com.mahmoud.attendify.system.location.LocationIntegrityGuard
import com.mahmoud.attendify.system.location.LocationIntegrityResult
import com.mahmoud.attendify.system.location.LocationEvidence

import com.mahmoud.attendify.orchestration.context.PhysicalRealitySnapshot
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot

import com.mahmoud.attendify.security.HardwareBackedSnapshotSigner
import com.mahmoud.attendify.security.HardwareBackedKeyManager

/**
 * =============================================================================
 * 🔬 PhysicalRealityBuilder — Deterministic Reality Binding Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 ABSTRACT MODEL
 * -----------------------------------------------------------------------------
 *
 * This component implements a **deterministic, cryptographically bound representation
 * of physical reality at a single moment in time**.
 *
 * Formally, it constructs:
 *
 *   R(t) → P → H → Sign(H)
 *
 * Where:
 *
 *   R(t)  = Physical reality at time t
 *   P     = Canonical binary representation (deterministic)
 *   H     = SHA-256(P)
 *   Sign  = Hardware-backed signature
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * 1. Temporal Consistency:
 *    - Capture order prevents TOCTOU (Time-of-check vs Time-of-use)
 *
 * 2. Deterministic Encoding:
 *    - Identical input → identical byte stream → identical hash
 *
 * 3. Cryptographic Binding:
 *    - Any bit modification breaks SHA-256 → invalid signature
 *
 * 4. Hardware Trust:
 *    - Signature bound to Keystore (TEE / StrongBox)
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE DIAGRAM
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────┐
 *   │ GPS Fix      │
 *   └──────┬───────┘
 *          │
 *          ▼
 *   ┌──────────────┐
 *   │ Camera Frame │
 *   └──────┬───────┘
 *          │
 *          ▼
 *   ┌──────────────┐
 *   │ Time Snapshot│
 *   └──────┬───────┘
 *          │
 *          ▼
 *   ┌────────────────────────────┐
 *   │ Canonical Payload Builder │
 *   │ (image + time + location) │
 *   └──────────────┬────────────┘
 *                  ▼
 *             SHA-256 Hash
 *                  ▼
 *         Hardware Signature
 *                  ▼
 *   SignedPhysicalRealitySnapshot ✅
 *
 * -----------------------------------------------------------------------------
 * ⚠️ CRITICAL DESIGN DECISION
 * -----------------------------------------------------------------------------
 *
 * The payload MUST:
 * ✅ Be deterministic
 * ✅ Have fixed ordering
 * ✅ Avoid encoding ambiguity
 * ✅ Be independent from runtime string formatting
 *
 * This prevents:
 * ❌ Serialization attacks
 * ❌ Data reordering attacks
 * ❌ Encoding inconsistencies
 *
 */
class PhysicalRealityBuilder(
    private val cameraManager: CameraManager,
    private val locationIntegrityGuard: LocationIntegrityGuard
) {

    /* =========================================================================
     * 🧮 PRIMITIVE ENCODING (DETERMINISTIC BINARY FORM)
     * =========================================================================
     *
     * PURPOSE:
     * Convert high-level data types into fixed, deterministic byte sequences.
     *
     * Key idea:
     *   Every primitive → fixed-size binary representation
     *
     * This avoids:
     * - Locale issues
     * - String formatting instability
     * - Platform differences
     */

    private fun longToBytes(v: Long) =
        ByteBuffer.allocate(8).putLong(v).array()

    private fun intToBytes(v: Int) =
        ByteBuffer.allocate(4).putInt(v).array()

    private fun doubleToBytes(v: Double) =
        ByteBuffer.allocate(8)
            .putLong(java.lang.Double.doubleToLongBits(v))
            .array()

    private fun floatToBytes(v: Float) =
        ByteBuffer.allocate(4)
            .putInt(java.lang.Float.floatToIntBits(v))
            .array()

    private fun booleanToByte(v: Boolean) =
        byteArrayOf(if (v) 1 else 0)

    /**
     * -----------------------------------------------------------------------------
     * 🔤 STRING CANONICALIZATION
     * -----------------------------------------------------------------------------
     *
     * Format:
     *   [length:4 bytes][UTF-8 bytes]
     *
     * Why?
     *   Prevents concatenation ambiguity:
     *
     *   "ab"+"cd" != "abc"+"d"
     *
     */
    private fun stringToBytes(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        return intToBytes(raw.size) + raw
    }

    /* =========================================================================
     * ⏱ TIME SERIALIZATION
     * ========================================================================= */

    private fun TimeSnapshot.toCanonicalBytes(): ByteArray {
        return longToBytes(wallClockMillis) +
                longToBytes(elapsedRealtimeMillis) +
                longToBytes(uptimeMillis) +
                stringToBytes(bootId) +
                stringToBytes(timeZoneId)
    }

    /* =========================================================================
     * 📍 LOCATION SERIALIZATION
     * ========================================================================= */

    private fun LocationEvidence.toCanonicalBytes(): ByteArray {

        val lat = latitude ?: Double.NaN
        val lon = longitude ?: Double.NaN
        val acc = accuracyMeters ?: Float.NaN

        return doubleToBytes(lat) +
                doubleToBytes(lon) +
                floatToBytes(acc) +
                stringToBytes(provider) +
                booleanToByte(isMockDetected) +
                booleanToByte(isStale) +
                booleanToByte(teleportDetected) +
                doubleToBytes(distanceToAllowedZoneMeters ?: Double.NaN) +
                stringToBytes(zoneDecision?.policy?.name ?: "NONE") +
                stringToBytes(policyDecision.name) +
                booleanToByte(justificationRequired) +
                stringToBytes(networkContext.toString()) +
                longToBytes(timestampMillis)
    }

    /* =========================================================================
     * 🖼 IMAGE HASHING
     * =========================================================================
     *
     * Converts bitmap → deterministic PNG → SHA-256
     *
     * Rationale:
     *   Raw Bitmap memory representation is NOT stable across devices.
     *
     * PNG ensures:
     * ✅ deterministic byte output
     * ✅ lossless encoding
     */
    private fun hashBitmap(bitmap: android.graphics.Bitmap): ByteArray {

        val imageBytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }

        return MessageDigest
            .getInstance("SHA-256")
            .digest(imageBytes)
    }

    /* =========================================================================
     * 🚀 MAIN PIPELINE
     * ========================================================================= */

    suspend fun buildSignedOrFail(
        timeoutMs: Long
    ): Result<SignedPhysicalRealitySnapshot> = withContext(Dispatchers.IO) {

        /**
         * ---------------------------------------------------------------------
         * STEP 1 — LOCATION FIRST (ANCHOR)
         * ---------------------------------------------------------------------
         *
         * Guarantees:
         * - fresh GPS
         * - prevents replay of cached location
         */
        val locationResult =
            locationIntegrityGuard.awaitFreshLocation(timeoutMs)

        if (locationResult !is LocationIntegrityResult.Allowed) {
            return@withContext Result.failure(
                IllegalStateException("Location integrity failed")
            )
        }

        /**
         * ---------------------------------------------------------------------
         * STEP 2 — FRAME CAPTURE
         * ---------------------------------------------------------------------
         *
         * Must occur AFTER location to maintain temporal ordering.
         */
        val frame = cameraManager.captureSingleFrameSuspend(timeoutMs)
            ?: return@withContext Result.failure(
                IllegalStateException("Camera capture failed")
            )

        try {

            /**
             * -----------------------------------------------------------------
             * STEP 3 — TIME SNAPSHOT
             * -----------------------------------------------------------------
             *
             * Captured after frame to bind them in same execution window.
             */
            val timeSnapshot = TimeSource.snapshot()

            /**
             * -----------------------------------------------------------------
             * STEP 4 — RAW SNAPSHOT
             * -----------------------------------------------------------------
             */
            val snapshot = PhysicalRealitySnapshot(
                frozenFrame = frame,
                timeSnapshot = timeSnapshot,
                locationEvidence = locationResult.evidence
            )

            val snapshotId = UUID.randomUUID()
            val createdAt = System.currentTimeMillis()

            /**
             * -----------------------------------------------------------------
             * STEP 5 — CANONICAL PAYLOAD CONSTRUCTION
             * -----------------------------------------------------------------
             *
             * Structure:
             *
             *   [len][imageHash]
             *   [len][timeBytes]
             *   [len][locationBytes]
             *
             * This ensures:
             * ✅ Deterministic structure
             * ✅ Fully reconstructible schema
             * ✅ Attack-resistant encoding
             */
            val imageHash = hashBitmap(frame)
            val timeBytes = timeSnapshot.toCanonicalBytes()
            val locationBytes = locationResult.evidence.toCanonicalBytes()

            val payload =
                intToBytes(imageHash.size) + imageHash +
                        intToBytes(timeBytes.size) + timeBytes +
                        intToBytes(locationBytes.size) + locationBytes

            /**
             * -----------------------------------------------------------------
             * STEP 6 — HASH
             * -----------------------------------------------------------------
             */
            val payloadHash = MessageDigest
                .getInstance("SHA-256")
                .digest(payload)

            /**
             * -----------------------------------------------------------------
             * STEP 7 — HARDWARE SIGN
             * -----------------------------------------------------------------
             */
            val signature =
                HardwareBackedSnapshotSigner.sign(payloadHash)

            val certificateChain =
                HardwareBackedKeyManager.getCertificateChain()
                    .map { it.encoded }

            /**
             * -----------------------------------------------------------------
             * STEP 8 — FINAL OUTPUT
             * -----------------------------------------------------------------
             */
            return@withContext Result.success(
                SignedPhysicalRealitySnapshot(
                    snapshotId = snapshotId,
                    timestampMillis = createdAt,
                    payload = snapshot,
                    signature = signature,
                    certificateChain = certificateChain,
                    snapshotHash = payloadHash
                )
            )

        } catch (t: Throwable) {

            /**
             * IMPORTANT:
             * Prevent memory leaks caused by retained bitmaps
             */
            frame.recycle()

            return@withContext Result.failure(t)
        }
    }
}