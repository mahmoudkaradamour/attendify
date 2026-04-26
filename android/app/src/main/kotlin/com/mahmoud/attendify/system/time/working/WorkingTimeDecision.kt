package com.mahmoud.attendify.system.time.working

/**
 * WorkingTimeDecision
 *
 * Result of evaluating current time
 * against a WorkingTimePolicy.
 *
 * This does NOT enforce anything,
 * it only explains the outcome.
 */
data class WorkingTimeDecision(

    /** Policy that was applied */
    val policy: WorkingTimePolicy,

    /** Is the attempt inside working hours? */
    val isWithinWorkingTime: Boolean,

    /** Is today a holiday? */
    val isHoliday: Boolean,

    /** Final administrative action */
    val action: OutOfWorkingTimeAction
)