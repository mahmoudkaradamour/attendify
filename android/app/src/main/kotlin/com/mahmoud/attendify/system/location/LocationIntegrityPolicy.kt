package com.mahmoud.attendify.system.location

/**
 * LocationIntegrityPolicy
 *
 * PURPOSE:
 * --------
 * This class defines ALL administrative rules that control
 * how location and connectivity are evaluated for attendance.
 *
 * IMPORTANT ARCHITECTURAL RULE:
 * -----------------------------
 * - This file contains ZERO business logic.
 * - NO calculations.
 * - NO conditions.
 * - NO assumptions.
 *
 * It is a pure, declarative policy model.
 *
 * Every value here is:
 * - defined by administration,
 * - interpreted by LocationIntegrityGuard,
 * - and fully auditable.
 */
data class LocationIntegrityPolicy(

    /* ==================================================
     * SECTION 1 — GLOBAL LOCATION CONTROL
     * ================================================== */

    /**
     * Master switch for location verification.
     *
     * false:
     *   - Location will not be requested at all.
     *   - Attendance proceeds without any location context.
     *   - A forensic record will still note that
     *     location verification was disabled by policy.
     *
     * true:
     *   - LocationIntegrityGuard will attempt
     *     to obtain and evaluate a fresh location fix.
     */
    val locationVerificationEnabled: Boolean,

    /**
     * Maximum time (in seconds) the system is allowed
     * to wait for a fresh GPS fix.
     *
     * This value is NOT technical.
     * It is an ADMINISTRATIVE decision that balances:
     * - user experience,
     * - physical building constraints,
     * - and security strictness.
     *
     * Typical values:
     * - 30 seconds (strict)
     * - 60 seconds (balanced)
     * - 120 seconds (very tolerant)
     */
    val locationFixTimeoutSeconds: Long,

    /* ==================================================
     * SECTION 2 — GPS TIMEOUT / WEAK SIGNAL BEHAVIOR
     * ================================================== */

    /**
     * Defines what happens AFTER the GPS timeout expires
     * without obtaining a reliable fix.
     *
     * This explicitly distinguishes:
     * - natural technical failure (weak signal),
     * - from malicious behavior.
     */
    val onLocationTimeout: LocationTimeoutAction,

    /**
     * Whether the employee MUST provide a justification
     * when attendance is accepted after a GPS timeout.
     *
     * This justification:
     * - is NOT used for validation,
     * - is NOT interpreted by the system,
     * - exists ONLY for audit and review.
     */
    val requireJustificationOnTimeout: Boolean,

    /* ==================================================
     * SECTION 3 — CONNECTION CONTEXT POLICY
     * ================================================== */

    /**
     * Whether attendance is allowed when the device
     * is connected through Wi‑Fi.
     *
     * IMPORTANT:
     * - Wi‑Fi is treated ONLY as connection context.
     * - No trust is placed in SSID, BSSID, or MAC addresses.
     */
    val allowWifi: Boolean,

    /**
     * Whether attendance is allowed when the device
     * is connected via cellular data (SIM / LTE / 5G).
     *
     * Cellular is generally harder to spoof locally
     * than Wi‑Fi, but it is still NOT trusted absolutely.
     */
    val allowCellular: Boolean,

    /**
     * Whether attendance is allowed with NO network
     * connectivity at all.
     *
     * Offline attendance is considered HIGH‑RISK context
     * and is always fully logged and auditable.
     */
    val allowOffline: Boolean,

    /* ==================================================
     * SECTION 4 — JUSTIFICATION RULES
     * ================================================== */

    /**
     * Require a written justification if attendance
     * occurs while connected via Wi‑Fi.
     *
     * Typical use cases:
     * - organization allows Wi‑Fi attendance,
     *   but wants accountability.
     */
    val requireJustificationOnWifi: Boolean,

    /**
     * Require a written justification if attendance
     * occurs while the device is OFFLINE.
     *
     * Offline attendance should never be silent.
     */
    val requireJustificationOnOffline: Boolean,

    /* ==================================================
     * SECTION 5 — ANTI‑TAMPERING BEHAVIOR
     * ================================================== */

    /**
     * Whether attendance must be BLOCKED immediately
     * if the system detects mock location indicators.
     *
     * If false:
     * - Attendance may still be allowed,
     * - but with full tampering evidence recorded.
     */
    val blockOnMockLocation: Boolean,

    /**
     * Whether attendance must be BLOCKED immediately
     * if impossible movement (teleportation) is detected.
     *
     * Teleportation is evaluated across sessions using
     * last known valid location anchor.
     */
    val blockOnTeleportation: Boolean,

    /**
     * Whether attendance is allowed to proceed
     * even when tampering signals are detected,
     * provided that full forensic evidence is stored.
     *
     * This enables "allow but flag" operational models.
     */
    val allowWithTamperEvidence: Boolean,

    /* ==================================================
     * SECTION 6 — TELEPORTATION THRESHOLD
     * ================================================== */

    /**
     * Maximum plausible human movement speed
     * in meters per second.
     *
     * This value is used to detect teleportation
     * across sessions (force stop → relocate).
     *
     * Reference values:
     * - 1.5–3 m/s  → walking
     * - 5–7 m/s    → running
     * - 10–15 m/s  → vehicle in city
     * - >50 m/s    → physically impossible
     *
     * The choice is STRICTLY a policy decision,
     * not a hard‑coded technical constant.
     */
    val maxHumanSpeedMetersPerSecond: Double
)

/**
 * LocationTimeoutAction
 *
 * Defines the administrative response
 * when GPS fix cannot be obtained in time.
 */
enum class LocationTimeoutAction {

    /**
     * Attendance is rejected outright.
     * No attendance record is created.
     */
    BLOCK,

    /**
     * Attendance is accepted WITHOUT any location,
     * but with full forensic evidence and high risk flag.
     */
    ALLOW_WITH_EVIDENCE,

    /**
     * Attendance is accepted ONLY IF the employee
     * provides a written justification.
     *
     * Attendance remains incomplete until justification
     * is supplied.
     */
    ALLOW_WITH_JUSTIFICATION
}