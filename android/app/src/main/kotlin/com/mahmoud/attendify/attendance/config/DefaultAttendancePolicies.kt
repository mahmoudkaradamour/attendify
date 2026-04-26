package com.mahmoud.attendify.config

import com.mahmoud.attendify.system.time.TimeIntegrityPolicy
import com.mahmoud.attendify.system.location.LocationIntegrityPolicy
import com.mahmoud.attendify.system.location.LocationTimeoutAction
import com.mahmoud.attendify.system.time.working.*
import java.time.LocalTime
import java.time.ZoneId

/**
 * DefaultAttendancePolicies
 *
 * ✅ Safe, conservative defaults
 * ✅ Used only until overridden by Flutter
 * ✅ Explicit – no hidden assumptions
 */
object DefaultAttendancePolicies {

    fun timePolicy(): TimeIntegrityPolicy =
        TimeIntegrityPolicy(
            requiredTimeZone = "Asia/Damascus",
            driftToleranceMillis = 5_000,
            allowPostBootRecords = true,
            requireInitialAnchor = true
        )

    fun locationPolicy(): LocationIntegrityPolicy =
        LocationIntegrityPolicy(
            locationVerificationEnabled = true,
            locationFixTimeoutSeconds = 30,
            onLocationTimeout = LocationTimeoutAction.ALLOW_WITH_JUSTIFICATION,
            requireJustificationOnTimeout = true,
            allowWifi = true,
            allowCellular = true,
            allowOffline = false,
            requireJustificationOnWifi = false,
            requireJustificationOnOffline = true,
            blockOnMockLocation = true,
            blockOnTeleportation = true,
            allowWithTamperEvidence = false,
            maxHumanSpeedMetersPerSecond = 15.0
        )

    fun workingTimePolicy(): WorkingTimePolicy =
        WorkingTimePolicy(
            id = "default",
            name = "Default Working Time Policy",
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
                end   = LocalTime.of(16, 0)
            ),
            holidays = emptySet(),
            outOfWorkingTimeAction = OutOfWorkingTimeAction.ALLOW_WITH_JUSTIFICATION
        )
}