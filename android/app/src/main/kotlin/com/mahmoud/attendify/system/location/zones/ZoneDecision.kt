package com.mahmoud.attendify.system.location.zones

/**
 * ============================================================================
 * ZoneDecision — FINAL (Forensic‑Grade Data Model)
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * Represents the deterministic outcome of evaluating a geographic coordinate
 * against a configured set of administrative zones.
 *
 * This class is part of:
 *   - LocationIntegrityGuard
 *   - Forensic evidence generation
 *   - Canonical payload construction (indirectly)
 *
 * ============================================================================
 * WHY THIS CLASS EXISTS
 * ============================================================================
 *
 * In high-assurance systems:
 *   - Raw latitude/longitude is NOT enough
 *   - Administrative meaning MUST be derived
 *
 * Examples:
 *   - Inside company HQ ✅
 *   - Outside allowed area ❌
 *   - Within restricted zone ⚠️
 *
 * ZoneDecision:
 *   → converts raw coordinates into POLICY MEANING
 *
 * ============================================================================
 * FORENSIC SIGNIFICANCE
 * ============================================================================
 *
 * ZoneDecision is included in evidence (indirectly via LocationEvidence)
 * and therefore:
 *
 * ✅ Must be deterministic
 * ✅ Must have stable serialization
 * ✅ Must not depend on runtime state
 *
 * This ensures:
 *   - Reproducible decisions
 *   - Audit traceability
 *   - Legal defensibility
 *
 * ============================================================================
 * SECURITY PROPERTIES
 * ============================================================================
 *
 * ✅ Pure data object (no behavior)
 * ✅ Immutable (data class)
 * ✅ No side effects
 * ✅ No external dependencies
 *
 * ============================================================================
 * DESIGN CONSTRAINTS
 * ============================================================================
 *
 * - Nullability is intentional:
 *     matchedZone      → null ⇒ no zone matched
 *     distanceMeters   → null ⇒ distance not meaningful
 *
 * - All policy meaning is carried in:
 *     ZonePolicy
 *
 * ============================================================================
 */
data class ZoneDecision(

    /**
     * The zone that matched the given coordinates.
     *
     * Null indicates:
     *   - No zone matched
     *   - Evaluation fell back to default policy
     *
     * IMPORTANT:
     * ------------------------------------------------------------------------
     * This field is NOT used directly for hashing.
     * Only derived fields (e.g. policy, distance) are serialized downstream.
     *
     * This prevents:
     *   - Object reference instability
     *   - Serialization ambiguity
     */
    val matchedZone: LocationZone?,

    /**
     * Distance between the evaluated point and the center of the matched zone.
     *
     * Unit:
     *   - Meters (Double precision)
     *
     * Null indicates:
     *   - No zone matched
     *
     * FORENSIC PURPOSE:
     * ------------------------------------------------------------------------
     * Provides measurable context for:
     *   - Boundary proximity
     *   - Dispute resolution ("I was near the office")
     *   - Policy enforcement explanation
     *
     * Example:
     *   "User was 12.3 meters outside restricted zone"
     */
    val distanceMeters: Double?,

    /**
     * Final policy decision for this location.
     *
     * This is the MOST IMPORTANT field in this class.
     *
     * Examples:
     *   - ALLOW
     *   - BLOCK
     *   - ALLOW_WITH_JUSTIFICATION
     *
     * SECURITY NOTE:
     * ------------------------------------------------------------------------
     * This field is:
     *   ✅ Always included in canonical serialization
     *   ✅ Used in downstream hash generation
     *
     * Therefore:
     *   → Any change to policy immediately changes the evidence hash
     */
    val policy: ZonePolicy
)
