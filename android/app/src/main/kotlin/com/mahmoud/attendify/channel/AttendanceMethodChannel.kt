package com.mahmoud.attendify.channel

import android.graphics.Bitmap
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import com.mahmoud.attendify.attendance.AttendanceUseCase
import com.mahmoud.attendify.attendance.AttendanceDecision
import com.mahmoud.attendify.attendance.RejectionReason
import com.mahmoud.attendify.attendance.AttendanceSession
/**
 * AttendanceMethodChannel
 *
 * ✅ جسر الاتصال بين Flutter و Native
 * ✅ لا يحتوي أي منطق أعمال
 * ✅ يحول البيانات فقط
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
                result.success(true)
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    /**
     * startAttendance
     *
     * في هذه المرحلة:
     * - Flutter يمرر employeeId + embedding
     * - Native يملك الصورة (faceBitmap) من Pipeline
     *
     * 📌 ربط السياسات و الصورة سيتم لاحقًا من Flutter
     */
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

            /**
             * 🔴 ملاحظة مهمة:
             * - faceBitmap يأتي من Native Pipeline
             * - في هذه المرحلة نمرّر Bitmap وهمي
             *
             * ➜ هذا مؤقت
             * ➜ سيتم استبداله عند ربط Flutter بالـ Camera session
             */
            val faceBitmap =
                AttendanceSession.consumeFace()
                    ?: run {
                        result.error(
                            "NO_FACE",
                            "No valid face captured yet",
                            null
                        )
                        return
                    }


            val decision =
                attendanceUseCase.attemptAttendance(
                    faceBitmap = faceBitmap,
                    liveEmbedding = floatEmbedding,
                    employeeId = employeeId,

                    employeePolicy = null,
                    groupPolicy = null,
                    orgPolicy = null
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

    /**
     * تحويل AttendanceDecision إلى Map بسيط لFlutter
     */
    private fun serializeDecision(
        decision: AttendanceDecision
    ): Map<String, Any> {

        return when (decision) {

            AttendanceDecision.AttendanceRecorded ->
                mapOf(
                    "status" to "RECORDED"
                )

            is AttendanceDecision.AttendanceRejected -> {
                when (val reason = decision.reason) {

                    is RejectionReason.FASRejected ->
                        mapOf(
                            "status" to "REJECTED",
                            "reason" to "FAS",
                            "message" to reason.reason
                        )

                    is RejectionReason.FaceMismatch ->
                        mapOf(
                            "status" to "REJECTED",
                            "reason" to "NO_MATCH"
                        )
                }
            }
        }
    }
}