package com.mahmoud.attendify.system.location.zones

/**
 * ZonePolicy
 *
 * Defines what happens when a location attempt
 * is evaluated relative to a zone.
 */
enum class ZonePolicy {

    /**
     * Attendance is allowed without justification.
     */
    ALLOW,

    /**
     * Attendance is allowed but requires
     * administrative justification.
     */
    ALLOW_WITH_JUSTIFICATION,

    /**
     * Attendance is strictly forbidden.
     */
    BLOCK
}