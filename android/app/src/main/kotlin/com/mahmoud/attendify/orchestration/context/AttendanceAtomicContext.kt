package com.mahmoud.attendify.orchestration.context

import android.graphics.Bitmap
import com.mahmoud.attendify.system.time.TimeSnapshot
import com.mahmoud.attendify.system.location.LocationEvidence

/**
 * ============================================================================
 * AttendanceAtomicContext
 * ============================================================================
 *
 * ROLE (ATOMIC FORENSIC SNAPSHOT — PHASE 2.3 FINAL):
 * ----------------------------------------------------------------------------
 * This class represents a **FULLY‑BOUND, NON‑DIVISIBLE capture horizon**
 * for a SINGLE attendance attempt.
 *
 * If an instance of this class exists, then the system GUARANTEES that:
 *
 *   ✅ A single camera frame was frozen
 *   ✅ A trusted time snapshot was taken
 *   ✅ Location integrity was evaluated
 *
 * AND MOST IMPORTANTLY:
 * --------------------
 * ✅ All three facts belong to the SAME physical moment.
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS IS:
 * ----------------------------------------------------------------------------
 * ✅ A forensic container
 * ✅ An immutable evidence bundle
 * ✅ A proof foundation
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS IS NOT:
 * ----------------------------------------------------------------------------
 * ❌ It does NOT capture data
 * ❌ It does NOT validate data
 * ❌ It does NOT derive decisions
 *
 * Those responsibilities live in other sealed components.
 *
 * ----------------------------------------------------------------------------
 * ABSOLUTE RULE (LEGALLY & ARCHITECTURALLY NON‑NEGOTIABLE):
 * ----------------------------------------------------------------------------
 * ❌ No AttendanceResult may be produced
 * ❌ No administrative decision may be taken
 *
 * unless it is derived from an instance of THIS class.
 *
 * ----------------------------------------------------------------------------
 * ARCHITECTURAL POSITION (ASCII MAP):
 * ----------------------------------------------------------------------------
 *
 *   CameraManager.captureSingleFrame()
 *                 │
 *                 ▼
 *        [ Frozen Camera Frame ]
 *                 │
 *        TimeSource.snapshot()
 *                 │
 *                 ▼
 *   LocationIntegrityGuard.evaluate()
 *                 │
 *                 ▼
 * ┌──────────────────────────────────────┐
 * │     AttendanceAtomicContext           │
 * │  ├─ frozenFrame (Bitmap)              │
 * │  ├─ timeSnapshot (Trusted)            │
 * │  └─ locationEvidence (Validated)      │
 * └───────────────┬──────────────────────┘
 *                 ▼
 *        ALL downstream processing
 *
 * ----------------------------------------------------------------------------
 * SECURITY CONSEQUENCES:
 * ----------------------------------------------------------------------------
 * ✅ Eliminates TOCTOU (Time‑of‑Check / Time‑of‑Use)
 * ✅ Prevents replay of old frames with new time
 * ✅ Prevents location swapping after biometric success
 * ✅ Makes evidence legally defensible
 *
 * ----------------------------------------------------------------------------
 * PHASE EVOLUTION NOTE:
 * ----------------------------------------------------------------------------
 * - Phase‑2.2 allowed a partially‑bound context (for structural migration).
 * - Phase‑2.3 REMOVES that flexibility permanently.
 *
 * From this point forward:
 * -----------------------
 * (Frame + Time + Location) is an ATOMIC UNIT.
 */
data class AttendanceAtomicContext(

    /**
     * frozenFrame
     *
     * ------------------------------------------------------------------------
     * A single, immutable camera frame.
     *
     * SOURCE OF TRUTH:
     * ----------------
     * CameraManager.captureSingleFrame()
     *
     * GUARANTEES:
     * -----------
     * ✅ Exactly ONE frame per attendance attempt
     * ✅ No replacement
     * ✅ No re‑capture
     * ✅ No external injection
     *
     * SECURITY RATIONALE:
     * -------------------
     * Prevents:
     * - Frame slippage
     * - Bitmap replay
     * - UI‑sourced image attacks
     */
    val frozenFrame: Bitmap,

    /**
     * timeSnapshot
     *
     * ------------------------------------------------------------------------
     * A trusted snapshot of device time.
     *
     * SOURCE OF TRUTH:
     * ----------------
     * TimeSource.snapshot()
     *
     * GUARANTEES:
     * -----------
     * ✅ TimeIntegrityGuard already validated
     * ✅ Anchor‑aware
     * ✅ Suitable for forensic proof
     *
     * SECURITY RATIONALE:
     * -------------------
     * Prevents:
     * - Clock backdating
     * - Timeline manipulation
     * - Proof reuse attacks
     */
    val timeSnapshot: TimeSnapshot,

    /**
     * locationEvidence
     *
     * ------------------------------------------------------------------------
     * Evidence‑grade result of location integrity evaluation.
     *
     * SOURCE OF TRUTH:
     * ----------------
     * LocationIntegrityGuard.evaluate()
     *
     * GUARANTEES:
     * -----------
     * ✅ Anti‑spoofing rules applied
     * ✅ Network/GPS context evaluated
     * ✅ Justification requirements encoded
     *
     * ABSOLUTE REQUIREMENT (PHASE‑2.3):
     * --------------------------------
     * ❌ Must NEVER be null
     *
     * SECURITY RATIONALE:
     * -------------------
     * Prevents:
     * - Post‑hoc location injection
     * - Administrative ambiguity
     * - Location laundering
     */
    val locationEvidence: LocationEvidence
)