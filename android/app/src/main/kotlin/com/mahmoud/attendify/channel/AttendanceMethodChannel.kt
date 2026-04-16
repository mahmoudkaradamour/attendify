package com.mahmoud.attendify.channel

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.mahmoud.attendify.attendance.AttendanceUseCase
import com.mahmoud.attendify.attendance.AttendanceDecision
import com.mahmoud.attendify.matching.MatchDecision

/**
 * AttendanceMethodChannel
 *
 * مسؤول عن:
 * - استقبال أوامر Flutter
 * - استدعاء AttendanceUseCase
 * - إعادة النتيجة بشكل بسيط
 *
 * لا يحتوي أي منطق أعمال.
 */
class AttendanceMethodChannel(
    private val attendanceUseCase: AttendanceUseCase
) : MethodChannel.MethodCallHandler {

    override fun onMethodCall(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        when (call.method) {

            "startAttendance" -> {
                handleStartAttendance(call, result)
            }

            "cancelAttendance" -> {
                // حاليًا لا يوجد State طويل
                result.success(true)
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun handleStartAttendance(
        call: MethodCall,
        result: MethodChannel.Result
    ) {
        try {
            val employeeId = call.argument<String>("employeeId")
                ?: run {
                    result.error("INVALID_ARGS", "employeeId missing", null)
                    return
                }

            val embedding = call.argument<List<Double>>("embedding")
                ?: run {
                    result.error("INVALID_ARGS", "embedding missing", null)
                    return
                }

            val floatEmbedding = FloatArray(embedding.size) {
                embedding[it].toFloat()
            }

            val decision =
                attendanceUseCase.attemptAttendance(
                    liveEmbedding = floatEmbedding,
                    employeeId = employeeId
                )

            result.success(serializeDecision(decision))

        } catch (e: Exception) {
            result.error(
                "NATIVE_ERROR",
                e.message ?: "Unknown error",
                null
            )
        }
    }

    private fun serializeDecision(
        decision: AttendanceDecision
    ): Map<String, Any> {

        return when (decision) {

            AttendanceDecision.AttendanceRecorded ->
                mapOf(
                    "status" to "RECORDED"
                )

            is AttendanceDecision.AttendanceRejected -> {
                val reason = decision.reason
                when (reason) {
                    is MatchDecision.MatchSuccess ->
                        mapOf("status" to "RECORDED")

                    else ->
                        mapOf(
                            "status" to "REJECTED",
                            "reason" to reason::class.simpleName!!
                        )
                }
            }
        }
    }
}