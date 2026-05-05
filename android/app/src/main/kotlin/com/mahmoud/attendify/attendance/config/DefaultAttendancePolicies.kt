package com.mahmoud.attendify.attendance.config

import com.mahmoud.attendify.system.time.TimeIntegrityPolicy
import com.mahmoud.attendify.system.location.LocationIntegrityPolicy
import com.mahmoud.attendify.system.location.LocationTimeoutAction
import com.mahmoud.attendify.system.location.zones.LocationZonesPolicy
import com.mahmoud.attendify.system.location.zones.ZonePolicy
import com.mahmoud.attendify.system.time.working.*
import java.time.LocalTime
import java.time.ZoneId

/**
 * ============================================================================
 * DefaultAttendancePolicies
 * ============================================================================
 *
 * ROLE (ADMINISTRATIVE POLICY DEFINITION):
 * ----------------------------------------------------------------------------
 * This object defines the **DEFAULT administrative policies**
 * governing attendance behavior.
 *
 * These policies represent:
 *  ✅ Explicit business / legal decisions
 *  ✅ Human‑approved defaults
 *  ✅ Conservative, audit‑safe configuration
 *
 * ----------------------------------------------------------------------------
 * IMPORTANT ARCHITECTURAL RULE:
 * ----------------------------------------------------------------------------
 * This object:
 *  ✅ Defines WHAT is allowed (policy)
 *  ❌ Does NOT enforce HOW it is enforced
 *
 * Enforcement is handled by Guards and UseCases, NOT here.
 *
 * ----------------------------------------------------------------------------
 * POSITION IN ARCHITECTURE (ASCII MAP):
 * ----------------------------------------------------------------------------
 *
 *   ┌──────────────────────────────┐
 *   │ DefaultAttendancePolicies    │  ← THIS FILE
 *   └──────────────┬───────────────┘
 *                  │ provides policies
 *   ┌──────────────▼───────────────┐
 *   │ AttendancePolicyProvider     │
 *   └──────────────┬───────────────┘
 *                  │ injects policies
 *   ┌──────────────▼───────────────┐
 *   │ Guards / UseCases            │
 *   │  (Time, Location, Attendance│
 *   └──────────────────────────────┘
 *
 * ----------------------------------------------------------------------------
 * PHASE‑1 NOTE:
 * ----------------------------------------------------------------------------
 * All defaults here are chosen to:
 *  - Be explicit
 *  - Avoid implicit nulls
 *  - Keep Phase‑1 behavior predictable
 */
object DefaultAttendancePolicies {

    /**
     * ------------------------------------------------------------------------
     * TIME INTEGRITY POLICY
     * ------------------------------------------------------------------------
     *
     * Governs whether device time is considered trustworthy.
     *
     * CONCEPTUAL MODEL:
     * -----------------
     * - Device time is NOT trusted by default
     * - Trust is established via anchors + drift analysis
     *
     * USED BY:
     * --------
     * - TimeIntegrityGuard
     *
     * NOT USED BY:
     * ------------
     * - UI
     * - AttendanceRuntimeOrchestrator
     */
    fun timePolicy(): TimeIntegrityPolicy =
        TimeIntegrityPolicy(
            /**
             * Corporate / legal timezone.
             * Device timezone is ignored.
             */
            requiredTimeZone = "Asia/Damascus",

            /**
             * Base drift tolerance between:
             * - wall clock
             * - elapsedRealtime
             *
             * This absorbs benign jitter only.
             */
            baseDriftToleranceMillis = 5_000,

            /**
             * Additional tolerance accumulated per day
             * since last trusted anchor.
             */
            driftPerDayMillis = 3_000,

            /**
             * Whether attendance after reboot is allowed
             * without re‑anchoring.
             */
            allowPostBootRecords = true,

            /**
             * Require at least ONE trusted anchor
             * before any attendance is accepted.
             */
            requireInitialAnchor = true
        )

