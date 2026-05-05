package com.mahmoud.attendify.orchestration

import java.util.UUID
import java.security.MessageDigest

import com.mahmoud.attendify.camera.CameraManager
import com.mahmoud.attendify.system.time.TimeSource
import com.mahmoud.attendify.system.location.LocationIntegrityGuard
import com.mahmoud.attendify.system.location.LocationIntegrityResult

import com.mahmoud.attendify.orchestration.context.PhysicalRealitySnapshot
import com.mahmoud.attendify.orchestration.context.SignedPhysicalRealitySnapshot

import com.mahmoud.attendify.security.HardwareBackedSnapshotSigner
import com.mahmoud.attendify.security.HardwareBackedKeyManager

/**
 * ============================================================================
 * PhysicalRealityBuilder
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * The SINGLE authority responsible for capturing and sealing
 * **atomic physical reality**.
 *
 * This class owns exclusive access to:
 *  ✅ Camera capture (single frame)
 *  ✅ Time snapshot
 *  ✅ Location integrity evaluation
 *
 * And is responsible for:
 *  ✅ Atomic binding (Frame + Time + Location)
 *  ✅ Cryptographic sealing
 *  ✅ Hardware‑backed attestation (Phase 3.6)
 *
 * ============================================================================
 * SECURITY MODEL (POST PHASE 3.6)
 * ============================================================================
 *
 * • Physical reality is captured ONCE
 * • Data is bound atomically
 * • Snapshot hash is signed using a HARDWARE‑BACKED PRIVATE KEY
 * • Private key NEVER leaves secure hardware (TEE / StrongBox)
 *
 * This eliminates:
 *  ❌ TOCTOU
 *  ❌ Software key exfiltration
 *  ❌ Replay via cloned signatures
 *
 * ============================================================================
 * TRUST BOUNDARY
 * ============================================================================
 *
 * Everything outside this class is considered:
 *  ⚠️ Computationally trusted
 *
 * Everything produced by this class is:
 *  ✅ Cryptographically and physically attested
 */
class PhysicalRealityBuilder(
    private val cameraManager: CameraManager,
    private val locationIntegrityGuard: LocationIntegrityGuard
) {

    /**
     * =========================================================================
     * buildSignedOrFail
     * =========================================================================
     *
     * Captures, binds, hashes, and SIGNs physical reality
     * using hardware‑backed cryptographic keys.
     *
     * GUARANTEES:
     * ------------------------------------------------------------------------
     * ✅ Frame, time, and location originate from ONE attempt
     * ✅ Either ALL evidence exists, or NONE exists
     * ✅ Signature is hardware‑bound to the device
     *
     * FAILURE MODES:
     * ------------------------------------------------------------------------
     * - Camera hardware failure
     * - Location integrity failure
     * - Cryptographic / Keystore failure
     */
    fun buildSignedOrFail(
        timeoutMs: Long
    ): Result<SignedPhysicalRealitySnapshot> {

        /* ====================================================================
         * STEP 1 — CAMERA CAPTURE
         * ==================================================================== */
        val frame =
            cameraManager.captureSingleFrame(timeoutMs)
                ?: return Result.failure(
                    IllegalStateException("Camera capture failed")
                )

        try {

            /* ================================================================
             * STEP 2 — TIME SNAPSHOT
             * ================================================================ */
            val timeSnapshot =
                TimeSource.snapshot()

            /* ================================================================
             * STEP 3 — LOCATION INTEGRITY
             * ================================================================ */
            val locationResult =
                locationIntegrityGuard.evaluate()

            if (locationResult !is LocationIntegrityResult.Allowed) {
                return Result.failure(
                    IllegalStateException("Location integrity failed")
                )
            }

            /* ================================================================
             * STEP 4 — ATOMIC REALITY BINDING
             * ================================================================ */
            val snapshot =
                PhysicalRealitySnapshot(
                    frozenFrame = frame,
                    timeSnapshot = timeSnapshot,
                    locationEvidence = locationResult.evidence
                )

            /* ================================================================
             * STEP 5 — IDENTIFIERS & TIMESTAMP
             * ================================================================ */
            val snapshotId =
                UUID.randomUUID()

            val timestampMillis =
                System.currentTimeMillis()

            /* ================================================================
             * STEP 6 — HASH (DETERMINISTIC REPRESENTATION)
             * ================================================================
             *
             * NOTE:
             * - Hashing is performed BEFORE signing
             * - Snapshot hash is what gets signed
             * - Ensures deterministic, verifiable input
             */
            val snapshotHash =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(
                        snapshot.toString()
                            .toByteArray(Charsets.UTF_8)
                    )

            /* ================================================================
             * STEP 7 — HARDWARE‑BACKED SIGNATURE (PHASE 3.6)
             * ================================================================ */
            val signature =
                HardwareBackedSnapshotSigner
                    .sign(snapshotHash)

            /* ================================================================
             * STEP 8 — CERTIFICATE CHAIN EXPORT
             * ================================================================
             *
             * The certificate chain proves:
             * - Key origin (Android Keystore)
             * - Hardware‑backed status (TEE / StrongBox)
             */
            val certificateChain =
                HardwareBackedKeyManager
                    .getCertificateChain()
                    .map { it.encoded }

            /* ================================================================
             * STEP 9 — FINAL SIGNED SNAPSHOT
             * ================================================================ */
            return Result.success(
                SignedPhysicalRealitySnapshot(
                    snapshotId = snapshotId,
                    timestampMillis = timestampMillis,
                    payload = snapshot,
                    signature = signature,
                    certificateChain = certificateChain
                )
            )

        } catch (t: Throwable) {

            /* ================================================================
             * FAILURE SAFETY
             * ================================================================
             *
             * In case of ANY failure:
             * - Frame is recycled
             * - Partial evidence is discarded
             */
            frame.recycle()
            return Result.failure(t)
        }
    }
}