package com.mahmoud.attendify.liveness

import com.mahmoud.attendify.liveness.checks.*
import com.mahmoud.attendify.liveness.data.FacialMetricsFrame
import com.mahmoud.attendify.liveness.engine.MetricsSessionLog
import com.mahmoud.attendify.liveness.policy.LivenessPolicy
import com.mahmoud.attendify.liveness.result.LivenessResult

/**
 * LivenessOrchestrator
 *
 * Arabic:
 * هذا الكلاس هو المسؤول عن:
 * 1. استقبال القياسات اللحظية (FacialMetricsFrame)
 * 2. تمريرها إلى جميع التحققات (Checks) المُفعّلة
 * 3. تسجيل الجلسة كاملة (Audit / Debug)
 * 4. اتخاذ القرار النهائي: حي أم تزوير
 *
 * ملاحظات معمارية:
 * - لا يحتوي أي منطق ML
 * - لا يحتوي UI
 * - كل التحكم يتم عبر LivenessPolicy
 */
class LivenessOrchestrator(
    private val policy: LivenessPolicy
) {

    /* =========================================================
     * Session logging
     * ========================================================= */

    private val sessionLog = MetricsSessionLog()

    /* =========================================================
     * Checks initialization (حسب السياسة)
     * ========================================================= */

    private val blinkCheck =
        if (policy.requireBlink)
            BlinkCheck(
                requiredBlinkCount = policy.minBlinkCount,
                allowSimultaneousBlink = policy.allowSimultaneousBlink,
                eyeClosedThreshold = policy.eyeClosedThreshold
            )
        else null

    private val smileCheck =
        if (policy.requireSmile)
            SmileCheck(
                minSmileScore = policy.minSmileScore,
                minSmileDurationMs = policy.minSmileDurationMs
            )
        else null

    private val mouthOpenCheck =
        if (policy.requireMouthOpen)
            MouthOpenCheck(
                minOpenFrames = policy.minMouthOpenFrames
            )
        else null

    private val headYawCheck =
        if (policy.requireYaw)
            HeadYawCheck(
                minYawDegrees = policy.minYawDegrees
            )
        else null

    private val headPitchCheck =
        if (policy.requirePitch)
            HeadPitchCheck(
                minPitchDegrees = policy.minPitchDegrees
            )
        else null

    private val photometricCheck =
        if (policy.requirePhotometricResponse)
            PhotometricResponseCheck(
                varianceChangeThreshold =
                    policy.luminanceVarianceThreshold
            )
        else null

    /* =========================================================
     * Frame ingestion
     * ========================================================= */

    /**
     * onFrame
     *
     * تُستدعى مرة لكل frame بعد بناء FacialMetricsFrame
     */
    fun onFrame(frame: FacialMetricsFrame) {

        // تسجيل كل frame للجلسة
        sessionLog.add(frame)

        // تمرير frame لكل التحققات المفعّلة
        blinkCheck?.onFrame(frame)
        smileCheck?.onFrame(frame)
        mouthOpenCheck?.onFrame(frame)
        headYawCheck?.onFrame(frame)
        headPitchCheck?.onFrame(frame)
        photometricCheck?.onFrame(frame)
    }

    /* =========================================================
     * Final evaluation
     * ========================================================= */

    /**
     * evaluate
     *
     * تُستدعى بعد انتهاء نافذة Liveness (زمنية أو منطقية)
     */
    fun evaluate(): LivenessResult {

        if (policy.requireBlink && blinkCheck?.isPassed() != true)
            return LivenessResult.SpoofDetected

        if (policy.requireSmile && smileCheck?.isPassed() != true)
            return LivenessResult.SpoofDetected

        if (policy.requireMouthOpen && mouthOpenCheck?.isPassed() != true)
            return LivenessResult.SpoofDetected

        if (policy.requireYaw && headYawCheck?.isPassed() != true)
            return LivenessResult.SpoofDetected

        if (policy.requirePitch && headPitchCheck?.isPassed() != true)
            return LivenessResult.SpoofDetected

        if (
            policy.requirePhotometricResponse &&
            photometricCheck?.isPassed() != true
        )
            return LivenessResult.SpoofDetected

        return LivenessResult.Alive
    }

    /* =========================================================
     * Session / lifecycle
     * ========================================================= */

    fun getSessionLog(): MetricsSessionLog = sessionLog

    fun reset() {
        sessionLog.clear()
        blinkCheck?.reset()
        smileCheck?.reset()
        mouthOpenCheck?.reset()
        headYawCheck?.reset()
        headPitchCheck?.reset()
        photometricCheck?.reset()
    }
}