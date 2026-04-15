package com.mahmoud.attendify.liveness.data

/**
 * FacialMetricsFrame
 *
 * Arabic:
 * يمثل القياسات الحيوية اللحظية للوجه (Frame-by-Frame).
 *
 * هذا الكلاس:
 * - ✅ يسجل ما حدث فعليًا
 * - ❌ لا يقرر قبول / رفض
 * - ❌ لا يطبّق سياسات
 *
 * يُستخدم لـ:
 * - العرض اللحظي (Debug / Dev mode)
 * - التسجيل والتدقيق (Audit & Forensics)
 * - تحليل Liveness لاحقًا عبر Policy
 */
data class FacialMetricsFrame(

    /* =========================================================
     * Time
     * ========================================================= */

    /**
     * الزمن (ms) لحظة التقاط هذا الـ frame
     */
    val timestampMs: Long,

    /* =========================================================
     * Eyes – Right & Left
     * ========================================================= */

    /**
     * Eye Aspect Ratio للعين اليمنى
     */
    val rightEyeEAR: Double,

    /**
     * Eye Aspect Ratio للعين اليسرى
     */
    val leftEyeEAR: Double,

    /**
     * هل العين اليمنى مغلقة في هذا frame
     * (حسب threshold لحظة القياس)
     */
    val isRightEyeClosed: Boolean,

    /**
     * هل العين اليسرى مغلقة في هذا frame
     */
    val isLeftEyeClosed: Boolean,

    /* =========================================================
     * Mouth / Smile / Breath
     * ========================================================= */

    /**
     * Mouth Aspect Ratio
     * يُستخدم لـ:
     * - فتح الفم
     * - التنفس
     */
    val mouthAspectRatio: Double,

    /**
     * Smile Score عام (0.0 – 1.0)
     *
     * يمثل شدة الابتسامة الكلية
     * (بغض النظر عن الجانب)
     */
    val smileScore: Double,

    /**
     * هل المستخدم مبتسم في هذا frame
     * (Boolean مشتق وليس قرارًا نهائيًا)
     */
    val isSmiling: Boolean,

    /**
     * هل الفم مفتوح في هذا frame
     */
    val isMouthOpen: Boolean,

    /* =========================================================
     * Head Pose (Degrees)
     * ========================================================= */

    /**
     * دوران الرأس يمين / يسار
     * +ve → يمين
     * -ve → يسار
     */
    val headYawDegrees: Double,

    /**
     * رفع / خفض الرأس
     * +ve → رفع
     * -ve → خفض
     */
    val headPitchDegrees: Double,

    /**
     * ميلان الرأس (Roll)
     */
    val headRollDegrees: Double,

    /* =========================================================
     * Photometric / Lighting
     * ========================================================= */

    /**
     * متوسط الإضاءة على الوجه
     */
    val meanLuminance: Double,

    /**
     * تباين الإضاءة (Texture variance)
     *
     * مهم جدًا لاختبار:
     * - Photometric response
     * - الصور المطبوعة / الشاشات
     */
    val luminanceVariance: Double
)