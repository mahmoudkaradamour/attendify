package com.mahmoud.attendify.security.boundary

import com.mahmoud.attendify.attendance.domain.AttendanceResult

/**
 * =============================================================================
 * 🛡 AttendanceRequestGate — High-Level UI Security Gate
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Central entry point BEFORE orchestrator:
 *
 *   UI → Gate → Orchestrator
 *
 * -----------------------------------------------------------------------------
 * 📊 FLOW
 * -----------------------------------------------------------------------------
 *
 * Request
 *   │
 *   ▼
 * Rate Limit Check
 *   │
 *   ├── BLOCK → return Blocked
 *   ▼
 * Forward to execution
 *
 */
object AttendanceRequestGate {

    fun guard(): AttendanceResult? {

        if (!UIAccessGuard.allow()) {
            return AttendanceResult.Blocked(
                "Too many requests. Please wait."
            )
        }

        return null
    }
}