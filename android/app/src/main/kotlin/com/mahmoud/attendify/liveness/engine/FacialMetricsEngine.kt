package com.mahmoud.attendify.liveness.engine

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame
import kotlin.math.hypot

/**
 * FacialMetricsEngine
 *
 * Arabic:
 * يحوّل Face Landmarks إلى قياسات رقمية
 * لكل frame بدون أي منطق قبول/رفض.
 */
class FacialMetricsEngine {

    fun buildFrame(
        timestampMs: Long,

        // Eyes EAR
        rightEyeEAR: Double,
        leftEyeEAR: Double,
        rightEyeClosed: Boolean,
        leftEyeClosed: Boolean,

        // Mouth & Smile
        mouthAspectRatio: Double,
        smileScore: Double,
        mouthOpen: Boolean,

        // Head pose
        yaw: Double,
        pitch: Double,
        roll: Double,

        // Lighting
        meanLuminance: Double,
        luminanceVariance: Double
    ): FacialMetricsFrame {

        return FacialMetricsFrame(
            timestampMs = timestampMs,
            rightEyeEAR = rightEyeEAR,
            leftEyeEAR = leftEyeEAR,
            isRightEyeClosed = rightEyeClosed,
            isLeftEyeClosed = leftEyeClosed,
            mouthAspectRatio = mouthAspectRatio,
            smileScore = smileScore,
            isSmiling = smileScore > 0.0,
            isMouthOpen = mouthOpen,
            headYawDegrees = yaw,
            headPitchDegrees = pitch,
            headRollDegrees = roll,
            meanLuminance = meanLuminance,
            luminanceVariance = luminanceVariance
        )
    }
}