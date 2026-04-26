package com.mahmoud.attendify.system.location.zones

/**
 * LocationZonesPolicy
 *
 * Top-level administrative policy that defines:
 * - Which zones are valid for a user
 * - What happens if no zone matches
 */
data class LocationZonesPolicy(

    /** Zones assigned to this user or group */
    val zones: List<LocationZone>,

    /**
     * Policy applied when location does NOT match
     * any defined zone.
     */
    val defaultPolicyOutsideAllZones: ZonePolicy
)