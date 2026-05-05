package com.mahmoud.attendify.security

/**
 * ============================================================================
 * SecureEmployeeSession
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * ✅ Native‑owned employee identity provider
 * ✅ Single source of truth for current employeeId
 *
 * PHASE 3.1 SECURITY CONTRACT:
 * ----------------------------------------------------------------------------
 * ❌ Flutter MUST NOT provide employeeId
 * ❌ UI MUST NOT know employeeId
 * ❌ Identity resolution is Native‑only
 *
 * ----------------------------------------------------------------------------
 * TRUST MODEL:
 * ----------------------------------------------------------------------------
 * - Flutter is ZERO‑TRUST
 * - MethodChannel accepts INTENT only
 * - AttendanceRuntimeOrchestrator resolves identity internally
 *
 * ----------------------------------------------------------------------------
 * RESPONSIBILITIES:
 * ----------------------------------------------------------------------------
 * ✅ Store employee identity after successful authentication
 * ✅ Provide identity ONLY to trusted Native components
 * ✅ Fail fast if identity is missing or invalid
 *
 * ----------------------------------------------------------------------------
 * NON‑RESPONSIBILITIES:
 * ----------------------------------------------------------------------------
 * ❌ Authentication
 * ❌ Authorization
 * ❌ Backend sync
 * ❌ Multi‑user handling
 *
 * This object enforces the principle:
 *   "No Attendance Attempt Without Native‑Resolved Identity"
 */
object SecureEmployeeSession {

    /**
     * Backing field for employee identity.
     *
     * SECURITY NOTE:
     * --------------
     * - This value MUST be set only after successful authentication
     * - Must NEVER be populated from Flutter arguments
     *
     * Implementation note:
     * --------------------
     * - Currently stored in‑memory (Phase 3.1 scope)
     * - Can later be backed by:
     *   • EncryptedSharedPreferences
     *   • Android Keystore
     *   • Secure Hardware‑backed storage
     */
    @Volatile
    private var employeeId: String? = null

    /**
     * setEmployeeId
     *
     * =========================================================================
     * Stores the authenticated employeeId in a Native‑owned context.
     *
     * CONTRACT:
     * ---------
     * ✅ Must be called ONLY from trusted Native authentication flow
     * ❌ Must NEVER be called from Flutter
     */
    fun setEmployeeId(
        id: String
    ) {
        require(id.isNotBlank()) {
            "employeeId must not be blank"
        }

        employeeId = id
    }

    /**
     * requireEmployeeId
     *
     * =========================================================================
     * Resolves the current employeeId or FAILS FAST.
     *
     * USAGE:
     * ------
     * - AttendanceRuntimeOrchestrator
     * - Matching pipeline
     *
     * SECURITY GUARANTEE:
     * -------------------
     * If this method returns:
     * ✅ Identity is Native‑owned
     * ✅ Identity was NOT supplied by UI
     *
     * FAILURE MODE:
     * -------------
     * Throws IllegalStateException if:
     * - Session not initialized
     * - Authentication not completed
     */
    fun requireEmployeeId(): String =
        employeeId
            ?: error(
                "SecureEmployeeSession not initialized: employeeId missing"
            )

    /**
     * clear
     *
     * =========================================================================
     * Clears the current session.
     *
     * Intended usage:
     * ----------------
     * - Logout
     * - Session invalidation
     * - Security reset
     */
    fun clear() {
        employeeId = null
    }
}