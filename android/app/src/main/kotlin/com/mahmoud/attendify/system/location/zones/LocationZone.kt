package com.mahmoud.attendify.system.location.zones

/**
 * LocationZone
 *
 * Represents an administratively defined physical location
 * (e.g. office, branch, workspace).
 *
 * IMPORTANT:
 * - This is NOT a geofence.
 * - This is a policy anchor.
 *
 * A location attempt is evaluated against one or more zones
 * to decide attendance behavior.
 */
data class LocationZone(

    /** Stable identifier (used for audit & backend correlation) */
    val id: String,

    /** Human-readable name (e.g. "Aleppo HQ") */
    val name: String,

    /** Zone center latitude */
    val latitude: Double,

    /** Zone center longitude */
    val longitude: Double,

    /** Allowed radius in meters */
    val radiusMeters: Double,

    /** Policy applied when user is INSIDE this zone */
    val insidePolicy: ZonePolicy,

    /** Policy applied when user is OUTSIDE this zone */
    val outsidePolicy: ZonePolicy
)