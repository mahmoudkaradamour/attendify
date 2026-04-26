package com.mahmoud.attendify.config

/**
 * AttendancePolicyProvider
 *
 * ✅ Single source of truth for current policies
 * ✅ Default now, Runtime later (Flutter override)
 */
object AttendancePolicyProvider {

    fun timePolicy() =
        DefaultAttendancePolicies.timePolicy()

    fun locationPolicy() =
        DefaultAttendancePolicies.locationPolicy()

    fun workingTimePolicy() =
        DefaultAttendancePolicies.workingTimePolicy()
}