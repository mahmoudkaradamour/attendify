package com.mahmoud.attendify.channel

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.orchestration.AttendanceRuntimeOrchestrator

/**
 * ============================================================================
 * AttendanceMethodChannel
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * This class is a **strict Flutter ⇄ Native bridge**.
 *
 * It exists ONLY to:
 *  - Receive method calls from Flutter
 *  - Validate and map primitive arguments
 *  - Invoke native domain orchestration
 *  - Return explicit results back to Flutter
 *
 * ----------------------------------------------------------------------------
 * WHAT THIS CLASS MUST NEVER DO:
 * ----------------------------------------------------------------------------
 * ❌ Accept user identity (employeeId, userId, etc.)
 * ❌ Execute business logic
 * ❌ Perform biometric operations
 * ❌ Access camera, ML models, or location services
 * ❌ Apply policy decisions
 *
 * ----------------------------------------------------------------------------
 * SECURITY MODEL (Phase 3.1 – Zero Trust UI):
 * ----------------------------------------------------------------------------
 * 🔒 Flutter is considered UNTRUSTED
 * 🔒 Identity is resolved ONLY inside Native secure storage
 * 🔒 All evidence and decisions are Native‑owned
 *
 * This layer is intentionally:
 *  - Thin
 *  - Explicit
 *  - Stupid by design
 *
 * Any intelligence here would be a security violation.
 */
class AttendanceMethodChannel(
    private val runtimeOrchestrator: AttendanceRuntimeOrchestrator
) : MethodChannel.MethodCallHandler {

    /**
     * CoroutineScope bound to the MAIN thread.
     *
     * TECHNICAL JUSTIFICATION:
     * ------------------------------------------------------------------------
     * - MethodChannel callbacks MUST respond on the main thread.
     * - Heavy work MUST NOT block the main thread.
     *
     * HOW THIS IS SAFE:
     * ------------------------------------------------------------------------
     * - attemptAttendance(...) is a suspend function
     * - Its heavy work is internally dispatched (camera, ML, IO)
     * - The main thread only coordinates suspension/resumption
     */
    private val scope =
        CoroutineScope(Dispatchers.Main)

    /**
     * =========================================================================
     * onMethodCall
     * =========================================================================
     *
     * Entry point from Flutter.
     *
     * RULES:
     * ------------------------------------------------------------------------
     * ✅ Route by method name only
     * ✅ Delegate real work to private handlers
     * ❌ No logic branching beyond routing
     */
    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {

            /**
             * Primary attendance entry point.
             *
             * Flutter sends:
             *  - action: String ("CHECK_IN" / "CHECK_OUT")
             *
             * Flutter does NOT send:
             *  - employeeId
             *  - timestamps
             *  - location
             */
            "startAttendance" ->
                handleStartAttendance(call, result)

            /**
             * Optional cancel signal.
             *
             * NOTE:
             * In the current architecture, cancellation is best‑effort.
             * The call exists mainly for UI symmetry.
             */
            "cancelAttendance" ->
                result.success(true)

            /**
             * Any unknown method is explicitly rejected.
             */
            else ->
                result.notImplemented()
        }
    }

    /**
     * =========================================================================
     * handleStartAttendance
     * =========================================================================
     *
     * PURPOSE:
     * ------------------------------------------------------------------------
     * - Extract and validate Flutter arguments
     * - Translate them into domain types
     * - Cross the coroutine boundary safely
     *
     * SECURITY CONTRACT:
     * ------------------------------------------------------------------------
     * ✅ Accepts ONLY high‑level intent
     * ❌ Accepts NO identity, evidence, or state
     */
    private fun handleStartAttendance(
        call: MethodCall,
        result: MethodChannel.Result
    ) {

        /**
         * Extract attendance action from Flutter.
         *
         * REQUIRED:
         * - action must exist
         * - action must match AttendanceAction enum
         */
        val actionName =
            call.argument<String>("action")
                ?: run {
                    result.error(
                        "INVALID_ARGS",
                        "action missing",
                        null
                    )
                    return
                }

        val action =
            try {
                AttendanceAction.valueOf(actionName)
            } catch (_: Exception) {
                result.error(
                    "INVALID_ARGS",
                    "Invalid action: $actionName",
                    null
                )
                return
            }

        /**
         * ✅ COROUTINE BOUNDARY
         *
         * WHY THIS MATTERS:
         * --------------------------------------------------------------------
         * - Flutter ↔ Native calls must never block
         * - Biometric pipelines are slow by nature
         * - Suspending preserves UI responsiveness
         */
        scope.launch {

            try {

                /**
                 * Delegate EVERYTHING to the Runtime Orchestrator.
                 *
                 * This is the ONLY place where:
                 *  - camera
                 *  - face recognition
                 *  - liveness
                 *  - location integrity
                 * are coordinated.
                 */
                val attendanceResult =
                    runtimeOrchestrator.attemptAttendance(
                        action = action
                    )

                /**
                 * Serialize the explicit final result
                 * and return it to Flutter.
                 */
                result.success(
                    serializeResult(attendanceResult)
                )

            } catch (t: Throwable) {

                /**
                 * Any uncaught native exception is mapped
                 * to a generic platform error.
                 *
                 * SECURITY NOTE:
                 * We do NOT leak stack traces or internal details.
                 */
                result.error(
                    "NATIVE_ERROR",
                    t.message ?: "Unknown native error",
                    null
                )
            }
        }
    }

    /**
     * =========================================================================
     * serializeResult
     * =========================================================================
     *
     * Converts a strongly‑typed AttendanceResult
     * into a minimal, explicit Map for Flutter.
     *
     * DESIGN PRINCIPLES:
     * ------------------------------------------------------------------------
     * ✅ Deterministic
     * ✅ No interpretation
     * ✅ No derived signals
     *
     * Flutter receives:
     *  - Final decision
     *  - Human‑readable explanation (if any)
     *
     * Flutter does NOT receive:
     *  - Confidence scores
     *  - Thresholds
     *  - Internal state
     */
    private fun serializeResult(
        result: AttendanceResult
    ): Map<String, Any> =
        when (result) {

            is AttendanceResult.Accepted ->
                buildMap {
                    put("status", "ACCEPTED")
                    put("action", result.action.name)

                    result.justification?.let {
                        put("justification", it.text)
                    }
                }

            is AttendanceResult.Blocked ->
                mapOf(
                    "status" to "BLOCKED",
                    "reason" to result.reason
                )
        }
}