package com.mahmoud.attendify.system.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.mahmoud.attendify.system.location.zones.LocationZonesPolicy
import com.mahmoud.attendify.system.location.zones.ZoneDecision
import com.mahmoud.attendify.system.location.zones.ZonePolicy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * LocationIntegrityGuard
 *
 * PURPOSE:
 * --------
 * This class is the ONLY authority responsible for:
 * - evaluating device location,
 * - evaluating network context,
 * - enforcing administrative location & zone policies,
 * - and producing a single deterministic verdict.
 *
 * CORE PRINCIPLES:
 * ----------------
 * - Context is measured, never trusted.
 * - Policies are applied, never guessed.
 * - Evidence is recorded, never inferred.
 *
 * This class does NOT handle:
 * - UI
 * - Justification text
 * - Persistence
 */
class LocationIntegrityGuard(
    private val context: Context,
    private val policy: LocationIntegrityPolicy,
    private val zonesPolicy: LocationZonesPolicy?,
    private val locationSource: LocationSource,
    private val anchorStorage: LocationAnchorStorage
) {

    /**
     * Evaluates the full location context for an attendance attempt.
     *
     * @return LocationIntegrityResult
     *         - Allowed(evidence)
     *         - Blocked(reason)
     */
    fun evaluate(): LocationIntegrityResult {

        /* ==================================================
         * 1️⃣ GLOBAL DISABLE (ADMIN POLICY)
         * ================================================== */
        if (!policy.locationVerificationEnabled) {
            return LocationIntegrityResult.Allowed(
                evidence = buildNoLocationEvidence()
            )
        }

        /* ==================================================
         * 2️⃣ NETWORK CONTEXT RESOLUTION & ENFORCEMENT
         * ================================================== */
        val networkContext = resolveNetworkContext()

        when (networkContext) {
            NetworkContext.WIFI ->
                if (!policy.allowWifi) {
                    return LocationIntegrityResult.Blocked(
                        reason = "Attendance via Wi‑Fi is disallowed by policy"
                    )
                }

            NetworkContext.CELLULAR ->
                if (!policy.allowCellular) {
                    return LocationIntegrityResult.Blocked(
                        reason = "Attendance via Cellular is disallowed by policy"
                    )
                }

            NetworkContext.OFFLINE ->
                if (!policy.allowOffline) {
                    return LocationIntegrityResult.Blocked(
                        reason = "Offline attendance is disallowed by policy"
                    )
                }
        }

        /* ==================================================
         * 3️⃣ ACQUIRE FRESH GPS LOCATION (TIME‑BOUNDED)
         * ================================================== */
        val snapshot = locationSource.getFreshLocation(
            timeoutSeconds = policy.locationFixTimeoutSeconds
        )

        if (snapshot == null) {
            return handleLocationTimeout(networkContext)
        }

        /* ==================================================
         * 4️⃣ STALE FIX DETECTION
         * ================================================== */
        val fixAgeMillis =
            abs(SystemClock.elapsedRealtime() - snapshot.elapsedRealtimeMillis)

        if (fixAgeMillis > policy.locationFixTimeoutSeconds * 1000) {
            return handleLocationTimeout(networkContext)
        }

        /* ==================================================
         * 5️⃣ TELEPORTATION DETECTION (CROSS‑SESSION)
         * ================================================== */
        var teleportDetected = false

        if (anchorStorage.hasLastLocation()) {
            val last = anchorStorage.loadLastLocation()

            val distanceMeters = haversineDistance(
                last.latitude,
                last.longitude,
                snapshot.latitude,
                snapshot.longitude
            )

            val deltaSeconds =
                abs(snapshot.elapsedRealtimeMillis - last.elapsedRealtimeMillis) / 1000.0

            if (deltaSeconds > 0) {
                val speedMps = distanceMeters / deltaSeconds
                teleportDetected = speedMps > policy.maxHumanSpeedMetersPerSecond
            }
        }

        if (teleportDetected && policy.blockOnTeleportation) {
            return LocationIntegrityResult.Blocked(
                reason = "Teleportation detected by policy"
            )
        }

        /* ==================================================
         * 6️⃣ MOCK LOCATION (RISK SIGNAL)
         * ================================================== */
        if (snapshot.isMock && policy.blockOnMockLocation) {
            return LocationIntegrityResult.Blocked(
                reason = "Mock location detected by policy"
            )
        }

        /* ==================================================
         * 7️⃣ ZONE EVALUATION
         * ================================================== */
        val zoneDecision = evaluateZones(
            snapshot.latitude,
            snapshot.longitude
        )

        if (zoneDecision != null) {
            when (zoneDecision.policy) {
                ZonePolicy.BLOCK ->
                    return LocationIntegrityResult.Blocked(
                        reason = "Blocked by zone policy"
                    )

                ZonePolicy.ALLOW_WITH_JUSTIFICATION -> Unit
                ZonePolicy.ALLOW -> Unit
            }
        }

        /* ==================================================
         * 8️⃣ SAVE LOCATION ANCHOR
         * ================================================== */
        anchorStorage.saveLastLocation(snapshot)

        /* ==================================================
         * 9️⃣ JUSTIFICATION AGGREGATION
         * ================================================== */
        val justificationRequired =
            (networkContext == NetworkContext.WIFI && policy.requireJustificationOnWifi) ||
                    (networkContext == NetworkContext.OFFLINE && policy.requireJustificationOnOffline) ||
                    (teleportDetected && policy.allowWithTamperEvidence) ||
                    (snapshot.isMock && policy.allowWithTamperEvidence) ||
                    (zoneDecision?.policy == ZonePolicy.ALLOW_WITH_JUSTIFICATION)

        /* ==================================================
         * 🔟 BUILD FORENSIC EVIDENCE
         * ================================================== */
        val evidence = LocationEvidence(
            latitude = if (snapshot.isMock) null else snapshot.latitude,
            longitude = if (snapshot.isMock) null else snapshot.longitude,
            accuracyMeters = snapshot.accuracyMeters,
            provider = snapshot.provider,

            isMockDetected = snapshot.isMock,
            isStale = false,
            teleportDetected = teleportDetected,

            distanceToAllowedZoneMeters = zoneDecision?.distanceMeters,
            zoneDecision = zoneDecision,

            policyDecision =
                if (teleportDetected || snapshot.isMock)
                    LocationDecision.ALLOWED_WITH_EVIDENCE
                else
                    LocationDecision.ALLOWED,

            justificationRequired = justificationRequired,
            networkContext = networkContext,
            timestampMillis = snapshot.timestampMillis,
            signature = LocationSigner.sign(snapshot.toString())
        )

        return LocationIntegrityResult.Allowed(evidence)
    }

    /* ==================================================
     * ZONE EVALUATION
     * ================================================== */
    private fun evaluateZones(
        latitude: Double,
        longitude: Double
    ): ZoneDecision? {

        val zones = zonesPolicy?.zones ?: return null

        for (zone in zones) {
            val distance = haversineDistance(
                zone.latitude,
                zone.longitude,
                latitude,
                longitude
            )

            if (distance <= zone.radiusMeters) {
                return ZoneDecision(
                    matchedZone = zone,
                    distanceMeters = distance,
                    policy = zone.insidePolicy
                )
            }
        }

        return ZoneDecision(
            matchedZone = null,
            distanceMeters = null,
            policy = zonesPolicy.defaultPolicyOutsideAllZones
        )
    }

    /* ==================================================
     * GPS TIMEOUT HANDLING
     * ================================================== */
    private fun handleLocationTimeout(
        networkContext: NetworkContext
    ): LocationIntegrityResult =
        when (policy.onLocationTimeout) {

            LocationTimeoutAction.BLOCK ->
                LocationIntegrityResult.Blocked(
                    reason = "GPS fix timeout exceeded"
                )

            LocationTimeoutAction.ALLOW_WITH_EVIDENCE ->
                LocationIntegrityResult.Allowed(
                    evidence = buildTimeoutEvidence(
                        false,
                        networkContext
                    )
                )

            LocationTimeoutAction.ALLOW_WITH_JUSTIFICATION ->
                LocationIntegrityResult.Allowed(
                    evidence = buildTimeoutEvidence(
                        true,
                        networkContext
                    )
                )
        }

    /* ==================================================
     * NETWORK CONTEXT RESOLUTION
     * ================================================== */
    private fun resolveNetworkContext(): NetworkContext {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
                ?: return NetworkContext.OFFLINE

        val network = cm.activeNetwork ?: return NetworkContext.OFFLINE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkContext.OFFLINE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetworkContext.WIFI

            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkContext.CELLULAR

            else ->
                NetworkContext.OFFLINE
        }
    }

    /* ==================================================
     * EVIDENCE HELPERS
     * ================================================== */
    private fun buildNoLocationEvidence(): LocationEvidence =
        LocationEvidence(
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            provider = "DISABLED_BY_POLICY",

            isMockDetected = false,
            isStale = false,
            teleportDetected = false,

            distanceToAllowedZoneMeters = null,
            zoneDecision = null,

            policyDecision = LocationDecision.ALLOWED,
            justificationRequired = false,
            networkContext = NetworkContext.OFFLINE,
            timestampMillis = System.currentTimeMillis(),
            signature = LocationSigner.sign("LOCATION_DISABLED")
        )

    private fun buildTimeoutEvidence(
        justificationRequired: Boolean,
        networkContext: NetworkContext
    ): LocationEvidence =
        LocationEvidence(
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            provider = "GPS_TIMEOUT",

            isMockDetected = false,
            isStale = true,
            teleportDetected = false,

            distanceToAllowedZoneMeters = null,
            zoneDecision = null,

            policyDecision = LocationDecision.ALLOWED_WITH_EVIDENCE,
            justificationRequired = justificationRequired,
            networkContext = networkContext,
            timestampMillis = System.currentTimeMillis(),
            signature = LocationSigner.sign("GPS_TIMEOUT")
        )

    /* ==================================================
     * DISTANCE CALCULATION
     * ================================================== */
    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                    kotlin.math.cos(Math.toRadians(lat1)) *
                    kotlin.math.cos(Math.toRadians(lat2)) *
                    kotlin.math.sin(dLon / 2) *
                    kotlin.math.sin(dLon / 2)

        return 2 *
                earthRadiusMeters *
                kotlin.math.atan2(
                    sqrt(a),
                    sqrt(1 - a)
                )
    }
}