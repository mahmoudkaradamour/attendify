package com.mahmoud.attendify.orchestration

import java.util.UUID
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
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
 * 🧠 PhysicalRealityBuilder — Hardened Reality Binding Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 FORMAL MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   R(t)  = physical world state at time t
 *   C     = canonical encoding function
 *   H     = SHA‑256 hash
 *   S     = hardware-backed signature
 *
 * Then:
 *
 *   Evidence = S(H(C(R(t))))
 *
 * -----------------------------------------------------------------------------
 * ⚠️ CRITICAL CONSTRAINTS IMPLEMENTED
 * -----------------------------------------------------------------------------
 *
 * ✅ Canonical determinism (no floating ambiguity)
 * ✅ Temporal synchronization (≤ 500 ms constraint)
 * ✅ Image binding (hash of pixels)
 * ✅ Hardware-backed authenticity
 *
 * -----------------------------------------------------------------------------
 * 📊 PIPELINE (REALITY → PROOF)
 * -----------------------------------------------------------------------------
 *
 *   Location (fresh + timestamp)
 *        │
 *        ▼
 *   Camera Capture (timestamped)
 *        │
 *        ▼
 *   Temporal Synchronization Check ✅
 *        │
 *        ▼
 *   Canonical Encoding
 *        │
 *        ▼
 *   SHA‑256 Hash
 *        │
 *        ▼
 *   Hardware Signature
 *        │
 *        ▼
 *   Signed Snapshot
 *
 */