    /**
     * ------------------------------------------------------------------------
     * LOCATION INTEGRITY POLICY
     * ------------------------------------------------------------------------
     *
     * Governs how raw location signals are evaluated.
     *
     * USED BY:
     * --------
     * - LocationIntegrityGuard
     *
     * IMPORTANT:
     * ----------
     * This policy decides:
     *  - Whether location is required
     *  - Whether Wi‑Fi / Cellular is allowed
     *
     * It does NOT decide:
     *  - Administrative zones
     */
    fun locationPolicy(): LocationIntegrityPolicy =
        LocationIntegrityPolicy(
            locationVerificationEnabled = true,
            locationFixTimeoutSeconds = 30,

            /**
             * If GPS fails:
             * - Allowed, but justification is required
             */
            onLocationTimeout =
                LocationTimeoutAction.ALLOW_WITH_JUSTIFICATION,

            requireJustificationOnTimeout = true,

            allowWifi = true,
            allowCellular = true,

            /**
             * Offline attendance is disallowed
             * to prevent blind spoofing.
             */
            allowOffline = false,

            requireJustificationOnWifi = false,
            requireJustificationOnOffline = true,

            blockOnMockLocation = true,
            blockOnTeleportation = true,

            /**
             * Tampered signals are NOT allowed silently.
             */
            allowWithTamperEvidence = false,

            /**
             * Maximum plausible human speed.
             */
            maxHumanSpeedMetersPerSecond = 15.0
        )

    /**
     * ------------------------------------------------------------------------
     * LOCATION ZONES POLICY
     * ------------------------------------------------------------------------
     *
     * Governs ADMINISTRATIVE / LEGAL spatial rules.
     *
     * CRITICAL DISTINCTION:
     * --------------------
     * - LocationIntegrityPolicy ⇒ technical trust
     * - LocationZonesPolicy     ⇒ legal/business rules
     *
     * PHASE‑1 DECISION:
     * ----------------
     * Zones are EXPLICITLY defined as:
     *  - No zones
     *  - No blocking
     *
     * WHY NOT NULL?
     * -------------
     * - AttendanceUseCase is FINAL AUTHORITY
     * - Null would introduce ambiguity
     * - Policy must be explicit, even when permissive
     *
     * USED BY:
     * --------
     * - AttendanceUseCase (ZoneEvaluator)
     */
    fun zonePolicy(): LocationZonesPolicy =
        LocationZonesPolicy(
            /**
             * No administrative zones defined in Phase‑1.
             */
            zones = emptyList(),

            /**
             * Outside all zones ⇒ ALLOW.
             */
            defaultPolicyOutsideAllZones =
                ZonePolicy.ALLOW
        )

    /**
     * ------------------------------------------------------------------------
     * WORKING TIME POLICY (HR LAYER)
     * ------------------------------------------------------------------------
     *
     * Represents organizational working hours.
     *
     * IMPORTANT:
     * ----------
     * This policy does NOT block automatically.
     * It may ALLOW_WITH_JUSTIFICATION depending on configuration.
     *
     * USED BY:
     * --------
     * - AttendanceUseCase
     */
    fun workingTimePolicy(): WorkingTimePolicy =
        WorkingTimePolicy(
            id = "default",
            name = "Default Working Time Policy",

            /**
             * HR‑defined timezone.
             */
            timeZone = ZoneId.of("Asia/Damascus"),

            workingDays = setOf(
                WorkingDay.SATURDAY,
                WorkingDay.SUNDAY,
                WorkingDay.MONDAY,
                WorkingDay.TUESDAY,
                WorkingDay.WEDNESDAY,
                WorkingDay.THURSDAY
            ),

            workingHours = WorkingHours(
                start = LocalTime.of(8, 0),
                end = LocalTime.of(16, 0)
            ),

            holidays = emptySet(),

            /**
             * Outside working hours is allowed,
             * but justification is required.
             */
            outOfWorkingTimeAction =
                OutOfWorkingTimeAction.ALLOW_WITH_JUSTIFICATION
        )
}