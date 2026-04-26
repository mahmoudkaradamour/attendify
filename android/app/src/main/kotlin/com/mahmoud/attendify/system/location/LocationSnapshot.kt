package com.mahmoud.attendify.system.location

/**
 * Immutable snapshot of a freshly acquired location.
 */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val isMock: Boolean,
    val elapsedRealtimeMillis: Long,
    val timestampMillis: Long
)