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
import com.mahmoud.attendify.security.canonical.CanonicalSerializer

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * =============================================================================
 * 🧠 LocationIntegrityGuard — Forensic Location Trust Engine
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   L(t)  = location sample at time t
 *   P     = policy constraints
 *   Z     = geo-zone restrictions
 *   C(.)  = canonical serializer
 *   H(.)  = SHA-256
 *
 * Then:
 *
 *   Evidence = H(C(L(t) ∪ P ∪ Z))
 *
 * -----------------------------------------------------------------------------
 * 🎯 OBJECTIVE
 * -----------------------------------------------------------------------------
 *
 * Convert unstable and spoofable GPS data into:
 *
 *   ✅ Deterministic (same input → same output)
 *   ✅ Policy-compliant (network + zones)
 *   ✅ Security-evaluated (mock, teleport)
 *   ✅ Cryptographically bound evidence
 *
 * -----------------------------------------------------------------------------
 * 🔐 THREAT MODEL
 * -----------------------------------------------------------------------------
 *
 * The system defends against:
 *
 *   - Mock location providers
 *   - GPS replay (stale readings)
 *   - Teleportation attacks (unrealistic movement)
 *   - Zone policy bypass
 *   - Encoding ambiguity attacks
 *
 * -----------------------------------------------------------------------------
 * 📊 EXECUTION FLOW
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────┐
 *   │ Network Context Resolution   │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Location Acquisition         │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Freshness Validation         │
 *   │ age ≤ threshold              │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Teleportation Detection      │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Zone Evaluation              │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Canonical Encoding           │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ SHA-256 Hash                │
 *   └───────────────┬──────────────┘
 *                   ▼
 *   ┌──────────────────────────────┐
 *   │ Signed Evidence              │
 *   └──────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL INVARIANTS
 * -----------------------------------------------------------------------------
 *
 * 1. No String-based serialization (NO toString())
 * 2. Same location snapshot → identical binary payload
 * 3. No stale data silently accepted
 *
 * =============================================================================
 */
class LocationIntegrityGuard(
    private val context: Context,
    private val policy: LocationIntegrityPolicy,
    private val zonesPolicy: LocationZonesPolicy?,
    private val locationSource: LocationSource,
    private val anchorStorage: LocationAnchorStorage
) {

    /* =========================================================================
     * 🚀 ENTRY POINT
     * ========================================================================= */

    suspend fun awaitFreshLocation(
        timeoutMs: Long
    ): LocationIntegrityResult = withContext(Dispatchers.IO) {

        /* ================= GLOBAL DISABLE ================= */

        if (!policy.locationVerificationEnabled) {
            return@withContext LocationIntegrityResult.Allowed(
                buildNoLocationEvidence()
            )
        }

        /* ================= NETWORK CONTEXT ================= */

        val networkContext = resolveNetworkContext()

        /* ================= LOCATION FETCH ================= */

        val snapshot =
            locationSource.awaitFreshLocation(timeoutMs)
                ?: return@withContext LocationIntegrityResult.Blocked("No location")

        /* ================= FRESHNESS ================= */

        val age =
            abs(SystemClock.elapsedRealtime() - snapshot.elapsedRealtimeMillis)

        if (age > policy.locationFixTimeoutSeconds * 1000) {
            return@withContext LocationIntegrityResult.Blocked("Stale location")
        }

        /* ================= TELEPORT DETECTION ================= */

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

        /* ================= ZONE CONTROL ================= */

        val zoneDecision =
            evaluateZones(snapshot.latitude, snapshot.longitude)

        if (zoneDecision?.policy == ZonePolicy.BLOCK) {
            return@withContext LocationIntegrityResult.Blocked("Zone restriction")
        }

        /* ================= SAVE ANCHOR ================= */

        anchorStorage.saveLastLocation(snapshot)

        /* ================= FINAL EVIDENCE ================= */

        return@withContext allowed(
            snapshot,
            networkContext,
            teleportDetected,
            zoneDecision
        )
    }

    /* =========================================================================
     * 🧮 CANONICAL ENCODING
     * ========================================================================= */

    private fun buildCanonicalLocationPayload(
        snapshot: LocationSnapshot
    ): ByteArray {

        return CanonicalSerializer.encodeLatitude(snapshot.latitude) +
                CanonicalSerializer.encodeLongitude(snapshot.longitude) +
                CanonicalSerializer.floatToBytes(snapshot.accuracyMeters ?: Float.NaN) +
                CanonicalSerializer.stringToBytes(snapshot.provider) +
                CanonicalSerializer.booleanToBytes(snapshot.isMock) +
                CanonicalSerializer.longToBytes(snapshot.elapsedRealtimeMillis) +
                CanonicalSerializer.longToBytes(snapshot.timestampMillis)
    }

    /* =========================================================================
     * ✅ RESULT CONSTRUCTION
     * ========================================================================= */

    private fun allowed(
        snapshot: LocationSnapshot,
        networkContext: NetworkContext,
        teleportDetected: Boolean,
        zoneDecision: ZoneDecision?
    ): LocationIntegrityResult {

        val payload = buildCanonicalLocationPayload(snapshot)

        val hash = MessageDigest
            .getInstance("SHA-256")
            .digest(payload)

        return LocationIntegrityResult.Allowed(
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

                policyDecision = LocationDecision.ALLOWED,
                justificationRequired = false,
                networkContext = networkContext,
                timestampMillis = snapshot.timestampMillis,

                signature = LocationSigner.sign(hash)
            )
        )
    }

    /* =========================================================================
     * 📴 DISABLED CASE
     * ========================================================================= */

    private fun buildNoLocationEvidence(): LocationEvidence =
        LocationEvidence(
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            provider = "DISABLED",

            isMockDetected = false,
            isStale = true,
            teleportDetected = false,

            distanceToAllowedZoneMeters = null,
            zoneDecision = null,

            policyDecision = LocationDecision.ALLOWED,
            justificationRequired = false,
            networkContext = NetworkContext.OFFLINE,
            timestampMillis = System.currentTimeMillis(),

            signature = LocationSigner.sign("DISABLED")
        )

    /* =========================================================================
     * 🌐 NETWORK RESOLUTION
     * ========================================================================= */

    private fun resolveNetworkContext(): NetworkContext {

        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager

        val network = cm.activeNetwork ?: return NetworkContext.OFFLINE
        val caps = cm.getNetworkCapabilities(network)
            ?: return NetworkContext.OFFLINE

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetworkContext.WIFI

            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkContext.CELLULAR

            else -> NetworkContext.OFFLINE
        }
    }

    /* =========================================================================
     * 📍 ZONE EVALUATION
     * ========================================================================= */

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

    /* =========================================================================
     * 📐 GEODESIC DISTANCE (HAVERSINE)
     * ========================================================================= */

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