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
 * AttendanceMethodChannel
 *
 * ============================================================================
 * ROLE:
 * ============================================================================
 * ✅ Flutter ⇄ Native asynchronous bridge
 * ✅ Data mapping only
 *
 * IMPORTANT NON‑NEGOTIABLE RULES:
 * ============================================================================
 * ❌ No business logic
 * ❌ No ML calls
 * ❌ No policy inference
 * ❌ No camera or bitmap handling
 *
 * This layer:
 *  - Translates Flutter arguments → domain inputs
 *  - Invokes suspend orchestration safely
 *  - Serializes explicit AttendanceResult back to Flutter
 *
 * All decisions MUST already be encoded in AttendanceResult.
 */
class AttendanceMethodChannel(
    private val runtimeOrchestrator: AttendanceRuntimeOrchestrator
) : MethodChannel.MethodCallHandler {

    /**
     * Coroutine scope bound to Main thread.
     *
     * WHY Main?
     * ----------
     * - MethodChannel callbacks must return on main thread
     * - Heavy work is safely suspended inside orchestrator/usecases
     */
    private val scope =
        CoroutineScope(Dispatchers.Main)

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {

            "startAttendance" ->
                handleStartAttendance(call, result)

            "cancelAttendance" ->
                result.success(true)

            else ->
                result.notImplemented()
        }
    }

    /**
     * handleStartAttendance
     *
     * =========================================================================
     * Validates Flutter input, then launches attendance asynchronously.
     */
    private fun handleStartAttendance(
        call: MethodCall,
        result: MethodChannel.Result
    ) {

        val employeeId =
            call.argument<String>("employeeId")
                ?: run {
                    result.error(
                        "INVALID_ARGS",
                        "employeeId missing",
                        null
                    )
                    return
                }

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
         * ✅ COROUTINE BOUNDARY (Stage 0.3)
         *
         * - Orchestrator is suspend
         * - Never block MethodChannel thread
         */
        scope.launch {

            try {

                val attendanceResult =
                    runtimeOrchestrator.attemptAttendance(
                        action = action,
                        employeeId = employeeId
                    )

                result.success(
                    serializeResult(attendanceResult)
                )

            } catch (t: Throwable) {

                result.error(
                    "NATIVE_ERROR",
                    t.message ?: "Unknown native error",
                    null
                )
            }
        }
    }

    /**
     * serializeResult
     *
     * =========================================================================
     * Converts AttendanceResult → simple Map for Flutter.
     *
     * Justification:
     * --------------
     * - Policy interpretation is NOT done here
     * - Flutter receives explicit domain output only
     */
    private fun serializeResult(
        result: AttendanceResult
    ): Map<String, Any> =
        when (result) {

            is AttendanceResult.Accepted ->
                buildMap {
                    put("status", "ACCEPTED")
                    put("action", result.action.name)

                    // Included ONLY if domain explicitly attached it
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