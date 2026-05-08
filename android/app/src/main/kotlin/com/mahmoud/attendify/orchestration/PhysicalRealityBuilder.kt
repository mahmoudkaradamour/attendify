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
 * 🧠 PhysicalRealityBuilder — Cryptographic Reality Encoder
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 CORE IDEA
 * -----------------------------------------------------------------------------
 *
 * This class transforms real-world signals into an immutable cryptographic proof:
 *
 *   Reality → CanonicalBytes → Hash → Signature
 *
 * -----------------------------------------------------------------------------
 * 📊 DATAFLOW
 * -----------------------------------------------------------------------------
 *
 * Location (fresh)
 *    ↓
 * Frame Capture
 *    ↓
 * Time Snapshot
 *    ↓
 * Canonical Encoding
 *    ↓
 * SHA‑256 Hash
 *    ↓
 * Hardware Signature
 *    ↓
 * Signed Snapshot ✅
 *
 * -----------------------------------------------------------------------------
 * 🔐 KEY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 * ✅ Deterministic encoding
 * ✅ Full data binding (image + time + location)
 * ✅ Tamper-evident output
 * ✅ Hardware-backed authenticity
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

    private fun doubleToBytes(v: Double) =
        ByteBuffer.allocate(8)
            .putLong(java.lang.Double.doubleToLongBits(v))
            .array()

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
     * ⏱ TIME
     * ========================================================================= */

    private fun TimeSnapshot.toCanonicalBytes(): ByteArray {
        return longToBytes(wallClockMillis) +
                longToBytes(elapsedRealtimeMillis) +
                longToBytes(uptimeMillis) +
                stringToBytes(bootId) +
                stringToBytes(timeZoneId)
    }

    /* =========================================================================
     * 📍 LOCATION
     * ========================================================================= */

    private fun LocationEvidence.toCanonicalBytes(): ByteArray {

        val lat = latitude ?: Double.NaN
        val lon = longitude ?: Double.NaN
        val acc = accuracyMeters ?: Float.NaN

        return doubleToBytes(lat) +
                doubleToBytes(lon) +
                floatToBytes(acc) +
                stringToBytes(provider) +
                booleanToBytes(isMockDetected) +
                booleanToBytes(isStale) +
                booleanToBytes(teleportDetected) +
                doubleToBytes(distanceToAllowedZoneMeters ?: Double.NaN) +
                stringToBytes(zoneDecision?.policy?.name ?: "NONE") +
                stringToBytes(policyDecision.name) +
                booleanToBytes(justificationRequired) +
                stringToBytes(networkContext.toString()) +
                longToBytes(timestampMillis)
    }

    /* =========================================================================
     * 🖼 IMAGE HASH (DETERMINISTIC)
     * ========================================================================= */

    private fun hashBitmap(bitmap: android.graphics.Bitmap): ByteArray {

        val bytes = ByteArrayOutputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }

        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /* =========================================================================
     * 🚀 PIPELINE
     * ========================================================================= */

    suspend fun buildSignedOrFail(
        timeoutMs: Long
    ): Result<SignedPhysicalRealitySnapshot> = withContext(Dispatchers.IO) {

        /* ================= LOCATION ================= */
        val locationResult =
            locationIntegrityGuard.awaitFreshLocation(timeoutMs)

        if (locationResult !is LocationIntegrityResult.Allowed) {
            return@withContext Result.failure(
                IllegalStateException("Location failed")
            )
        }

        /* ================= FRAME ================= */
        val frame = cameraManager.captureSingleFrameSuspend(timeoutMs)
            ?: return@withContext Result.failure(
                IllegalStateException("Camera failed")
            )

        try {

            /* ================= TIME ================= */
            val timeSnapshot = TimeSource.snapshot()

            val snapshot = PhysicalRealitySnapshot(
                frozenFrame = frame,
                timeSnapshot = timeSnapshot,
                locationEvidence = locationResult.evidence
            )

            val snapshotId = UUID.randomUUID()
            val createdAt = System.currentTimeMillis()

            /* ================= CANONICAL PAYLOAD ================= */

            val imageHash = hashBitmap(frame)
            val timeBytes = timeSnapshot.toCanonicalBytes()
            val locationBytes = locationResult.evidence.toCanonicalBytes()

            val payload =
                intToBytes(imageHash.size) + imageHash +
                        intToBytes(timeBytes.size) + timeBytes +
                        intToBytes(locationBytes.size) + locationBytes

            /* ================= HASH ================= */

            val snapshotHash =
                MessageDigest.getInstance("SHA-256").digest(payload)

            /* ================= SIGN ================= */

            val signature =
                HardwareBackedSnapshotSigner.sign(snapshotHash)

            val certificateChain =
                HardwareBackedKeyManager.getCertificateChain()
                    .map { it.encoded }

            /* ================= FINAL OUTPUT ================= */

            return@withContext Result.success(
                SignedPhysicalRealitySnapshot(
                    snapshotId = snapshotId,
                    timestampMillis = createdAt,
                    payload = snapshot,
                    signature = signature,
                    certificateChain = certificateChain,
                    snapshotHash = snapshotHash // ✅ IMPORTANT FIX
                )
            )

        } catch (t: Throwable) {

            frame.recycle()
            return@withContext Result.failure(t)
        }
    }
}