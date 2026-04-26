package com.mahmoud.attendify.channel

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.mahmoud.attendify.attendance.domain.AttendanceAction
import com.mahmoud.attendify.attendance.domain.AttendanceResult
import com.mahmoud.attendify.attendance.orchestration.AttendanceRuntimeOrchestrator
import com.mahmoud.attendify.attendance.AttendanceSession

/**
 * AttendanceMethodChannel
 *
 * ROLE:
 * -----
 * ✅ Flutter ⇄ Native bridge
 * ✅ Data mapping only
 *
 * IMPORTANT:
 * ----------
 * ❌ No business logic
 * ❌ No policy inference
 * ❌ No null‑based guessing
 *
 * Domain decisions must be explicit in AttendanceResult.
 */
class AttendanceMethodChannel(
    private val runtimeOrchestrator: AttendanceRuntimeOrchestrator
) : MethodChannel.MethodCallHandler {

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

    private fun handleStartAttendance(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        try {
            val employeeId = call.argument<String>("employeeId")
                ?: return result.error(
                    "INVALID_ARGS",
                    "employeeId missing",
                    null
                )

            val actionName = call.argument<String>("action")
                ?: return result.error(
                    "INVALID_ARGS",
                    "action missing",
                    null
                )

            val action = try {
                AttendanceAction.valueOf(actionName)
            } catch (e: Exception) {
                return result.error(
                    "INVALID_ARGS",
                    "Invalid action: $actionName",
                    null
                )
            }

            val faceBitmap =
                AttendanceSession.consumeFace()
                    ?: return result.error(
                        "NO_FACE",
                        "No valid face captured yet",
                        null
                    )

            val attendanceResult =
                runtimeOrchestrator.attemptAttendance(
                    action = action,
                    frameBitmap = faceBitmap,
                    employeeId = employeeId
                )

            result.success(serializeResult(attendanceResult))

        } catch (e: Exception) {
            result.error(
                "NATIVE_ERROR",
                e.message ?: "Unknown native error",
                null
            )
        }
    }

    /**
     * Serialize AttendanceResult for Flutter.
     *
     * NOTE:
     * -----
     * Justification policy is NOT inferred here.
     * Flutter receives only explicit domain output.
     */
    private fun serializeResult(
        result: AttendanceResult
    ): Map<String, Any> =
        when (result) {

            is AttendanceResult.Accepted ->
                buildMap {
                    put("status", "ACCEPTED")
                    put("action", result.action.name)

                    // Included only if domain provided it explicitly
                    result.justification?.let {
                        put("justification", it)
                    }
                }

            is AttendanceResult.Blocked ->
                mapOf(
                    "status" to "BLOCKED",
                    "reason" to result.reason
                )
        }
}