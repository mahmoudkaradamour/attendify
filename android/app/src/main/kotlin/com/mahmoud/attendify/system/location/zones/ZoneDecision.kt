package com.mahmoud.attendify.system.location.zones

/**
 * ZoneDecision
 *
 * Result of evaluating a location against zones.
 */
data class ZoneDecision(

    /** Zone that matched (null if no zone matched) */
    val matchedZone: LocationZone?,

    /** Distance to matched zone center (meters) */
    val distanceMeters: Double?,

    /** Final policy result */
    val policy: ZonePolicy
)