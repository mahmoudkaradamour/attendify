package com.mahmoud.attendify.audit

object AuditLogger {

    fun log(event: String, details: String) {
        // لاحقًا: File / DB / Remote Sync
        println("[AUDIT] $event :: $details")
    }
}