class PhysicalRealityBuilder(
    private val cameraManager: CameraManager,
    private val locationIntegrityGuard: LocationIntegrityGuard
) {

    /* =========================================================================
     * 🧮 CANONICAL PRIMITIVES
     * ========================================================================= */

    private fun longToBytes(v: Long) =
        ByteBuffer.allocate(8).putLong(v).array()

    private fun intToBytes(v: Int) =
        ByteBuffer.allocate(4).putInt(v).array()

    private fun floatToBytes(v: Float) =
        ByteBuffer.allocate(4)
            .putInt(java.lang.Float.floatToIntBits(v))
            .array()

    private fun booleanToBytes(v: Boolean) =
        byteArrayOf(if (v) 1 else 0)

    private fun stringToBytes(value: String): ByteArray {
        val raw = value.toByteArray(Charsets.UTF_8)
        return intToBytes(raw.size) + raw
    }

    /* =========================================================================
     * ✅ CRITICAL FIX — FLOAT → INTEGER CANONICALIZATION
     * =========================================================================
     *
     * Floating-point numbers are NOT deterministic across architectures.
     *
     * Therefore:
     *
     *   lat/lon → scaled integers (1e7 precision)
     *
     * This guarantees:
     *   ✅ deterministic encoding
     *   ✅ cross-device consistency
     */
    private fun encodeLatitude(lat: Double?): ByteArray {
        val value = ((lat ?: 0.0) * 1e7).toLong()
        return longToBytes(value)
    }

    private fun encodeLongitude(lon: Double?): ByteArray {
        val value = ((lon ?: 0.0) * 1e7).toLong()
        return longToBytes(value)
    }

    /* =========================================================================
     * ⏱ TIME ENCODING
     * ========================================================================= */

    private fun TimeSnapshot.toCanonicalBytes(): ByteArray {
        return longToBytes(wallClockMillis) +
                longToBytes(elapsedRealtimeMillis) +
                longToBytes(uptimeMillis) +
                stringToBytes(bootId) +
                stringToBytes(timeZoneId)
    }

    /* =========================================================================
     * 📍 LOCATION ENCODING (HARDENED)
     * ========================================================================= */

    private fun LocationEvidence.toCanonicalBytes(): ByteArray {

        return encodeLatitude(latitude) +
                encodeLongitude(longitude) +
                floatToBytes(accuracyMeters ?: Float.NaN) +
                stringToBytes(provider) +
                booleanToBytes(isMockDetected) +
                booleanToBytes(isStale) +
                booleanToBytes(teleportDetected) +
                longToBytes(timestampMillis)
    }

    /* =========================================================================
     * 🖼 IMAGE HASHING
     * ========================================================================= */

    private fun hashBitmap(bitmap: android.graphics.Bitmap): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /* =========================================================================
     * 🚀 MAIN PIPELINE
     * ========================================================================= */

    suspend fun buildSignedOrFail(
        timeoutMs: Long
    ): Result<SignedPhysicalRealitySnapshot> = withContext(Dispatchers.IO) {

        /* ================================================================
         * STEP 1 — LOCATION (ANCHOR)
         * ================================================================ */
        val locationResult =
            locationIntegrityGuard.awaitFreshLocation(timeoutMs)

        if (locationResult !is LocationIntegrityResult.Allowed) {
            return@withContext Result.failure(
                IllegalStateException("Location integrity failed")
            )
        }

        val locationTime = locationResult.evidence.timestampMillis

        /* ================================================================
         * STEP 2 — CAMERA FRAME (REAL-TIME)
         * ================================================================ */
        val captureStart = System.currentTimeMillis()

        val frame = cameraManager.captureSingleFrameSuspend(timeoutMs)
            ?: return@withContext Result.failure(
                IllegalStateException("Camera capture failed")
            )

        val captureEnd = System.currentTimeMillis()

        /**
         * ================================================================
         * ✅ CRITICAL FIX — TEMPORAL SYNCHRONIZATION
         * ================================================================
         *
         * Ensures that:
         *   image timestamp ≈ location timestamp
         *
         * Constraint:
         *   |T_location - T_capture| ≤ 500ms
         *
         * Prevents:
         *   ❌ Cached GPS replay
         *   ❌ Time-of-check/time-of-use mismatch
         */
        val delta = abs(locationTime - captureEnd)

        if (delta > 500) {
            frame.recycle()
            return@withContext Result.failure(
                IllegalStateException("Sensor desynchronization detected")
            )
        }

        try {

            /* ============================================================
             * STEP 3 — TIME SNAPSHOT
             * ============================================================ */
            val timeSnapshot = TimeSource.snapshot()

            val snapshot = PhysicalRealitySnapshot(
                frozenFrame = frame,
                timeSnapshot = timeSnapshot,
                locationEvidence = locationResult.evidence
            )

            val snapshotId = UUID.randomUUID()
            val createdAt = System.currentTimeMillis()

            /* ============================================================
             * STEP 4 — CANONICAL PAYLOAD
             * ============================================================ */

            val imageHash = hashBitmap(frame)
            val timeBytes = timeSnapshot.toCanonicalBytes()
            val locationBytes = locationResult.evidence.toCanonicalBytes()

            val payload =
                intToBytes(imageHash.size) + imageHash +
                        intToBytes(timeBytes.size) + timeBytes +
                        intToBytes(locationBytes.size) + locationBytes

            /* ============================================================
             * STEP 5 — HASH
             * ============================================================ */
            val snapshotHash =
                MessageDigest.getInstance("SHA-256").digest(payload)

            /* ============================================================
             * STEP 6 — SIGNATURE (HARDWARE)
             * ============================================================ */
            val signature =
                HardwareBackedSnapshotSigner.sign(snapshotHash)

            val certificateChain =
                HardwareBackedKeyManager.getCertificateChain()
                    .map { it.encoded }

            /* ============================================================
             * STEP 7 — FINAL OBJECT
             * ============================================================ */
            return@withContext Result.success(
                SignedPhysicalRealitySnapshot(
                    snapshotId = snapshotId,
                    timestampMillis = createdAt,
                    payload = snapshot,
                    signature = signature,
                    certificateChain = certificateChain,
                    snapshotHash = snapshotHash
                )
            )

        } catch (t: Throwable) {

            frame.recycle()
            return@withContext Result.failure(t)
        }
    }
}