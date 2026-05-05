package com.mahmoud.attendify.attendance.config

/**
 * ============================================================================
 * AttendancePolicyProvider
 * ============================================================================
 *
 * ROLE (SINGLE SOURCE OF TRUTH):
 * ----------------------------------------------------------------------------
 * This object is the **ONLY entry point** from which the rest of the
 * attendance system retrieves administrative policies.
 *
 * It plays a STRICTLY DELEGATING role:
 *  ✅ Exposes active policies
 *  ✅ Hides concrete policy construction details
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS OBJECT DOES:
 * ----------------------------------------------------------------------------
 * ✅ Provides explicit, non‑null policy instances
 * ✅ Centralizes policy retrieval
 * ✅ Makes policy usage auditable and traceable
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS OBJECT DOES NOT DO:
 * ----------------------------------------------------------------------------
 * ❌ It does NOT define rules itself
 * ❌ It does NOT enforce any policy
 * ❌ It does NOT transform or merge policies
 *
 * Policy DEFINITION lives in:
 * ---------------------------
 * DefaultAttendancePolicies
 *
 * Policy ENFORCEMENT lives in:
 * -----------------------------
 * - TimeIntegrityGuard
 * - LocationIntegrityGuard
 * - AttendanceUseCase
 *
 * ----------------------------------------------------------------------------
 * ARCHITECTURAL POSITION (ASCII MAP):
 * ----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────────────┐
 *   │ DefaultAttendancePolicies            │
 *   │  (Concrete policy definitions)       │
 *   └──────────────┬───────────────────────┘
 *                  │
 *   ┌──────────────▼───────────────────────┐
 *   │ AttendancePolicyProvider              │  ← THIS FILE
 *   │  (Policy exposure & indirection)      │
 *   └──────────────┬───────────────────────┘
 *                  │
 *   ┌──────────────▼───────────────────────┐
 *   │ Guards & UseCases                     │
 *   │  - TimeIntegrityGuard                 │
 *   │  - LocationIntegrityGuard             │
 *   │  - AttendanceUseCase                  │
 *   └──────────────────────────────────────┘
 *
 * ----------------------------------------------------------------------------
 * WHY THIS LAYER EXISTS:
 * ----------------------------------------------------------------------------
 * 1. Avoids hard dependencies on DefaultAttendancePolicies
 * 2. Allows future runtime overrides (Flutter / Backend)
 * 3. Makes policy changes globally visible and reviewable
 *
 * ----------------------------------------------------------------------------
 * PHASE‑1 NOTE:
 * ----------------------------------------------------------------------------
 * All policies returned here are:
 *  ✅ Explicit (never null)
 *  ✅ Conservative by default
 *  ✅ Aligned with Phase‑1 security goals
 */
object AttendancePolicyProvider {

    /**
     * ------------------------------------------------------------------------
     * TIME INTEGRITY POLICY
     * ------------------------------------------------------------------------
     *
     * Governs whether device time is acceptable for attendance.
     *
     * CONSUMERS:
     * ----------
     * - TimeIntegrityGuard
     */
    fun timePolicy() =
        DefaultAttendancePolicies.timePolicy()

    /**
     * ------------------------------------------------------------------------
     * LOCATION INTEGRITY POLICY
     * ------------------------------------------------------------------------
     *
     * Governs how raw location signals are evaluated
     * (GPS / Wi‑Fi / Cellular / Offline).
     *
     * CONSUMERS:
     * ----------
     * - LocationIntegrityGuard
     */
    fun locationPolicy() =
        DefaultAttendancePolicies.locationPolicy()

    /**
     * ------------------------------------------------------------------------
     * LOCATION ZONES POLICY
     * ------------------------------------------------------------------------
     *
     * Governs administrative / legal spatial rules.
     *
     * IMPORTANT DISTINCTION:
     * ---------------------
     * - LocationIntegrityPolicy  → technical trust
     * - LocationZonesPolicy      → business / legal constraints
     *
     * PHASE‑1 BEHAVIOR:
     * ----------------
     * - Zones are explicitly defined as EMPTY
     * - Default behavior is ALLOW
     *
     * This policy is STILL REQUIRED (non‑nullable),
     * because AttendanceUseCase is a FINAL AUTHORITY
     * that must never operate on null ambiguity.
     *
     * CONSUMERS:
     * ----------
     * - AttendanceUseCase (via ZoneEvaluator)
     */
    fun zonePolicy() =
        DefaultAttendancePolicies.zonePolicy()

    /**
     * ------------------------------------------------------------------------
     * WORKING TIME POLICY (HR LAYER)
     * ------------------------------------------------------------------------
     *
     * Governs organizational working hours and holidays.
     *
     * CONSUMERS:
     * ----------
     * - AttendanceUseCase
     */
    fun workingTimePolicy() =
        DefaultAttendancePolicies.workingTimePolicy()
}