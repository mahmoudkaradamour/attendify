package com.mahmoud.attendify.system.time

/**
 * Administrative policy controlling time rules.
 */
data class TimeIntegrityPolicy(
    val requiredTimeZone: String,
    val driftToleranceMillis: Long = 5_000,
    val allowPostBootRecords: Boolean = true,
    val requireInitialAnchor: Boolean = true
)
