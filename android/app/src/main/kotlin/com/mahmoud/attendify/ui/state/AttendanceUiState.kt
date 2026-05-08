package com.mahmoud.attendify.ui.state

/**
 * =============================================================================
 * 🧠 AttendanceUiState — User-Visible State Machine (Formal Model)
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL DEFINITION
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   S = {Idle, Securing, Capturing, Processing, Success, Error}
 *
 * Then:
 *
 *   UI ∈ S
 *
 * evolves through transitions:
 *
 *   T : S × Event → S
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Represent the system execution state in a form that is:
 *
 *   ✅ Human-readable
 *   ✅ Deterministic
 *   ✅ Minimal
 *   ✅ Safe for exposure
 *
 * -----------------------------------------------------------------------------
 * 🧠 ARCHITECTURAL SEPARATION
 * -----------------------------------------------------------------------------
 *
 * IMPORTANT:
 *
 *   This model represents:
 *     → What the user SHOULD SEE
 *
 *   It explicitly does NOT represent:
 *     ❌ internal engine states
 *     ❌ cryptographic operations
 *     ❌ sensor-level details
 *
 * -----------------------------------------------------------------------------
 * 📊 STATE SPACE (ENUMERATION)
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────┐
 *   │ Idle         │  ← System waiting
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Securing     │  ← Integrity & environment checks
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Capturing    │  ← Camera + sensor acquisition
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Processing   │  ← AI + crypto + matching
 *   └──────┬───────┘
 *      ┌───▼───────┐
 *      │ Success   │  ← Completed successfully
 *      └───────────┘
 *           OR
 *      ┌────────────┐
 *      │ Error      │  ← Failure with reason
 *      └────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 COGNITIVE ENGINEERING PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * Humans cannot process multi-layered system complexity.
 *
 * Therefore:
 *
 *   Complex system state → mapped → simplified UI state
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY MODEL
 * -----------------------------------------------------------------------------
 *
 * This state MUST NEVER expose:
 *
 *   ❌ cryptographic hashes
 *   ❌ raw sensor data
 *   ❌ internal identifiers
 *
 * Only high-level semantics are allowed.
 *
 * -----------------------------------------------------------------------------
 * ✅ DESIGN PROPERTIES
 * -----------------------------------------------------------------------------
 *
 *   ✅ Deterministic:
 *       Same events → same UI transitions
 *
 *   ✅ Finite:
 *       State space is bounded and enumerable
 *
 *   ✅ Safe:
 *       No sensitive information leakage
 *
 *   ✅ Observable:
 *       Suitable for reactive streams (StateFlow / LiveData)
 *
 * -----------------------------------------------------------------------------
 * ❗ INVARIANTS
 * -----------------------------------------------------------------------------
 *
 * 1. Exactly ONE state active at a time
 *
 * 2. Transition must be explicit (no implicit jumps)
 *
 * 3. Error must always carry human-readable explanation
 *
 * 4. Idle is the only stable resting state
 *
 * -----------------------------------------------------------------------------
 * 📊 TRANSITION GRAPH (SIMPLIFIED)
 * -----------------------------------------------------------------------------
 *
 *   Idle
 *    ↓
 *   Securing
 *    ↓
 *   Capturing
 *    ↓
 *   Processing
 *    ↓
 *   ├── Success → Idle
 *   └── Error   → Idle
 *
 * -----------------------------------------------------------------------------
 * 🧠 DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * "Expose intent, hide complexity."
 *
 * =============================================================================
 */
sealed class AttendanceUiState {

    /**
     * -------------------------------------------------------------------------
     * 🟢 Idle State
     * -------------------------------------------------------------------------
     *
     * System is ready and waiting for user interaction.
     *
     * Characteristics:
     *   - No active processing
     *   - Safe resting state
     */
    object Idle : AttendanceUiState()

    /**
     * -------------------------------------------------------------------------
     * 🔐 Securing State
     * -------------------------------------------------------------------------
     *
     * Represents:
     *
     *   - Session validation
     *   - Security checks
     *   - Environment verification
     *
     * User Perception:
     *   "Preparing secure environment..."
     */
    object Securing : AttendanceUiState()

    /**
     * -------------------------------------------------------------------------
     * 📸 Capturing State
     * -------------------------------------------------------------------------
     *
     * Represents:
     *
     *   - Camera capture
     *   - Location sampling
     *   - Sensor synchronization
     *
     * User Perception:
     *   "Capturing your data..."
     */
    object Capturing : AttendanceUiState()

    /**
     * -------------------------------------------------------------------------
     * ⚙️ Processing State
     * -------------------------------------------------------------------------
     *
     * Represents:
     *
     *   - Face detection
     *   - Feature extraction
     *   - Liveness analysis
     *   - Matching
     *   - Cryptographic verification
     *
     * User Perception:
     *   "Verifying identity..."
     */
    object Processing : AttendanceUiState()

    /**
     * -------------------------------------------------------------------------
     * ✅ Success State
     * -------------------------------------------------------------------------
     *
     * Represents:
     *
     *   - Successful attendance recording
     *   - Complete pipeline execution
     *
     * Characteristics:
     *   - Terminal state
     *   - Usually followed by reset to Idle
     */
    object Success : AttendanceUiState()

    /**
     * -------------------------------------------------------------------------
     * ❌ Error State
     * -------------------------------------------------------------------------
     *
     * Represents:
     *
     *   - Any failure in the pipeline
     *
     * Includes:
     *   - sensor failure
     *   - face mismatch
     *   - spoof detection
     *   - system error
     *
     * -----------------------------------------------------------------------------
     * PARAMETER:
     *
     *   message → user-friendly explanation
     *
     * -----------------------------------------------------------------------------
     * DESIGN RULE:
     *
     *   Message MUST:
     *     ✅ be understandable by user
     *     ❌ not expose internal implementation details
     */
    data class Error(
        val message: String
    ) : AttendanceUiState()
}
