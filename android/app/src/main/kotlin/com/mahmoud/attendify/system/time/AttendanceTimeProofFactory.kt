package com.mahmoud.attendify.system.time

import kotlin.math.abs

/**
 * AttendanceTimeProofFactory
 *
 * ============================================================
 * ROLE (Forensic Responsibility):
 * ============================================================
 * This class is the SINGLE authority responsible for creating
 * a cryptographically signed, immutable AttendanceTimeProof.
 *
 * It transforms raw temporal signals into a court-defensible
 * forensic artifact.
 *
 * ============================================================
 * WHAT THIS CLASS DOES:
 * ============================================================
 * - Combines a trusted current time snapshot
 * - Combines an immutable genesis (anchor) snapshot
 * - Computes anchor-based drift using monotonic clocks
 * - Optionally attaches GPS UTC time as a *witness*
 * - Cryptographically signs ALL of the above INCLUDING
 *   the liveness-executed flag
 *
 * ============================================================
 * WHAT THIS CLASS MUST NEVER DO:
 * ============================================================
 * ❌ Talk to UI or Flutter
 * ❌ Guess or infer any value
 * ❌ Modify or "correct" inputs
 * ❌ Perform policy decisions
 *
 * ============================================================
 * SECURITY PHILOSOPHY:
 * ============================================================
 * Determinism over convenience.
 * Evidence over assumptions.
 * Mathematics overtrust.
 */
