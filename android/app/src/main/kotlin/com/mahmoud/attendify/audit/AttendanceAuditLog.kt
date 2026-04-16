package com.mahmoud.attendify.audit

import com.mahmoud.attendify.matching.ReferenceSource

/**
 * AttendanceAuditLog
 *
 * سجل واحد لقرار حضور، يُستخدم للتدقيق والمزامنة.
 */
data class AttendanceAuditLog(
    val employeeId: String,
    val timestampMs: Long,
    val similarity: Double?,
    val threshold: Double?,
    val referenceSource: ReferenceSource,
    val decision: String
)