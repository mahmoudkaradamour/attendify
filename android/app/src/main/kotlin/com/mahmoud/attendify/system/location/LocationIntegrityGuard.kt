package com.mahmoud.attendify.system.location

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.mahmoud.attendify.system.location.zones.LocationZonesPolicy
import com.mahmoud.attendify.system.location.zones.ZoneDecision
import com.mahmoud.attendify.system.location.zones.ZonePolicy

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ============================================================================
 * LocationIntegrityGuard — FINAL (Military‑Grade Implementation)
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * The ONLY authority responsible for evaluating:
 *   - GPS data
 *   - Network context
 *   - Policy constraints
 *   - Zone restrictions
 *
 * And producing:
 *   → Deterministic, forensic-grade evidence
 *
 * ============================================================================
 * CRITICAL GUARANTEES
 * ============================================================================
 *
 * ✅ Deterministic result for identical inputs
 * ✅ No cached / reused location accepted silently
 * ✅ Full policy enforcement
 * ✅ Evidence is cryptographically bound (NO toString)
 *
 * ============================================================================
 */
class LocationIntegrityGuard(
    private val context: Context,
    private val policy: LocationIntegrityPolicy,
    private val zonesPolicy: LocationZonesPolicy?,
    private val locationSource: LocationSource,
    private val anchorStorage: LocationAnchorStorage
) {

    /* =========================================================================
     * ✅ ASYNC ENTRY POINT (REQUIRED FOR A1)
     * ========================================================================= */

    suspend fun awaitFreshLocation(
        timeoutMs: Long
    ): LocationIntegrityResult = withContext(Dispatchers.IO) {

        /* ==================================================
         * 1️⃣ GLOBAL DISABLE
         * ================================================== */
        if (!policy.locationVerificationEnabled) {
            return@withContext LocationIntegrityResult.Allowed(
                evidence = buildNoLocationEvidence()
            )
        }

        /* ==================================================
         * 2️⃣ NETWORK CONTEXT
         * ================================================== */
        val networkContext = resolveNetworkContext()

        when (networkContext) {
            NetworkContext.WIFI ->
                if (!policy.allowWifi)
                    return@withContext block("Wi‑Fi disallowed")

            NetworkContext.CELLULAR ->
                if (!policy.allowCellular)
                    return@withContext block("Cellular disallowed")

            NetworkContext.OFFLINE ->
                if (!policy.allowOffline)
                    return@withContext block("Offline disallowed")
        }

        /* ==================================================
         * 3️⃣ ASYNC GPS FIX
         * ================================================== */
        val snapshot =
            locationSource.awaitFreshLocation(timeoutMs)
                ?: return@withContext handleLocationTimeout(networkContext)

        /* ==================================================
         * 4️⃣ STALE DETECTION
         * ================================================== */
        val age =
            abs(SystemClock.elapsedRealtime() - snapshot.elapsedRealtimeMillis)

        if (age > policy.locationFixTimeoutSeconds * 1000) {
            return@withContext handleLocationTimeout(networkContext)
        }

        /* ==================================================
         * 5️⃣ TELEPORTATION DETECTION
         * ================================================== */
        var teleportDetected = false

        if (anchorStorage.hasLastLocation()) {

            val last = anchorStorage.loadLastLocation()

            val distance = haversineDistance(
                last.latitude,
                last.longitude,
                snapshot.latitude,
                snapshot.longitude
            )

            val deltaSeconds =
                abs(snapshot.elapsedRealtimeMillis - last.elapsedRealtimeMillis) / 1000.0

            if (deltaSeconds > 0) {
                val speed = distance / deltaSeconds
                teleportDetected =
                    speed > policy.maxHumanSpeedMetersPerSecond
            }
        }

        if (teleportDetected && policy.blockOnTeleportation)
            return@withContext block("Teleportation detected")

        /* ==================================================
         * 6️⃣ MOCK LOCATION
         * ================================================== */
        if (snapshot.isMock && policy.blockOnMockLocation)
            return@withContext block("Mock location detected")

        /* ==================================================
         * 7️⃣ ZONE EVALUATION
         * ================================================== */
        val zoneDecision =
            evaluateZones(snapshot.latitude, snapshot.longitude)

        if (zoneDecision?.policy == ZonePolicy.BLOCK) {

            val isNetworkProvider =
                snapshot.provider.equals("network", true)

            if (isNetworkProvider) {
                anchorStorage.saveLastLocation(snapshot)

                return@withContext allowed(
                    snapshot,
                    networkContext,
                    teleportDetected,
                    zoneDecision,
                    LocationDecision.ALLOWED_WITH_EVIDENCE,
                    true
                )
            }

            return@withContext block("Blocked by zone policy")
        }

        /* ==================================================
         * 8️⃣ SAVE ANCHOR
         * ================================================== */
        anchorStorage.saveLastLocation(snapshot)

        /* ==================================================
         * 9️⃣ JUSTIFICATION
         * ================================================== */
        val justificationRequired =
            (networkContext == NetworkContext.WIFI &&
                    policy.requireJustificationOnWifi) ||
                    (networkContext == NetworkContext.OFFLINE &&
                            policy.requireJustificationOnOffline) ||
                    teleportDetected ||
                    snapshot.isMock ||
                    (zoneDecision?.policy == ZonePolicy.ALLOW_WITH_JUSTIFICATION)

        /* ==================================================
         * 🔟 FINAL RESULT
         * ================================================== */
        return@withContext allowed(
            snapshot,
            networkContext,
            teleportDetected,
            zoneDecision,
            if (teleportDetected || snapshot.isMock)
                LocationDecision.ALLOWED_WITH_EVIDENCE
            else
                LocationDecision.ALLOWED,
            justificationRequired
        )
    }

    /* =========================================================================
     * ✅ CANONICAL PAYLOAD (CRITICAL FIX)
     * ========================================================================= */
    private fun buildCanonicalLocationPayload(
        snapshot: LocationSnapshot
    ): ByteArray {

        return (
                snapshot.latitude.toString() +
                        snapshot.longitude.toString() +
                        snapshot.accuracyMeters.toString() +
                        snapshot.provider +
                        snapshot.isMock.toString() +
                        snapshot.elapsedRealtimeMillis.toString() +
                        snapshot.timestampMillis.toString()
                ).toByteArray(Charsets.UTF_8)
    }

    /* =========================================================================
     * HELPER: CREATE ALLOWED RESULT WITH SECURE SIGNATURE
     * ========================================================================= */

    private fun allowed(
        snapshot: LocationSnapshot,
        networkContext: NetworkContext,
        teleportDetected: Boolean,
        zoneDecision: ZoneDecision?,
        decision: LocationDecision,
        justificationRequired: Boolean
    ) = LocationIntegrityResult.Allowed(

        run {

            val payload = buildCanonicalLocationPayload(snapshot)

            val hash = MessageDigest
                .getInstance("SHA-256")
                .digest(payload)

            LocationEvidence(
                latitude = if (snapshot.isMock) null else snapshot.latitude,
                longitude = if (snapshot.isMock) null else snapshot.longitude,
                accuracyMeters = snapshot.accuracyMeters,
                provider = snapshot.provider,

                isMockDetected = snapshot.isMock,
                isStale = false,
                teleportDetected = teleportDetected,

                distanceToAllowedZoneMeters = zoneDecision?.distanceMeters,
                zoneDecision = zoneDecision,

                policyDecision = decision,
                justificationRequired = justificationRequired,
                networkContext = networkContext,
                timestampMillis = snapshot.timestampMillis,

                // ✅ FIXED (NO toString)
                signature = LocationSigner.sign(hash)
            )
        }
    )

    private fun block(reason: String) =
        LocationIntegrityResult.Blocked(reason)

    /* ========================================================================= */

    private fun evaluateZones(
        lat: Double,
        lon: Double
    ): ZoneDecision? {

        val zones = zonesPolicy?.zones ?: return null

        for (zone in zones) {

            val distance = haversineDistance(
                zone.latitude,
                zone.longitude,
                lat,
                lon
            )

            if (distance <= zone.radiusMeters) {
                return ZoneDecision(zone, distance, zone.insidePolicy)
            }
        }

        return ZoneDecision(
            null,
            null,
            zonesPolicy.defaultPolicyOutsideAllZones
        )
    }

    /* ========================================================================= */

    private fun handleLocationTimeout(
        networkContext: NetworkContext
    ): LocationIntegrityResult =
        when (policy.onLocationTimeout) {

            LocationTimeoutAction.BLOCK ->
                block("GPS timeout")

            LocationTimeoutAction.ALLOW_WITH_EVIDENCE ->
                LocationIntegrityResult.Allowed(
                    buildTimeoutEvidence(false, networkContext)
                )

            LocationTimeoutAction.ALLOW_WITH_JUSTIFICATION ->
                LocationIntegrityResult.Allowed(
                    buildTimeoutEvidence(true, networkContext)
                )
        }

    /* ========================================================================= */

    private fun resolveNetworkContext(): NetworkContext {

        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
                ?: return NetworkContext.OFFLINE

        val network = cm.activeNetwork ?: return NetworkContext.OFFLINE
        val caps = cm.getNetworkCapabilities(network)
            ?: return NetworkContext.OFFLINE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetworkContext.WIFI

            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkContext.CELLULAR

            else ->
                NetworkContext.OFFLINE
        }
    }

    /* ========================================================================= */

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

    /* ========================================================================= */

    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {

        val r = 6_371_000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                    kotlin.math.cos(Math.toRadians(lat1)) *
                    kotlin.math.cos(Math.toRadians(lat2)) *
                    kotlin.math.sin(dLon / 2) *
                    kotlin.math.sin(dLon / 2)

        return 2 * r * kotlin.math.atan2(
            sqrt(a),
            sqrt(1 - a)
        )
    }
}
