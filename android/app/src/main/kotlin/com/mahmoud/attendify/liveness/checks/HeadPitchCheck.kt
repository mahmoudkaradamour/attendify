package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame
import kotlin.math.abs

/**
 * HeadPitchCheck
 *
 * Arabic:
 * يكتشف رفع أو خفض الرأس
 * مع تسجيل الدرجة.
 */
class HeadPitchCheck(
    private val minPitchDegrees: Double
) {

    private var maxObservedPitch = 0.0

    fun onFrame(frame: FacialMetricsFrame) {
        maxObservedPitch =
            maxOf(maxObservedPitch, abs(frame.headPitchDegrees))
    }

    fun isPassed(): Boolean =
        maxObservedPitch >= minPitchDegrees

    fun getMaxPitch(): Double = maxObservedPitch

    fun reset() {
        maxObservedPitch = 0.0
    }
}