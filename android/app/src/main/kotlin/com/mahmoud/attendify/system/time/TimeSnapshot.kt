package com.mahmoud.attendify.system.time

/**
 * Immutable snapshot of all time-related signals at a single moment.
 */
data class TimeSnapshot(
    val wallClockMillis: Long,
    val elapsedRealtimeMillis: Long,
    val uptimeMillis: Long,
    val bootId: String,
    val timeZoneId: String
)
