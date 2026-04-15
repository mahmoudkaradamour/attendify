package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame

/**
 * MouthOpenCheck
 *
 * Arabic:
 * يكتشف فتح الفم لمدة كافية
 * (تنفس / حركة مقصودة).
 */
class MouthOpenCheck(
    private val minOpenFrames: Int
) {

    private var openFrameCount = 0

    fun onFrame(frame: FacialMetricsFrame) {
        if (frame.isMouthOpen) {
            openFrameCount++
        }
    }

    fun isPassed(): Boolean =
        openFrameCount >= minOpenFrames

    fun reset() {
        openFrameCount = 0
    }

    fun getOpenFrameCount(): Int = openFrameCount
}