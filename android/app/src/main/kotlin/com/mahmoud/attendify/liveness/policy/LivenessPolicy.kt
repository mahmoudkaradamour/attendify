package com.mahmoud.attendify.liveness.policy

/**
 * LivenessPolicy
 *
 * Arabic:
 * سياسة تحدد:
 * - ما هي التحققات المطلوبة
 * - وما هي الدرجات الدنيا المقبولة
 *
 * ملاحظة:
 * كل هذه القيم ستأتي لاحقًا من Flutter (الإدارة)
 */
data class LivenessPolicy(

    /* ===================== Blink ===================== */

    val requireBlink: Boolean,
    val minBlinkCount: Int,

    /**
     * هل يُسمح بالرمش المتزامن للعينين؟
     */
    val allowSimultaneousBlink: Boolean,

    /**
     * EAR threshold أسفلها تُعتبر العين مغلقة
     */
    val eyeClosedThreshold: Double,

    /* ===================== Smile ===================== */

    val requireSmile: Boolean,

    /**
     * أقل درجة ابتسامة مقبولة (0.0 – 1.0)
     */
    val minSmileScore: Double,

    /**
     * أقل مدة (ms) يجب أن تستمر الابتسامة
     */
    val minSmileDurationMs: Long,

    /* ===================== Mouth / Breath ===================== */

    val requireMouthOpen: Boolean,

    /**
     * عدد الإطارات التي يجب أن يكون الفم فيها مفتوحًا
     */
    val minMouthOpenFrames: Int,

    /* ===================== Head Movement ===================== */

    val requireYaw: Boolean,
    val minYawDegrees: Double,

    val requirePitch: Boolean,
    val minPitchDegrees: Double,

    /* ===================== Photometric ===================== */

    val requirePhotometricResponse: Boolean,

    /**
     * التغير الأدنى في تباين الإضاءة لقبول الاستجابة الضوئية
     */
    val luminanceVarianceThreshold: Double
)