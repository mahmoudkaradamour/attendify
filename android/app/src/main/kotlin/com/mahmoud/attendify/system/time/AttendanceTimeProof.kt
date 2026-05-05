package com.mahmoud.attendify.system.time

/**
 * AttendanceTimeProof
 *
 * Immutable, cryptographically signed forensic proof that
 * represents the full temporal integrity state of an
 * attendance operation.
 *
 * This object is designed to be:
 * - Stored offline
 * - Transmitted to backend
 * - Audited later
 * - Used in dispute resolution
 *
 * IMPORTANT:
 * - This is a DATA PROOF, not a UI or business object.
 * - Once created, it must never be modified.
 */
data class AttendanceTimeProof(
    /**
     * UTC timestamp used for attendance.
     * Anchored and drift-checked.
     */
    val utcTimestampMillis: Long,

    /**
     * Monotonic clock snapshot.
     * Used to detect time rollback / replay.
     */
    val elapsedRealtimeMillis: Long,

    /**
     * Device uptime at the moment of attendance.
     */
    val uptimeMillis: Long,

    /**
     * Logical boot identifier.
     * Changes after device reboot.
     */
    val bootId: String,

    /**
     * Device timezone ID.
     * Must match administrative policy.
     */
    val timeZoneId: String,

    /**
     * Genesis anchor wall-clock time.
     */
    val anchorWallClockMillis: Long,

    /**
     * Genesis anchor monotonic time.
     */
    val anchorElapsedMillis: Long,

    /**
     * Absolute anchor-based drift in milliseconds.
     */
    val driftMillis: Long,

    /**
     * Final integrity verdict produced by TimeIntegrityGuard.
     */
    val verificationResult: TimeIntegrityResult,

    /**
     * Optional GPS-based UTC timestamp.
     * NEVER used as a primary acceptance factor.
     */
    val gpsUtcMillis: Long?,

    /**
     * 🔐 FORENSIC FLAG
     *
     * Indicates whether biometric liveness checks
     * were EXECUTED for this attendance attempt.
     *
     * IMPORTANT:
     * - This is NOT an input from Flutter.
     * - This is NOT derived later.
     * - This value is bound cryptographically to the timestamp.
     *
     * Purpose:
     * - Prevent administrative collusion.
     * - Prevent replay of older "liveness=true" proofs.
     * - Provide court-defensible evidence.
     */
    val wasLivenessExecuted: Boolean,

    /**
     * Cryptographic signature of the deterministic payload.
     * Generated using Android Keystore.
     */
    val signature: String
)