package com.mahmoud.attendify.orchestration

import java.util.UUID
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import kotlin.math.abs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.SystemClock
import android.graphics.Bitmap

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
import com.mahmoud.attendify.security.canonical.CanonicalSerializer

/**
 * =============================================================================
 * 🧠 PhysicalRealityBuilder — Deterministic Reality Binding Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 FORMAL SYSTEM MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   R(t) = captured physical reality at time t
 *   C(.) = canonical encoding function
 *   H(.) = SHA-256 cryptographic hash
 *   S(.) = hardware-backed signature
 *
 * Then:
 *
 *   Evidence = S(H(C(R(t))))
 *
 * -----------------------------------------------------------------------------
 * 🎯 SYSTEM OBJECTIVE
 * -----------------------------------------------------------------------------
 *
 * Convert UNTRUSTED physical inputs (camera + GPS + time)
 * into a TRUSTED, signed, and verifiable digital artifact.
 *
 * -----------------------------------------------------------------------------
 * 🔐 CORE SECURITY PROPERTIES
 * -----------------------------------------------------------------------------
 *
 *   ✅ Temporal coherence  (sensor synchronization)
 *   ✅ Spatial validity    (accuracy constraint)
 *   ✅ Canonical determinism (bit-level consistency)
 *   ✅ Cryptographic integrity (SHA-256)
 *   ✅ Hardware authenticity (TEE / StrongBox)
 *
 * -----------------------------------------------------------------------------
 * 📊 EXECUTION PIPELINE (FULL FLOW)
 * -----------------------------------------------------------------------------
 *
 *   ┌─────────────────────────────┐
 *   │ Location Acquisition       │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ Camera Frame Capture        │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ Temporal Constraint Check   │
 *   │ |ΔT| ≤ 500 ms              │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ Canonical Encoding          │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ SHA-256 Hash                │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ Hardware Signature          │
 *   └────────────┬───────────────┘
 *                ▼
 *   ┌─────────────────────────────┐
 *   │ Signed Evidence Snapshot    │
 *   └─────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * ❗ SYSTEM INVARIANTS (CRITICAL)
 * -----------------------------------------------------------------------------
 *
 * 1. Determinism:
 *    Same physical input → identical hash
 *
 * 2. Freshness:
 *    No stale or replayed data allowed
 *
 * 3. Binding:
 *    Image + location + time MUST belong to same event
 *
 * -----------------------------------------------------------------------------
 */
