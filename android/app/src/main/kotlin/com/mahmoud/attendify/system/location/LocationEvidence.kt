package com.mahmoud.attendify.system.location

import com.mahmoud.attendify.system.location.zones.ZoneDecision

/**
 * LocationEvidence
 *
 * Immutable, forensic‑grade record describing
 * the EXACT location and connectivity context
 * under which an attendance attempt occurred.
 *
 * FUNDAMENTAL RULES:
 * ------------------
 * - Evidence NEVER decides.
 * - Evidence NEVER enforces.
 * - Evidence NEVER alters behavior.
 *
 * Evidence ONLY describes:
 * - what signals were observed,
 * - what policies were applied,
 * - and what contextual facts existed.
 *
 * Any acceptance or rejection decision
 * is taken elsewhere and merely RECORDED here.
 */
data class LocationEvidence(

    /* ==================================================
     * RAW LOCATION SIGNALS
     * ================================================== */

    /**
     * Latitude reported by the location subsystem.
     *
     * Can be NULL in cases such as:
     * - GPS timeout
     * - Location disabled by policy
     * - Mock / tampered location where coordinates
     *   must NOT be trusted.
     */
    val latitude: Double?,

    /**
     * Longitude reported by the location subsystem.
     *
     * Subject to the same NULL semantics as latitude.
     */
    val longitude: Double?,

    /**
     * Accuracy radius in meters as reported by the provider.
     *
     * NULL when:
     * - No fix was acquired
     * - Location is intentionally ignored
     */
    val accuracyMeters: Float?,

    /**
     * Name of the provider that delivered the location.
     *
     * Examples:
     * - "gps"
     * - "DISABLED_BY_POLICY"
     * - "GPS_TIMEOUT"
     *
     * This is recorded for audit purposes ONLY.
     */
    val provider: String,

    /* ==================================================
     * INTEGRITY & TAMPER SIGNALS
     * ================================================== */

    /**
     * Indicates whether Android reported this
     * location as originating from a mock provider.
     *
     * IMPORTANT:
     * - This flag is treated as a RISK SIGNAL,
     *   NOT absolute proof of tampering.
     */
    val isMockDetected: Boolean,

    /**
     * Indicates that the location fix was considered stale.
     *
     * Typical causes:
     * - Delayed delivery
     * - Replay of old cached fix
     * - GPS fix older than policy timeout
     */
    val isStale: Boolean,

    /**
     * Indicates detection of physically impossible movement
     * across attendance sessions (cross‑session teleport).
     *
     * Computed using:
     * - last known valid anchor
     * - time delta
     * - administrative maxHumanSpeed policy
     */
    val teleportDetected: Boolean,

    /* ==================================================
     * ZONE‑BASED POLICY CONTEXT
     * ================================================== */

    /**
     * Distance in meters to the matched zone center.
     *
     * NULL when:
     * - No zones are configured
     * - Location is unavailable
     * - Location was discarded
     *
     * This value is purely informational and forensic.
     */
    val distanceToAllowedZoneMeters: Double?,

    /**
     * Result of evaluating the location
     * against administratively defined zones.
     *
     * Contains:
     * - Which zone (if any) matched
     * - Distance to zone
     * - Zone policy applied
     *
     * NULL when zones are not enabled.
     */
    val zoneDecision: ZoneDecision?,

    /* ==================================================
     * ADMINISTRATIVE DECISIONS
     * ================================================== */

    /**
     * Final location‑related policy verdict.
     *
     * This does NOT determine attendance alone,
     * but explains HOW location influenced the outcome.
     *
     * Examples:
     * - ALLOWED
     * - ALLOWED_WITH_EVIDENCE
     */
    val policyDecision: LocationDecision,

    /**
     * Indicates whether this attendance attempt
     * REQUIRES employee justification according
     * to applied location / network / zone policies.
     *
     * IMPORTANT:
     * - This flag does NOT store the justification.
     * - It only signals that justification is mandatory.
     */
    val justificationRequired: Boolean,

    /* ==================================================
     * NETWORK CONTEXT
     * ================================================== */

    /**
     * Network connectivity context at the moment
     * of attendance attempt.
     *
     * Examples:
     * - WIFI
     * - CELLULAR
     * - OFFLINE
     *
     * Network context is NEVER trusted,
     * but always recorded.
     */
    val networkContext: NetworkContext,

    /* ==================================================
     * FINAL FORENSIC METADATA
     * ================================================== */

    /**
     * Wall‑clock timestamp at which the evidence
     * record was created.
     *
     * Used for:
     * - ordering
     * - correlation
     * - legal auditing
     */
    val timestampMillis: Long,

    /**
     * Cryptographic signature binding all fields
     * of this evidence record.
     *
     * Ensures:
     * - immutability
     * - non‑repudiation
     * - tamper detection
     */
    val signature: String
)