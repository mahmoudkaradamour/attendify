package com.mahmoud.attendify.ui.controller

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mahmoud.attendify.ui.state.AttendanceUiState

/**
 * =============================================================================
 * 🧠 AttendanceUiController — Reactive UI State Orchestrator
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 📌 FORMAL MODEL
 * -----------------------------------------------------------------------------
 *
 * Let:
 *
 *   S = finite set of UI states
 *   T = transition function
 *
 * Then:
 *
 *   UI(t+1) = T(UI(t), Event)
 *
 * where:
 *
 *   - UI state is deterministic
 *   - Transitions are explicit
 *   - Observers react to state changes
 *
 * -----------------------------------------------------------------------------
 * 🎯 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Provide a SINGLE SOURCE OF TRUTH for user-visible system state.
 *
 * This abstraction separates:
 *
 *   ✅ Internal system complexity (hidden)
 *   ❌ from
 *   ✅ Simple, understandable UI signals (visible)
 *
 * -----------------------------------------------------------------------------
 * 🧠 ARCHITECTURAL POSITION
 * -----------------------------------------------------------------------------
 *
 *   Orchestrator (Core Engine)
 *         │
 *         ▼
 *   UI Controller (THIS CLASS)
 *         │
 *         ▼
 *   View Layer (Activity / Compose / Flutter bridge)
 *
 * -----------------------------------------------------------------------------
 * 📊 DATA FLOW (UNIDIRECTIONAL)
 * -----------------------------------------------------------------------------
 *
 *       Event (User/System)
 *               │
 *               ▼
 *       setState(newState)
 *               │
 *               ▼
 *     MutableStateFlow (internal)
 *               │
 *               ▼
 *       StateFlow (read-only)
 *               │
 *               ▼
 *        UI Rendering Layer
 *
 * -----------------------------------------------------------------------------
 * ✅ DESIGN PROPERTIES
 * -----------------------------------------------------------------------------
 *
 *   ✅ Determinism:
 *       Same sequence of events → same UI sequence
 *
 *   ✅ Reactivity:
 *       Observers receive updates instantly
 *
 *   ✅ Immutability:
 *       External code cannot mutate state directly
 *
 *   ✅ Thread-safety:
 *       Backed by Kotlin coroutines StateFlow
 *
 * -----------------------------------------------------------------------------
 * ❗ CRITICAL DESIGN RULES
 * -----------------------------------------------------------------------------
 *
 * 1. UI state MUST NOT contain business logic
 *
 * 2. All transitions MUST be explicit via setState()
 *
 * 3. No direct mutation allowed outside this class
 *
 * 4. UI state SHOULD remain minimal and expressive
 *
 * -----------------------------------------------------------------------------
 * 📊 STATE MACHINE
 * -----------------------------------------------------------------------------
 *
 *   ┌──────────────┐
 *   │ Idle         │
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Securing     │
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Capturing    │
 *   └──────┬───────┘
 *          ▼
 *   ┌──────────────┐
 *   │ Processing   │
 *   └──────┬───────┘
 *      ┌───▼───────┐
 *      │ Success   │
 *      └───────────┘
 *           OR
 *      ┌────────────┐
 *      │ Error      │
 *      └────────────┘
 *
 * -----------------------------------------------------------------------------
 * 🧠 COGNITIVE DESIGN PRINCIPLE
 * -----------------------------------------------------------------------------
 *
 * Human users cannot process internal system complexity.
 *
 * Therefore:
 *
 *   System → maps complex execution into simple states
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY NOTE
 * -----------------------------------------------------------------------------
 *
 * UI state should NEVER expose:
 *
 *   ❌ internal hashes
 *   ❌ cryptographic material
 *   ❌ implementation details
 *
 * Only high-level semantics are allowed.
 *
 * -----------------------------------------------------------------------------
 * ⚙️ PERFORMANCE CHARACTERISTICS
 * -----------------------------------------------------------------------------
 *
 *   - O(1) state updates
 *   - Non-blocking emission
 *   - Efficient subscription via Flow collectors
 *
 * =============================================================================
 */
class AttendanceUiController {

    /* =========================================================================
     * 🔄 INTERNAL STATE (MUTABLE SOURCE)
     * ========================================================================= */

    /**
     * MutableStateFlow acts as:
     *
     *   → Reactive data holder
     *   → State broadcaster
     *
     * Initial state:
     *   System is idle (no active operation)
     */
    private val _state =
        MutableStateFlow<AttendanceUiState>(AttendanceUiState.Idle)

    /* =========================================================================
     * ✅ PUBLIC STATE (READ-ONLY VIEW)
     * ========================================================================= */

    /**
     * Exposed as read-only:
     *
     *   Prevents:
     *     ❌ external mutation
     *
     *   Ensures:
     *     ✅ controlled transitions
     */
    val state: StateFlow<AttendanceUiState> =
        _state.asStateFlow()

    /* =========================================================================
     * 🚀 STATE TRANSITION FUNCTION
     * ========================================================================= */

    /**
     * Sets new UI state.
     *
     * -----------------------------------------------------------------------------
     * BEHAVIOR:
     *
     *   - Immediate emission to subscribers
     *   - Replaces previous state (no history retained)
     *
     * -----------------------------------------------------------------------------
     * DESIGN NOTE:
     *
     * This is the ONLY mutation point in the system.
     *
     * -----------------------------------------------------------------------------
     * EXAMPLE:
     *
     *   setState(Securing)
     *   setState(Capturing)
     *   setState(Processing)
     *
     * -----------------------------------------------------------------------------
     * THREAD SAFETY:
     *
     * StateFlow ensures safe updates across threads.
     */
    fun setState(newState: AttendanceUiState) {
        _state.value = newState
    }

    /* =========================================================================
     * 🧹 RESET STATE (OPTIONAL UTILITY)
     * ========================================================================= */

    /**
     * Resets UI back to Idle.
     *
     * Useful for:
     *   - after success
     *   - after error
     *   - manual reset scenarios
     *
     * Ensures clean start for next operation.
     */
    fun reset() {
        _state.value = AttendanceUiState.Idle
    }

    /* =========================================================================
     * 🚫 ERROR HANDLING HELPER
     * ========================================================================= */

    /**
     * Convenience function for error transitions.
     *
     * Ensures:
     *   Consistent error formatting across UI
     */
    fun setError(message: String) {
        _state.value = AttendanceUiState.Error(message)
    }

    /* =========================================================================
     * 🧠 STATE INSPECTION (DEBUG / ANALYTICS)
     * ========================================================================= */

    /**
     * Returns current state snapshot.
     *
     * Useful for:
     *   - logging
     *   - debugging
     *   - telemetry
     */
    fun currentState(): AttendanceUiState {
        return _state.value
    }
}