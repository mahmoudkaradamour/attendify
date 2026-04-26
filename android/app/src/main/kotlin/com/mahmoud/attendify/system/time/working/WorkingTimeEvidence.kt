package com.mahmoud.attendify.system.time.working

/**
‑grade record describing * WorkingTimeEvidence
 * how working‑time policy affected an attendance attempt.
 *
 * PRINCIPLES:
 * -----------
 * - Evidence NEVER decides.
 * - Evidence NEVER enforces.
 * - Evidence ONLY records applied policy context.
 */
data class WorkingTimeEvidence(

    /** Identifier of the applied working time policy */
    val policyId: String,

    /** Human‑readable policy name (for audit reports) */
    val policyName: String,

    /** Is the attempt within configured working hours? */
    val isWithinWorkingTime: Boolean,

    /** Was the day classified as a holiday by policy? */
    val isHoliday: Boolean,

    /**
     * Final administrative action applied by working‑time policy.
     *
     * Examples:
     * - ALLOW
     * - ALLOW_WITH_JUSTIFICATION
     * - BLOCK (will not reach Evidence if blocked earlier)
     */
    val action: OutOfWorkingTimeAction
)
