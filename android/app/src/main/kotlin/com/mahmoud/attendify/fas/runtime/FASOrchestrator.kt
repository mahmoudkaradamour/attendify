package com.mahmoud.attendify.fas.runtime

import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.fas.policy.FASPolicy

/**
 * FASOrchestrator
 *
 * =========================================================
 * ✅ English:
 * ---------------------------------------------------------
 * Simplified and hardened execution layer for Face
 * Anti‑Spoofing (FAS).
 *
 * Responsibilities:
 * - Apply administrative policy (enable / disable)
 * - Resolve the requested model strictly by ID
 * - Prepare the model interpreter (CPU / GPU)
 * - Execute FAS analysis
 * - Translate FASResult → FASDecision
 *
 * ❌ Explicitly does NOT:
 * - Perform thresholding
 * - Apply Softmax
 * - Interpret logits
 * - Contain any ML decision logic
 *
 * All intelligence resides inside BaseFASModel.
 *
 * =========================================================
 * ✅ عربي:
 * ---------------------------------------------------------
 * طبقة التنفيذ النهائية والمبسّطة لنظام مكافحة تزوير الوجه.
 *
 * المسؤوليات:
 * - تطبيق سياسة الإدارة (تفعيل / تعطيل)
 * - اختيار النموذج المطلوب بدقة عبر المعرّف
 * - تهيئة الـ Interpreter (CPU / GPU)
 * - تنفيذ تحليل FAS
 * - تحويل FASResult إلى FASDecision
 *
 * ❌ لا يقوم بـ:
 * - حساب العتبة (Threshold)
 * - تنفيذ Softmax
 * - تفسير Logits
 * - أي قرار ذكاء اصطناعي
 *
 * كل الذكاء مركزي داخل BaseFASModel.
 */
class FASOrchestrator(
    private val models: Map<String, FASModel>
) {

    /**
     * Evaluate Face Anti‑Spoofing for a single face.
     *
     * =====================================================
     * English:
     * Executes the full FAS flow for one face image
     * according to the resolved policy.
     *
     * عربي:
     * تنفيذ فحص مقاومة التزوير لوجه واحد
     * بناءً على السياسة النهائية.
     */
    fun evaluate(
        face: Bitmap,
        policy: FASPolicy
    ): FASDecision {

        /* --------------------------------------------------
         * 1️⃣ FAS Disabled by Policy
         * --------------------------------------------------
         *
         * English:
         * If FAS is explicitly disabled, skip safely.
         *
         * عربي:
         * إذا تم تعطيل FAS من السياسة، يتم التخطي بأمان.
         */
        if (!policy.enabled) {
            return FASDecision.Skipped
        }

        /* --------------------------------------------------
         * 2️⃣ Resolve Model (STRICT – NO FALLBACK)
         * --------------------------------------------------
         *
         * English:
         * The requested model MUST exist.
         * Any fallback to a different / weaker model
         * is considered a security violation.
         *
         * عربي:
         * يجب أن يكون النموذج المطلوب موجودًا.
         * أي تراجع تلقائي لنموذج أضعف يُعد خرقًا أمنيًا.
         */
        val modelId = policy.modelId
            ?: return FASDecision.Blocked(
                reason = "FAS enabled but no model specified in policy"
            )

        val model = models[modelId]
            ?: return FASDecision.Blocked(
                reason =
                    "Required security model '$modelId' " +
                            "is not available or disabled"
            )

        /* --------------------------------------------------
         * 3️⃣ Prepare Interpreter (CPU / GPU)
         * --------------------------------------------------
         *
         * English:
         * Interpreter preparation is handled by the model.
         *
         * عربي:
         * تهيئة الـ Interpreter تتم داخل النموذج نفسه.
         */
        try {
            model.prepare(useGpu = policy.useGpu)
        } catch (e: Exception) {
            return FASDecision.Blocked(
                reason = "FAS model preparation failed: ${e.message}"
            )
        }

        /* --------------------------------------------------
         * 4️⃣ Execute FAS Analysis
         * --------------------------------------------------
         *
         * English:
         * Inference execution is fully encapsulated
         * by the BaseFASModel.
         *
         * عربي:
         * تنفيذ التنبؤ يتم بالكامل داخل BaseFASModel.
         */
        val result = try {
            model.analyze(face)
        } catch (e: Exception) {
            return FASDecision.Blocked(
                reason = "FAS inference exception: ${e.message}"
            )
        }

        /* --------------------------------------------------
         * 5️⃣ Translate Result
         * --------------------------------------------------
         *
         * English:
         * Convert ML result into a system‑level decision.
         *
         * عربي:
         * تحويل نتيجة الـ ML إلى قرار واضح للنظام.
         */
        return when (result) {

            is FASResult.Real ->
                FASDecision.Passed

            is FASResult.Spoof ->
                FASDecision.Blocked(
                    reason =
                        "Spoof detected " +
                                "(confidence=${result.confidence})"
                )

            is FASResult.Inconclusive ->
                FASDecision.Blocked(
                    reason = result.reason
                )
        }
    }
}