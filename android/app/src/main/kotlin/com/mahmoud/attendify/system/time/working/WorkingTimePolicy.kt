package com.mahmoud.attendify.system.time.working

import java.time.ZoneId

/**
 * WorkingTimePolicy
 *
 * Administrative policy defining when attendance
 * is ALLOWED from a time perspective.
 *
 * This policy can be assigned to:
 * - employee
 * - group
 * - office
 * - multiple offices
 *
 * IMPORTANT SEPARATION:
 * ---------------------
 * - TimeIntegrity validates "is time correct?"
 * - WorkingTimePolicy decides "is attendance allowed now?"
 */
data class WorkingTimePolicy(

    /** Identifier for backend and audit correlation */
    val id: String,

    /** Human‑readable name */
    val name: String,

    /** Timezone used to interpret working hours */
    val timeZone: ZoneId,

    /** Days considered working days */
    val workingDays: Set<WorkingDay>,

    /** Working hours definition */
    val workingHours: WorkingHours,

    /** Fixed and variable holidays */
    val holidays: Set<Holiday> = emptySet(),

    /** Behavior outside working time */
    val outOfWorkingTimeAction: OutOfWorkingTimeAction
)