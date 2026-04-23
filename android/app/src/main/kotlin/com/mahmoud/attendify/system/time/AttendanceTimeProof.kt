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
     * UTC timestamp used for attendance (wall clock).
     * Anchored and drift-checked.
     */
    val utcTimestampMillis: Long,

    /**
     * Monotonic clock snapshot (milliseconds since boot).
     * Used for tamper detection.
     */
    val elapsedRealtimeMillis: Long,

    /**
     * Device uptime at the moment of attendance.
     * Secondary integrity signal.
     */
    val uptimeMillis: Long,

    /**
     * Logical boot identifier.
     * Changes after device reboot.
     */
    val bootId: String,

    /**
     * Device timezone ID at time of attendance.
     * Compared against administrative policy.
     */
    val timeZoneId: String,

    /**
     * Genesis anchor wall-clock time (UTC).
     * Obtained from backend during mandatory initial handshake.
     */
    val anchorWallClockMillis: Long,

    /**
     * Genesis anchor monotonic time.
     * Used to calculate time drift.
     */
    val anchorElapsedMillis: Long,

    /**
     * Absolute time drift in milliseconds.
     * |ΔWallClock − ΔElapsedRealtime|
     */
    val driftMillis: Long,

    /**
     * Final integrity verdict produced by TimeIntegrityGuard.
     * (OK / Blocked / Tampered)
     */
    val verificationResult: TimeIntegrityResult,

    /**
     * Optional GPS-based UTC timestamp.
     *
     * Acts only as an integrity witness (forensic signal),
     * NEVER as a standalone acceptance factor.
     */
    val gpsUtcMillis: Long?,

    /**
     * Cryptographic signature of the deterministic payload.
     *
     * Generated using Android Keystore.
     * Proves authenticity and prevents record tampering.
     */
    val signature: String
)