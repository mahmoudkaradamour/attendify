package com.mahmoud.attendify.liveness.engine

import android.graphics.Bitmap
import com.mahmoud.attendify.liveness.data.FacialMetricsFrame

/**
 * FacialMetricsEngine
 *
 * Arabic:
 * هذا الكلاس هو المكان الوحيد المسموح له:
 * - حساب القياسات (Metrics) الخاصة بالوجه
 * - أو تجميعها من مصادر مختلفة (Landmarks / Pose / Lighting)
 *
 * ملاحظات معمارية مهمة:
 * ✅ لا يحتوي أي منطق قبول/رفض (ليس مسؤولًا عن القرار)
 * ✅ لا يعرف شيئًا عن LivenessPolicy
 * ✅ لا يعرف شيئًا عن UI أو Camera lifecycle
 * ✅ يُنتج فقط "قياسات رقمية" لكل frame
 *
 * لماذا هذا مهم؟
 * - لتفادي تكرار الحسابات في MainActivity
 * - لعزل الحساب الرياضي عن منطق النظام
 * - لتسهيل اختبار هذا الجزء منفصلًا
 */
class FacialMetricsEngine {

    /* =========================================================
     * Public API — ما تستدعيه MainActivity فقط
     * ========================================================= */

    /**
     * computeFrameFromBitmap
     *
     * Arabic:
     * هذه هي الدالة الوحيدة التي يجب أن يستدعيها MainActivity.
     * تستقبل صورة الوجه (بعد القص)،
     * وتقوم داخليًا بحساب أو تجميع كل القياسات،
     * ثم تُرجع FacialMetricsFrame جاهزًا للاستخدام.
     *
     * ملاحظة:
     * القيم المستخدمة هنا هي PLACEHOLDERS صحيحة النوع.
     * يمكنك لاحقًا استبدال أي حساب بآخر أدق دون تغيير أي API.
     */
    fun computeFrameFromBitmap(faceBitmap: Bitmap): FacialMetricsFrame {

        // =====================================================
        // 1️⃣ Timestamp
        // =====================================================
        val timestampMs = System.currentTimeMillis()

        // =====================================================
        // 2️⃣ Eye metrics (EAR - Eye Aspect Ratio)
        // =====================================================
        // هذه القيم مثال فقط — لاحقًا تُحسب من landmarks
        val rightEyeEAR = 0.30
        val leftEyeEAR = 0.29

        val rightEyeClosed = rightEyeEAR < 0.25
        val leftEyeClosed = leftEyeEAR < 0.25

        // =====================================================
        // 3️⃣ Mouth & Smile
        // =====================================================
        val mouthAspectRatio = 0.52
        val mouthOpen = mouthAspectRatio > 0.50

        val smileScore = 0.0  // 0 = لا ابتسامة

        // =====================================================
        // 4️⃣ Head pose (yaw / pitch / roll)
        // =====================================================
        // درجات — لاحقًا تُحسب من نموذج pose
        val yaw = 0.0
        val pitch = 0.0
        val roll = 0.0

        // =====================================================
        // 5️⃣ Photometric (Lighting)
        // =====================================================
        val meanLuminance = 120.0
        val luminanceVariance = 15.0

        // =====================================================
        // 6️⃣ تجميع كل القيم في Frame واحد
        // =====================================================
        return buildFrame(
            timestampMs = timestampMs,

            // Eyes
            rightEyeEAR = rightEyeEAR,
            leftEyeEAR = leftEyeEAR,
            rightEyeClosed = rightEyeClosed,
            leftEyeClosed = leftEyeClosed,

            // Mouth & Smile
            mouthAspectRatio = mouthAspectRatio,
            smileScore = smileScore,
            mouthOpen = mouthOpen,

            // Head pose
            yaw = yaw,
            pitch = pitch,
            roll = roll,

            // Lighting
            meanLuminance = meanLuminance,
            luminanceVariance = luminanceVariance
        )
    }

    /* =========================================================
     * Internal assembler — لا يُستدعى من MainActivity
     * ========================================================= */

    /**
     * buildFrame
     *
     * Arabic:
     * هذه الدالة مسؤولة فقط عن:
     * - تحويل القيم الخام إلى FacialMetricsFrame
     * - بدون أي حساب إضافي
     *
     * الفصل بين computeFrameFromBitmap و buildFrame
     * يسمح مستقبلًا بتغذية هذه الدالة من:
     * - كاميرا RGB
     * - كاميرا IR
     * - Sensors أخرى
     */
    fun buildFrame(
        timestampMs: Long,

        // Eyes
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

            // Eyes
            rightEyeEAR = rightEyeEAR,
            leftEyeEAR = leftEyeEAR,
            isRightEyeClosed = rightEyeClosed,
            isLeftEyeClosed = leftEyeClosed,

            // Mouth & Smile
            mouthAspectRatio = mouthAspectRatio,
            smileScore = smileScore,
            isSmiling = smileScore > 0.0,
            isMouthOpen = mouthOpen,

            // Head pose
            headYawDegrees = yaw,
            headPitchDegrees = pitch,
            headRollDegrees = roll,

            // Lighting
            meanLuminance = meanLuminance,
            luminanceVariance = luminanceVariance
        )
    }
}