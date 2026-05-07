package com.mahmoud.attendify.storage.recovery

import com.mahmoud.attendify.attendance.lifecycle.AttemptLifecycleManager
import com.mahmoud.attendify.attendance.lifecycle.AttemptStatus
import com.mahmoud.attendify.forensics.ForensicAuditTrailWriter

/**
 * =============================================================================
 * 🛡 AttemptRecoveryManager — Process Recovery Layer (C2 + C3)
 * =============================================================================
 *
 * ┌──────────────────────────── ARCHITECTURE ─────────────────────────────┐
 *
 *                     Application Startup
 *                              │
 *                              ▼
 *                   AttemptRecoveryManager
 *                              │
 *          detectUnfinishedAttempt()
 *                              │
 *                ┌─────────────┴─────────────┐
 *                ▼                           ▼
 *        NO UNFINISHED                UNFINISHED FOUND
 *                │                           │
 *                │                           ▼
 *                │                 appendSystemEvent()
 *                │                           │
 *                │                           ▼
 *                │             markFinal(SUSPICIOUS_ABORT)
 *
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 ROLE
 * -----------------------------------------------------------------------------
 *
 * Ensures that:
 *
 * ✅ No attempt disappears silently (C2)
 * ✅ Crashed / killed flows are recorded (C3)
 * ✅ System maintains forensic continuity
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY GUARANTEES
 * -----------------------------------------------------------------------------
 *
 * ✅ Detects incomplete attempts
 * ✅ Converts them into forensic events
 * ✅ Prevents silent bypass (kill-before-log attack)
 *
 * -----------------------------------------------------------------------------
 * 📊 FORENSIC FLOW
 * -----------------------------------------------------------------------------
 *
 * INITIATED → (CRASH / KILL) → STARTUP → SUSPICIOUS_ABORT → LOGGED ✅
 *
 */
class AttemptRecoveryManager(
    private val lifecycleManager: AttemptLifecycleManager,
    private val auditWriter: ForensicAuditTrailWriter
) {

    /**
     * =============================================================================
     * ✅ RUN RECOVERY CHECK
     * =============================================================================
     *
     * MUST be called on application start.
     *
     * This function:
     * ✅ Detects unfinished attempts
     * ✅ Converts them into forensic events
     * ✅ Restores system integrity
     *
     * -----------------------------------------------------------------------------
     * 🔥 IMPORTANT DESIGN DECISION
     * -----------------------------------------------------------------------------
     *
     * This function is suspend because:
     * - it interacts with forensic ledger (I/O)
     * - it MUST run safely without blocking main thread
     */
    suspend fun runRecoveryCheck() {

        /* ================================================================
         * STEP 1 — DETECT INCOMPLETE FLOW
         * ================================================================ */
        val hasUnfinished = lifecycleManager.detectUnfinishedAttempt()

        if (!hasUnfinished) {
            return
        }

        /* ================================================================
         * STEP 2 — LOG FORENSIC EVENT
         * ================================================================ */
        auditWriter.appendSystemEvent(
            event = "SUSPICIOUS_ABORT",
            details = "Previous attempt terminated unexpectedly"
        )

        /* ================================================================
         * STEP 3 — FIX STATE
         * ================================================================ */
        lifecycleManager.markFinal(AttemptStatus.SUSPICIOUS_ABORT)
    }
}
