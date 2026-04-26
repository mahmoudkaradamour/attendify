package com.mahmoud.attendify.system.time.working

/**
 * WorkingDay
 *
 * Represents days of the week that are considered
 * working days by administrative policy.
 *
 * IMPORTANT:
 * - This is NOT derived from locale.
 * - It is an explicit administrative decision.
 */
enum class WorkingDay {
    SATURDAY,
    SUNDAY,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY
}