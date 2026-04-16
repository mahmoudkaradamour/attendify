package com.mahmoud.attendify.audit

/**
 * AttendanceAuditLogger
 *
 * واجهة عامة لتسجيل ومزامنة سجلات الحضور.
 */
interface AttendanceAuditLogger {

    fun log(entry: AttendanceAuditLog)

    fun getPendingLogs(): List<AttendanceAuditLog>

    fun clearPendingLogs()
}