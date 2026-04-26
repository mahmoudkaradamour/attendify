package com.mahmoud.attendify.system.time.working

/**
 * OutOfWorkingTimeAction
 *
 * Defines what happens when attendance is attempted
 * outside defined working time.
 */
enum class OutOfWorkingTimeAction {

    /** Attendance is allowed normally */
    ALLOW,

    /** Attendance allowed but requires justification */
    ALLOW_WITH_JUSTIFICATION,

    /** Attendance is strictly forbidden */
    BLOCK
}