package com.mahmoud.attendify.system.time

/**
 * TimeIntegrityPolicy
 *
 * ============================================================================
 * ROLE (What this class represents):
 * ============================================================================
 * An ADMINISTRATIVE POLICY object that defines the rules under which
 * device time is considered acceptable for attendance purposes.
 *
 * IMPORTANT:
 * ----------
 * This class DOES NOT:
 *  ❌ Validate time
 *  ❌ Compare clocks
 *  ❌ Detect tampering
 *
 * It ONLY defines *WHAT IS ALLOWED*.
 *
 * The enforcement logic lives exclusively in TimeIntegrityGuard.
 *
 * ============================================================================
 * WHY THIS SEPARATION EXISTS:
 * ============================================================================
 * - Policies are business / administrative decisions
 * - Guards are security / enforcement mechanisms
 *
 * Mixing them would:
 *  ❌ Obscure intent
 *  ❌ Make audits harder
 *  ❌ Create hidden assumptions
 */
data class TimeIntegrityPolicy(

    /**
     * requiredTimeZone
     *
     * ------------------------------------------------------------------------
     * The ONLY timezone considered valid for attendance.
     *
     * RATIONALE:
     * ----------
     * - Device local timezone is user-controlled
     * - VPNs / travel can shift timezone
     * - Attendance rules depend on a *corporate* notion of time
     *
     * SECURITY EFFECT:
     * ----------------
     * Prevents:
     *  - Timezone hopping
     *  - Cross-border clock manipulation
     *
     * Example:
     * --------
     * "Asia/Damascus"
     */
    val requiredTimeZone: String,

    /**
     * baseDriftToleranceMillis
     *
     * ------------------------------------------------------------------------
     * The BASE tolerance (in milliseconds) allowed between:
     *  - wall clock progression
     *  - elapsedRealtime progression
     *
     * WHY A BASE TOLERANCE EXISTS:
     * ----------------------------
     * Even on perfectly honest devices:
     *  - Scheduling delays exist
     *  - Minor clock skew exists
     *
     * 5 seconds is a CONSERVATIVE but REALISTIC value that:
     *  ✅ Absorbs benign jitter
     *  ❌ Does NOT hide real tampering
     */
    val baseDriftToleranceMillis: Long = 5_000,

    /**
     * driftPerDayMillis
     *
     * ------------------------------------------------------------------------
     * Additional drift tolerance PER DAY since the last valid anchor.
     *
     * WHY THIS EXISTS:
     * ----------------
     * Some OEMs aggressively suspend device timers during deep sleep.
     *
     * EFFECT:
     * -------
     * elapsedRealtime MAY lag slightly compared to wall clock
     * even WITHOUT user tampering.
     *
     * SECURITY DESIGN:
     * ----------------
     * - This allowance:
     *    ✅ Grows slowly
     *    ✅ Is anchor-bound
     *    ✅ Is reset after reboot
     *
     * Why 3000 ms (3 seconds)?
     * ------------------------
     * - Enough to absorb real-world deep-sleep drift
     * - Too small to hide deliberate clock walking attacks
     *
     * IMPORTANT:
     * ----------
     * This is NOT a free allowance.
     * It is applied only when:
     *  ✅ Anchor exists
     *  ✅ No reboot occurred
     */
    val driftPerDayMillis: Long = 3_000,

    /**
     * allowPostBootRecords
     *
     * ------------------------------------------------------------------------
     * Whether attendance is allowed AFTER a device reboot without
     * re-establishing a new anchor.
     *
     * DEFAULT = true
     *
     * WHY THIS IS CONFIGURABLE:
     * -------------------------
     * - Some organizations may allow same-day reboot usage
     * - Others may require strict re-anchoring
     *
     * NOTE:
     * -----
     * Even if TRUE:
     * - elapsedRealtime resets on reboot
     * - Drift detection logic will still apply
     */
    val allowPostBootRecords: Boolean = true,

    /**
     * requireInitialAnchor
     *
     * ------------------------------------------------------------------------
     * Whether an INITIAL trusted anchor is mandatory before ANY attendance.
     *
     * DEFAULT = true (SECURE DEFAULT)
     *
     * RATIONALE:
     * ----------
     * Without an anchor:
     *  - No baseline exists
     *  - Local device time is meaningless
     *
     * SECURITY IMPACT:
     * ----------------
     * If enabled:
     *  ✅ First attendance requires online verification
     *  ✅ Offline first-use is forbidden
     */
    val requireInitialAnchor: Boolean = true
)
