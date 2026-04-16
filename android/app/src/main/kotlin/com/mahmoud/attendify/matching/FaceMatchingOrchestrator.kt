package com.mahmoud.attendify.matching

import com.mahmoud.attendify.model.EmployeeReference
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy
import com.mahmoud.attendify.policy.ThresholdResolver
import com.mahmoud.attendify.repository.EmployeeReferenceRepository

/**
 * FaceMatchingOrchestrator
 *
 * Arabic:
 * هذا الكلاس هو القلب المنطقي للمطابقة.
 * مسؤول عن:
 *
 * 1️⃣ اختيار مصدر الصورة المرجعية (LOCAL / REMOTE / HYBRID)
 * 2️⃣ التحقق من صلاحية المرجع حسب سياسة الإدارة
 * 3️⃣ احتساب المسافة (L2 Distance) بين الـ embeddings
 * 4️⃣ تطبيق العتبة (Threshold) النهائية
 * 5️⃣ إرجاع قرار مفهوم وقابل للتفسير (MatchDecision)
 *
 * ❌ لا يتعامل مع Camera
 * ❌ لا يتعامل مع Liveness
 * ❌ لا يتعامل مع UI
 *
 * ✅ منطق قرار فقط
 */
class FaceMatchingOrchestrator(
    private val policy: MatchingPolicy,
    private val referenceAccessPolicy: ReferenceAccessPolicy,
    private val repository: EmployeeReferenceRepository
) {

    /**
     * performMatch
     *
     * @param liveEmbedding
     * الـ embedding المستخرج من الكاميرا بعد نجاح الجودة و الـ liveness
     *
     * @param employeeId
     * المعرّف الفريد للموظف (للبحث عن المرجع)
     *
     * @param groupThreshold
     * عتبة المجموعة (قادمة من الإدارة / السيرفر)،
     * يمكن أن تكون null
     *
     * @return MatchDecision
     * قرار نهائي واضح للواجهة أو النظام
     */
    fun performMatch(
        liveEmbedding: FloatArray,
        employeeId: String,
        groupThreshold: Double?
    ): MatchDecision {

        // --------------------------------------------------
        // 1️⃣ Resolve reference according to admin policy
        // --------------------------------------------------
        val resolved =
            resolveReference(employeeId)
                ?: return MatchDecision.PolicyBlockedAttendance

        val (reference, source) = resolved

        // --------------------------------------------------
        // 2️⃣ Reference validation policy
        // --------------------------------------------------
        when (policy.referenceValidationPolicy) {

            ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE -> {
                // لا شيء، يُسمح دائمًا
            }

            ReferenceValidationPolicy.VALIDATE_ONCE_AT_ENROLLMENT -> {
                if (!reference.referenceQualityApproved) {
                    return MatchDecision.ReferenceImageNotApproved(source)
                }
            }

            ReferenceValidationPolicy.VALIDATE_EVERY_ATTENDANCE -> {
                if (!reference.referenceQualityApproved) {
                    return MatchDecision.PolicyBlockedAttendance
                }
            }
        }

        // --------------------------------------------------
        // 3️⃣ Resolve effective threshold (Double only)
        // --------------------------------------------------
        val effectiveThreshold =
            ThresholdResolver.resolve(
                employeeThreshold = reference.customThreshold,
                groupThreshold = groupThreshold,
                defaultThreshold = policy.defaultThreshold
            )

        // --------------------------------------------------
        // 4️⃣ Compute similarity (L2 Distance)
        // --------------------------------------------------
        val distance =
            EmbeddingDistance.l2(
                liveEmbedding,
                reference.embedding
            )

        // --------------------------------------------------
        // 5️⃣ Final decision
        // --------------------------------------------------
        return if (distance <= effectiveThreshold) {
            MatchDecision.MatchSuccess(
                similarity = distance,
                threshold = effectiveThreshold,
                referenceSource = source
            )
        } else {
            MatchDecision.NoMatch(
                similarity = distance,
                threshold = effectiveThreshold,
                referenceSource = source
            )
        }
    }

    /**
     * resolveReference
     *
     * يحدد مصدر المرجع حسب سياسة الإدارة:
     *
     * LOCAL_ONLY  → الجهاز فقط (Offline attendance)
     * REMOTE_ONLY → السيرفر فقط (تحكم مركزي صارم)
     * HYBRID      → محاولة محليًا، ثم السيرفر
     */
    private fun resolveReference(
        employeeId: String
    ): Pair<EmployeeReference, ReferenceSource>? {

        return when (referenceAccessPolicy) {

            ReferenceAccessPolicy.LOCAL_ONLY ->
                repository
                    .getLocalReference(employeeId)
                    ?.let { it to ReferenceSource.LOCAL_ENCRYPTED }

            ReferenceAccessPolicy.REMOTE_ONLY ->
                repository
                    .getRemoteReference(employeeId)
                    ?.let { it to ReferenceSource.REMOTE_SERVER }

            ReferenceAccessPolicy.HYBRID ->
                repository
                    .getLocalReference(employeeId)
                    ?.let { it to ReferenceSource.LOCAL_ENCRYPTED }
                    ?: repository
                        .getRemoteReference(employeeId)
                        ?.let { it to ReferenceSource.REMOTE_SERVER }
        }
    }
}