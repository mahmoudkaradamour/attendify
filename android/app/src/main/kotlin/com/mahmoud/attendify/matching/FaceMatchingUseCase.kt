package com.mahmoud.attendify.matching

import com.mahmoud.attendify.model.EmployeeReference
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceValidationPolicy

/**
 * FaceMatchingUseCase
 *
 * Arabic:
 * طبقة وسيطة تُستخدم من:
 * - MainActivity
 * - MethodChannel
 * - أي Service مستقبلي
 *
 * هي التي تنشئ orchestrator وتنفذ المطابقة.
 *
 * English:
 * Application-level use case for face matching.
 */
class FaceMatchingUseCase(
    private val policy: MatchingPolicy,
    private val groupThreshold: Float?
) {

    private val orchestrator =
        FaceMatchingOrchestrator(policy)

    /**
     * matchNow
     *
     * @param liveEmbedding embedding مستخرج من صورة الكاميرا
     * @param reference بيانات الموظف المرجعية
     *
     * @return MatchDecision قرار واضح يمكن عرضه للواجهة
     */
    fun matchNow(
        liveEmbedding: FloatArray,
        reference: EmployeeReference
    ): MatchDecision {

        return orchestrator.performMatch(
            liveEmbedding = liveEmbedding,
            reference = reference,
            groupThreshold = groupThreshold
        )
    }
}