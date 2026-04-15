package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame

/**
 * SmileCheck
 *
 * Arabic:
 * يتحقق من ابتسامة حقيقية تدريجية
 * مع تسجيل الدرجة والزمن.
 */
class SmileCheck(
    private val minSmileScore: Double,
    private val minSmileDurationMs: Long = 300
) {

    private var smileStartMs: Long = 0
    private var smiling = false
    private var maxSmileScore = 0.0

    fun onFrame(frame: FacialMetricsFrame) {

        if (frame.smileScore >= minSmileScore) {

            if (!smiling) {
                smiling = true
                smileStartMs = frame.timestampMs
            }

            maxSmileScore = maxOf(maxSmileScore, frame.smileScore)

        } else {
            smiling = false
        }
    }

    fun isPassed(): Boolean {
        if (!smiling) return false

        val duration =
            System.currentTimeMillis() - smileStartMs

        return duration >= minSmileDurationMs
    }

    fun getMaxSmileScore(): Double = maxSmileScore

    fun reset() {
        smiling = false
        smileStartMs = 0
        maxSmileScore = 0.0
    }
}