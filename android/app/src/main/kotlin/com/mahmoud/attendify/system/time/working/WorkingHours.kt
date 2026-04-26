package com.mahmoud.attendify.system.time.working

import java.time.LocalTime

/**
 * WorkingHours
 *
 * Defines start and end time for a working day.
 *
 * IMPORTANT:
 * - Time is evaluated AFTER TimeIntegrity validation.
 * - These hours express POLICY, not device time.
 */
data class WorkingHours(

    /** Start of working period (e.g. 08:00) */
    val start: LocalTime,

    /** End of working period (e.g. 16:00) */
    val end: LocalTime
)