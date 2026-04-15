package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame
import kotlin.math.abs

/**
 * PhotometricResponseCheck
 *
 * Arabic:
 * يتحقق من استجابة الوجه لتغير مفاجئ في الإضاءة.
 *
 * يفشل عادة مع:
 * - الصور المطبوعة
 * - الشاشات
 */
class PhotometricResponseCheck(
    private val varianceChangeThreshold: Double
) {

    private var baselineVariance: Double? = null
    private var responseDetected = false

    fun onFrame(frame: FacialMetricsFrame) {

        if (baselineVariance == null) {
            baselineVariance = frame.luminanceVariance
            return
        }

        val diff =
            abs(frame.luminanceVariance - baselineVariance!!)

        if (diff >= varianceChangeThreshold) {
            responseDetected = true
        }
    }

    fun isPassed(): Boolean = responseDetected

    fun reset() {
        baselineVariance = null
        responseDetected = false
    }
}