class PhysicalRealityBuilder(
    private val cameraManager: CameraManager,
    private val locationIntegrityGuard: LocationIntegrityGuard
) {

    /* =========================================================================
     * ⏱ TIME CANONICALIZATION
     * ========================================================================= */

    /**
     * Converts TimeSnapshot → canonical byte representation.
     *
     * Ensures:
     *   - multi-clock correlation
     *   - resistance against clock spoofing
     */
    private fun TimeSnapshot.toCanonicalBytes(): ByteArray {
        return CanonicalSerializer.longToBytes(wallClockMillis) +
                CanonicalSerializer.longToBytes(elapsedRealtimeMillis) +
                CanonicalSerializer.longToBytes(uptimeMillis) +
                CanonicalSerializer.stringToBytes(bootId) +
                CanonicalSerializer.stringToBytes(timeZoneId)
    }

    /* =========================================================================
     * 📍 LOCATION CANONICALIZATION
     * ========================================================================= */

    /**
     * Converts location evidence into deterministic representation.
     *
     * Includes:
     *   - scaled coordinates
     *   - accuracy
     *   - spoofing indicators
     */
    private fun LocationEvidence.toCanonicalBytes(): ByteArray {

        return CanonicalSerializer.encodeLatitude(latitude) +
                CanonicalSerializer.encodeLongitude(longitude) +
                CanonicalSerializer.floatToBytes(accuracyMeters ?: Float.NaN) +
                CanonicalSerializer.stringToBytes(provider) +
                CanonicalSerializer.booleanToBytes(isMockDetected) +
                CanonicalSerializer.booleanToBytes(isStale) +
                CanonicalSerializer.booleanToBytes(teleportDetected) +
                CanonicalSerializer.longToBytes(timestampMillis)
    }

    /* =========================================================================
     * 🖼 IMAGE HASHING
     * ========================================================================= */

    /**
     * Converts bitmap → SHA-256 hash.
     *
     * Rationale:
     *   Image is large and non-deterministic in raw form.
     *   Hash provides:
     *     - compact representation
     *     - tamper detection
     *
     * Flow:
     *
     *   Bitmap → PNG encoding → ByteArray → SHA-256 → Hash
     */
    private fun hashBitmap(bitmap: Bitmap): ByteArray {

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    /* =========================================================================
     * 🚀 MAIN EXECUTION PIPELINE
     * ========================================================================= */

    suspend fun buildSignedOrFail(
        timeoutMs: Long
    ): Result<SignedPhysicalRealitySnapshot> = withContext(Dispatchers.IO) {

        /* ================================================================
         * STEP 1 — LOCATION ACQUISITION
         * ================================================================ */

        val locationResult =
            locationIntegrityGuard.awaitFreshLocation(timeoutMs)

        if (locationResult !is LocationIntegrityResult.Allowed) {
            return@withContext Result.failure(
                IllegalStateException("Location integrity failure")
            )
        }

        val location = locationResult.evidence

        /* ================================================================
         * STEP 2 — CAMERA CAPTURE (MONOTONIC TIME)
         * ================================================================ */

        val captureStartNanos = SystemClock.elapsedRealtimeNanos()

        val frame = cameraManager.captureSingleFrameSuspend(timeoutMs)
            ?: return@withContext Result.failure(
                IllegalStateException("Camera capture failed")
            )

        val captureEndNanos = SystemClock.elapsedRealtimeNanos()

        /* ================================================================
         * STEP 3 — TEMPORAL VALIDATION
         * ================================================================
         *
         * Prevents:
         *   - stale GPS reuse
         *   - TOCTOU inconsistencies
         */

        val locationNanos =
            location.timestampMillis * 1_000_000

        val deltaMillis =
            abs(locationNanos - captureEndNanos) / 1_000_000

        if (deltaMillis > 500) {
            frame.recycle()
            return@withContext Result.failure(
                IllegalStateException("Temporal desynchronization")
            )
        }

        /**
         * Spatial constraint:
         * Ensures location is meaningful.
         */
        if ((location.accuracyMeters ?: 999f) > 50f) {
            frame.recycle()
            return@withContext Result.failure(
                IllegalStateException("Low location accuracy")
            )
        }

        try {

            /* ============================================================
             * STEP 4 — TIME SNAPSHOT
             * ============================================================ */

            val timeSnapshot = TimeSource.snapshot()

            val snapshot = PhysicalRealitySnapshot(
                frozenFrame = frame,
                timeSnapshot = timeSnapshot,
                locationEvidence = location
            )

            val snapshotId = UUID.randomUUID()
            val createdAt = System.currentTimeMillis()

            /* ============================================================
             * STEP 5 — CANONICAL PAYLOAD CONSTRUCTION
             * ============================================================ */

            val imageHash = hashBitmap(frame)
            val timeBytes = timeSnapshot.toCanonicalBytes()
            val locationBytes = location.toCanonicalBytes()

            val payload =
                CanonicalSerializer.intToBytes(imageHash.size) + imageHash +
                        CanonicalSerializer.intToBytes(timeBytes.size) + timeBytes +
                        CanonicalSerializer.intToBytes(locationBytes.size) + locationBytes

            /* ============================================================
             * STEP 6 — HASH GENERATION
             * ============================================================ */

            val snapshotHash =
                MessageDigest.getInstance("SHA-256").digest(payload)

            /* ============================================================
             * STEP 7 — HARDWARE SIGNATURE
             * ============================================================ */

            val signature =
                HardwareBackedSnapshotSigner.sign(snapshotHash)

            val certificateChain =
                HardwareBackedKeyManager.getCertificateChain()
                    .map { it.encoded }

            /* ============================================================
             * STEP 8 — FINALIZATION
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
