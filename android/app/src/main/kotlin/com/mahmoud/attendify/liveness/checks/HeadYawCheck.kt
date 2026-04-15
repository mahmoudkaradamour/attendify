package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame
import kotlin.math.abs

/**
 * HeadYawCheck
 *
 * Arabic:
 * يتحقق من استدارة الرأس يمين أو يسار
 * مع تسجيل الدرجة الفعلية.
 */
class HeadYawCheck(
    private val minYawDegrees: Double
) {

    private var maxObservedYaw = 0.0

    fun onFrame(frame: FacialMetricsFrame) {
        maxObservedYaw =
            maxOf(maxObservedYaw, abs(frame.headYawDegrees))
    }

    fun isPassed(): Boolean =
        maxObservedYaw >= minYawDegrees

    fun getMaxYaw(): Double = maxObservedYaw

    fun reset() {
        maxObservedYaw = 0.0
    }
}