package com.mahmoud.attendify.system.time

import kotlin.math.abs

/**
 * AttendanceTimeProofFactory
 *
 * Builds a signed, immutable AttendanceTimeProof by combining:
 * - Current time snapshot
 * - Anchored genesis snapshot
 * - Time drift analysis
 * - Optional GPS time witness
 *
 * This is a forensic-grade proof intended for:
 * - Offline persistence
 * - Online verification
 * - Audit and dispute resolution
 */
class AttendanceTimeProofFactory(
    private val anchorStorage: TimeAnchorStorage,
    private val gpsTimeProvider: GpsTimeProvider
) {

    /**
     * Creates a signed AttendanceTimeProof.
     *
     * @param currentSnapshot current atomic time snapshot
     * @param integrityResult result produced by TimeIntegrityGuard
     */
    fun create(
        currentSnapshot: TimeSnapshot,
        integrityResult: TimeIntegrityResult
    ): AttendanceTimeProof {

        // Load immutable genesis anchor
        val anchor = anchorStorage.loadAnchor()

        // Core time drift equation:
        // |ΔWallClock - ΔElapsedRealtime|
        val driftMillis = abs(
            (currentSnapshot.wallClockMillis - anchor.wallClockMillis) -
                    (currentSnapshot.elapsedRealtimeMillis - anchor.elapsedRealtimeMillis)
        )

        // Optional GPS time witness (never mandatory)
        val gpsUtcMillis = gpsTimeProvider.getGpsUtcTimeIfAvailable()

        // Deterministic payload used for cryptographic signing
        val payload = buildPayload(
            currentSnapshot = currentSnapshot,
            anchorSnapshot = anchor,
            driftMillis = driftMillis,
            gpsUtcMillis = gpsUtcMillis,
            integrityResult = integrityResult
        )

        // Sign payload using Android Keystore
        val signature = TimeProofSigner.sign(payload)

        return AttendanceTimeProof(
            utcTimestampMillis = currentSnapshot.wallClockMillis,
            elapsedRealtimeMillis = currentSnapshot.elapsedRealtimeMillis,
            uptimeMillis = currentSnapshot.uptimeMillis,
            bootId = currentSnapshot.bootId,
            timeZoneId = currentSnapshot.timeZoneId,

            anchorWallClockMillis = anchor.wallClockMillis,
            anchorElapsedMillis = anchor.elapsedRealtimeMillis,

            driftMillis = driftMillis,
            verificationResult = integrityResult,

            gpsUtcMillis = gpsUtcMillis,
            signature = signature
        )
    }

    /**
     * Builds deterministic payload string for signing.
     *
     * IMPORTANT:
     * - Order MUST remain fixed
     * - No locale‑dependent formatting
     */
    private fun buildPayload(
        currentSnapshot: TimeSnapshot,
        anchorSnapshot: TimeSnapshot,
        driftMillis: Long,
        gpsUtcMillis: Long?,
        integrityResult: TimeIntegrityResult
    ): String {

        return listOf(
            currentSnapshot.wallClockMillis,
            currentSnapshot.elapsedRealtimeMillis,
            currentSnapshot.uptimeMillis,
            currentSnapshot.bootId,
            currentSnapshot.timeZoneId,

            anchorSnapshot.wallClockMillis,
            anchorSnapshot.elapsedRealtimeMillis,

            driftMillis,
            gpsUtcMillis ?: "NO_GPS",
            integrityResult::class.simpleName
        ).joinToString("|")
    }
}