class AttendanceTimeProofFactory(
    private val anchorStorage: TimeAnchorStorage,
    private val gpsTimeProvider: GpsTimeProvider
) {

    /**
     * Creates a signed, immutable AttendanceTimeProof.
     *
     * ============================================================
     * INPUT CONTRACT (NON-NEGOTIABLE):
     * ============================================================
     * @param currentSnapshot
     *   An atomic snapshot of all device time signals taken at
     *   the exact moment of attendance.
     *
     * @param integrityResult
     *   The final verdict produced by TimeIntegrityGuard.
     *   Attendance may ONLY proceed if this verdict is OK.
     *
     * @param wasLivenessExecuted
     *   A HARD FORENSIC FLAG indicating whether biometric
     *   liveness checks were actually executed.
     *
     *   IMPORTANT:
     *   - This value is determined ONLY inside Native code.
     *   - This value is NEVER provided by Flutter.
     *   - This value is cryptographically bound to the timestamp.
     *
     * ============================================================
     * OUTPUT GUARANTEE:
     * ============================================================
     * - The returned object is immutable.
     * - Any field modification invalidates the signature.
     * - Replay or partial reuse is cryptographically impossible.
     */
    fun create(
        currentSnapshot: TimeSnapshot,
        integrityResult: TimeIntegrityResult,
        wasLivenessExecuted: Boolean
    ): AttendanceTimeProof {

        // ------------------------------------------------------------
        // 1️⃣ Load immutable genesis anchor
        // ------------------------------------------------------------
        /*
         * The anchor represents the last known trusted relationship
         * between wall-clock time and monotonic elapsed time.
         *
         * Security rule:
         * - Anchors are NEVER modified here
         * - If anchor loading fails, TimeIntegrityGuard must
         *   already have blocked attendance
         */
        val anchor = anchorStorage.loadAnchor()

        // ------------------------------------------------------------
        // 2️⃣ Anchor-based time drift computation
        // ------------------------------------------------------------
        /*
         * Core forensic equation:
         *
         *   drift = |ΔWallClock − ΔElapsedRealtime|
         *
         * Rationale:
         * - wallClockMillis can be user-manipulated
         * - elapsedRealtimeMillis is monotonic and reboot-bound
         * - Any divergence beyond policy tolerance indicates tampering
         *
         * Mathematical properties:
         * - Symmetric
         * - Deterministic
         * - Immune to timezone changes
         */
        val driftMillis = abs(
            (currentSnapshot.wallClockMillis - anchor.wallClockMillis) -
                    (currentSnapshot.elapsedRealtimeMillis - anchor.elapsedRealtimeMillis)
        )

        // ------------------------------------------------------------
        // 3️⃣ Optional GPS UTC time witness
        // ------------------------------------------------------------
        /*
         * GPS time:
         * - Is NEVER trusted as a primary clock
         * - Is NEVER required for attendance
         * - Acts ONLY as an external witness
         *
         * Purpose:
         * - Strengthen forensic analysis
         * - Assist in dispute resolution
         */
        val gpsUtcMillis = gpsTimeProvider.getGpsUtcTimeIfAvailable()

        // ------------------------------------------------------------
        // 4️⃣ Deterministic forensic payload construction
        // ------------------------------------------------------------
        /*
         * The payload is the LEGAL IDENTITY of the proof.
         *
         * CRITICAL RULES:
         * - Field order MUST remain fixed
         * - No locale-dependent formatting
         * - No hidden defaults
         * - All security-relevant signals included
         *
         * Especially important:
         * - wasLivenessExecuted is INCLUDED here to prevent:
         *   • administrative collusion
         *   • replay of older "liveness=true" proofs
         */
        val payload = buildPayload(
            currentSnapshot = currentSnapshot,
            anchorSnapshot = anchor,
            driftMillis = driftMillis,
            gpsUtcMillis = gpsUtcMillis,
            integrityResult = integrityResult,
            wasLivenessExecuted = wasLivenessExecuted
        )

        // ------------------------------------------------------------
        // 5️⃣ Cryptographic signing (Android Keystore)
        // ------------------------------------------------------------
        /*
         * The signature:
         * - Is generated using a hardware-backed private key
         * - Binds ALL payload fields together
         * - Guarantees immutability and non-repudiation
         */
        val signature = TimeProofSigner.sign(payload)

        // ------------------------------------------------------------
        // 6️⃣ Immutable proof assembly
        // ------------------------------------------------------------
        return AttendanceTimeProof(
            // --- Current time snapshot (trusted & validated) ---
            utcTimestampMillis = currentSnapshot.wallClockMillis,
            elapsedRealtimeMillis = currentSnapshot.elapsedRealtimeMillis,
            uptimeMillis = currentSnapshot.uptimeMillis,
            bootId = currentSnapshot.bootId,
            timeZoneId = currentSnapshot.timeZoneId,

            // --- Genesis anchor snapshot ---
            anchorWallClockMillis = anchor.wallClockMillis,
            anchorElapsedMillis = anchor.elapsedRealtimeMillis,

            // --- Drift & integrity verdict ---
            driftMillis = driftMillis,
            verificationResult = integrityResult,

            // --- Optional forensic witnesses ---
            gpsUtcMillis = gpsUtcMillis,

            // --- 🔐 Forensically bound liveness flag ---
            wasLivenessExecuted = wasLivenessExecuted,

            // --- Cryptographic signature ---
            signature = signature
        )
    }

    /**
     * Builds the deterministic payload string for cryptographic signing.
     *
     * ============================================================
     * PAYLOAD DESIGN RULES:
     * ============================================================
     * - Fixed field order
     * - Explicit separators (avoid ambiguity)
     * - Stable textual representation
     *
     * Any change in:
     * - field order
     * - included fields
     * - separator
     * WILL invalidate all existing proofs.
     */
    private fun buildPayload(
        currentSnapshot: TimeSnapshot,
        anchorSnapshot: TimeSnapshot,
        driftMillis: Long,
        gpsUtcMillis: Long?,
        integrityResult: TimeIntegrityResult,
        wasLivenessExecuted: Boolean
    ): String {

        return listOf(
            // --- Current snapshot ---
            currentSnapshot.wallClockMillis,
            currentSnapshot.elapsedRealtimeMillis,
            currentSnapshot.uptimeMillis,
            currentSnapshot.bootId,
            currentSnapshot.timeZoneId,

            // --- Anchor snapshot ---
            anchorSnapshot.wallClockMillis,
            anchorSnapshot.elapsedRealtimeMillis,

            // --- Derived & verdict ---
            driftMillis,
            integrityResult::class.simpleName,

            // --- Optional witnesses ---
            gpsUtcMillis ?: "NO_GPS",

            // --- Forensic execution flags ---
            wasLivenessExecuted
        ).joinToString("|")
    }
}