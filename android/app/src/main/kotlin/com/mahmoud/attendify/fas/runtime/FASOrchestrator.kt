package com.mahmoud.attendify.fas.runtime

import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.fas.policy.FASPolicy

/**
 * FASOrchestrator
 *
 * ================== ROLE ==================
 * ✅ طبقة التنفيذ (Execution Layer) لنظام FAS
 *
 * مسؤول عن:
 * - تطبيق سياسة الإدارة (Policy)
 * - اختيار النموذج المناسب
 * - تهيئة النموذج (CPU / GPU / NNAPI)
 * - تنفيذ تحليل FAS
 * - تفسير النتيجة وتوحيد القرار
 *
 * ❌ لا يعرف Pre-processing
 * ❌ لا يعرف Input shape
 * ❌ لا يعرف Normalization
 *
 * كل ذلك مسؤولية Wrapper النموذج فقط.
 */
class FASOrchestrator(
    private val models: Map<String, FASModel>
) {

    /**
     * evaluate
     *
     * ينفّذ فحص FAS على وجه واحد
     * بناءً على السياسة المحلولة مسبقًا
     *
     * @param face
     * صورة الوجه بعد:
     * - Quality check
     * - Face cropping
     * - (قبل Matching)
     *
     * @param policy
     * السياسة النهائية (Employee / Group / Org)
     */
    fun evaluate(
        face: Bitmap,
        policy: FASPolicy
    ): FASDecision {

        /* --------------------------------------------------
         * 1️⃣ FAS Disabled by Policy
         * -------------------------------------------------- */
        if (!policy.enabled) {
            return FASDecision.Skipped
        }

        /* --------------------------------------------------
         * 2️⃣ Resolve Model
         * -------------------------------------------------- */
        val modelId = policy.modelId
            ?: return FASDecision.Blocked(
                reason = "FAS enabled but no model specified"
            )

        val model = models[modelId]
            ?: return FASDecision.Blocked(
                reason = "FAS model not found: $modelId"
            )

        /* --------------------------------------------------
         * 3️⃣ Prepare Interpreter (CPU / GPU / NNAPI)
         * --------------------------------------------------
         * ✅ يتم تهيئة الـ Interpreter هنا فقط
         * ✅ كل نموذج يقرر داخليًا إن كان يدعم GPU
         * ✅ في حال عدم الدعم → fallback آمن تلقائي
         */
        try {
            model.prepare(
                useGpu = policy.useGpu
            )
        } catch (e: Exception) {
            return FASDecision.Blocked(
                reason = "FAS model preparation failed: ${e.message}"
            )
        }

        /* --------------------------------------------------
         * 4️⃣ Execute FAS Analysis
         * -------------------------------------------------- */
        val fasResult = try {
            model.analyze(face)
        } catch (e: Exception) {
            return FASDecision.Blocked(
                reason = "FAS inference exception: ${e.message}"
            )
        }

        /* --------------------------------------------------
         * 5️⃣ Interpret Result
         * -------------------------------------------------- */
        return when (fasResult) {

            is FASResult.Real -> {
                val threshold =
                    policy.threshold ?: model.defaultThreshold

                if (fasResult.confidence >= threshold) {
                    FASDecision.Passed
                } else {
                    FASDecision.Blocked(
                        reason =
                            "FAS confidence (${fasResult.confidence}) " +
                                    "below threshold ($threshold)"
                    )
                }
            }

            is FASResult.Spoof -> {
                FASDecision.Blocked(
                    reason =
                        "Spoof detected with confidence " +
                                "${fasResult.confidence}"
                )
            }

            is FASResult.Inconclusive -> {
                FASDecision.Blocked(
                    reason = fasResult.reason
                )
            }
        }
    }
}