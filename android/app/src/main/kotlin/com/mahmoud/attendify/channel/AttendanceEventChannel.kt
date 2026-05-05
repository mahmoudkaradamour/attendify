package com.mahmoud.attendify.channel

import com.mahmoud.attendify.camera.SystemStatus
import io.flutter.plugin.common.EventChannel

/**
 * AttendanceEventChannel
 *
 * ============================================================================
 * ROLE:
 * ============================================================================
 * ✅ Native → Flutter one‑way guidance channel
 * ✅ UI feedback ONLY (non‑authoritative)
 *
 * IMPORTANT NON‑NEGOTIABLE RULES:
 * ============================================================================
 * ❌ No decisions
 * ❌ No identity
 * ❌ No evidence
 * ❌ No policy reasoning
 *
 * SECURITY MODEL (Phase 3.2):
 * ============================================================================
 * 🔒 Flutter is ZERO‑TRUST
 * 🔒 Events are informational only
 * 🔒 UI guidance does NOT influence attendance outcome
 *
 * This channel exists solely to:
 *  - Inform UI about system state
 *  - Improve user experience without leaking authority
 *
 * Any misuse of this channel to carry decisions or data
 * invalidates the forensic integrity of the system.
 */
class AttendanceEventChannel :
    EventChannel.StreamHandler {

    /**
     * Active event sink.
     *
     * Lifecycle:
     * ----------
     * - Set on listen
     * - Cleared on cancel
     */
    private var eventSink: EventChannel.EventSink? = null

    override fun onListen(
        arguments: Any?,
        events: EventChannel.EventSink?
    ) {
        eventSink = events
    }

    override fun onCancel(
        arguments: Any?
    ) {
        eventSink = null
    }

    /**
     * emitStatus
     *
     * =========================================================================
     * Emits a SYSTEM STATUS update to Flutter.
     *
     * CONTRACT:
     * ---------
     * ✅ Status only
     * ❌ No decision
     * ❌ No reasoning
     * ❌ No sensitive data
     *
     * Examples:
     * ---------
     * - CAMERA_BUSY
     * - FACE_TOO_FAR
     * - IMAGE_TOO_DARK
     * - HOLD_STEADY
     */
    fun emitStatus(
        status: SystemStatus
    ) {
        eventSink?.success(
            mapOf(
                "type" to "SYSTEM_STATUS",
                "status" to status.name
            )
        )
    }

    /**
     * clear
     *
     * =========================================================================
     * Explicitly clears any active listener.
     *
     * Used when:
     * ----------
     * - Attendance attempt ends
     * - Orchestrator resets
     */
    fun clear() {
        eventSink = null
    }
}