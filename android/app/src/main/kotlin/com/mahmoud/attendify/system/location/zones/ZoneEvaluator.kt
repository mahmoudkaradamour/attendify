package com.mahmoud.attendify.system.location.zones

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ZoneEvaluator
 *
 * ============================================================================
 * ROLE (Architectural Responsibility):
 * ============================================================================
 * Evaluates a trusted location (latitude / longitude / accuracy)
 * against an administrative LocationZonesPolicy and produces
 * a single, deterministic ZoneDecision.
 *
 * This class answers ONE question only:
 *
 *   "Given this physical position, which zone policy applies?"
 *
 * ============================================================================
 * IMPORTANT ARCHITECTURAL DECISIONS:
 * ============================================================================
 * ✅ NO dependency on android.location.Location
 * ✅ Pure mathematical evaluation only
 * ✅ Domain‑safe and testable
 *
 * Location integrity (mock, stale, teleportation) is assumed
 * to have been validated BEFORE calling this class.
 *
 * ============================================================================
 * WHAT THIS CLASS DOES NOT DO:
 * ============================================================================
 * ❌ Fetch GPS
 * ❌ Validate location integrity
 * ❌ Decide attendance acceptance
 *
 * Those responsibilities belong to:
 * - LocationIntegrityGuard
 * - AttendanceUseCase
 */
object ZoneEvaluator {

    /**
     * evaluate
     *
     * ------------------------------------------------------------------------
     * Evaluates the current trusted coordinates against configured zones.
     *
     * @param latitude          Trusted latitude
     * @param longitude         Trusted longitude
     * @param accuracyMeters    Location accuracy radius (meters)
     * @param zonesPolicy       Administrative zones policy
     *
     * @return ZoneDecision describing matched zone and resulting policy
     */
    fun evaluate(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        zonesPolicy: LocationZonesPolicy
    ): ZoneDecision {

        var bestMatch: LocationZone? = null
        var bestDistance: Double? = null

        /**
         * We evaluate ALL zones and pick the closest valid one.
         *
         * Why closest?
         * ------------
         * - Avoid ambiguity when zones overlap
         * - Deterministic behavior
         */
        for (zone in zonesPolicy.zones) {

            val distanceToCenter =
                distanceMeters(
                    latitude,
                    longitude,
                    zone.latitude,
                    zone.longitude
                )

            /**
             * ================================================================
             * GPS JITTER / HYSTERESIS HANDLING
             * ================================================================
             *
             * Naive rule:
             *   distance <= radius
             *
             * This is WRONG in real-world GPS.
             *
             * Correct rule:
             * --------------
             * Consider the user OUTSIDE the zone ONLY IF:
             *
             *   distance - accuracy > radius
             *
             * Otherwise:
             * - The user is inside
             * - Or exactly on the boundary
             * - Or measurement uncertainty overlaps the zone
             *
             * This prevents:
             *  ✅ False negatives
             *  ✅ Boundary flickering
             *  ✅ Employee frustration
             */
            val effectivelyInside =
                distanceToCenter - accuracyMeters <= zone.radiusMeters

            if (effectivelyInside) {
                if (
                    bestDistance == null ||
                    distanceToCenter < bestDistance
                ) {
                    bestMatch = zone
                    bestDistance = distanceToCenter
                }
            }
        }

        /**
         * ================================================================
         * FINAL POLICY RESOLUTION
         * ================================================================
         */
        return if (bestMatch != null) {
            ZoneDecision(
                matchedZone = bestMatch,
                distanceMeters = bestDistance,
                policy = bestMatch.insidePolicy
            )
        } else {
            /**
             * No zone matched.
             * Apply the global default policy.
             */
            ZoneDecision(
                matchedZone = null,
                distanceMeters = null,
                policy = zonesPolicy.defaultPolicyOutsideAllZones
            )
        }
    }

    /**
     * distanceMeters
     *
     * ------------------------------------------------------------------------
     * Computes approximate planar distance between two coordinates.
     *
     * DESIGN CHOICE:
     * --------------
     * - Planar approximation
     * - Fast
     * - Deterministic
     *
     * This is sufficient because:
     * - Zones are small (20m – 500m)
     * - Sub-meter geodesic precision is irrelevant here
     */
    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon =
            111_320.0 * kotlin.math.cos(Math.toRadians(lat1))

        val dLat = (lat2 - lat1) * metersPerDegreeLat
        val dLon = (lon2 - lon1) * metersPerDegreeLon

        return sqrt(dLat.pow(2) + dLon.pow(2))
    }
}