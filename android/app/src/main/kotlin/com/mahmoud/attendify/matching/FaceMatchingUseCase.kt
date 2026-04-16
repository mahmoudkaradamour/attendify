package com.mahmoud.attendify.matching

import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ReferenceAccessPolicy
import com.mahmoud.attendify.repository.EmployeeReferenceRepository

/**
 * FaceMatchingUseCase
 *
 * طبقة استعمال (Application Layer).
 * تُستدعى من:
 * - MainActivity
 * - Service
 * - MethodChannel
 */
class FaceMatchingUseCase(
    private val policy: MatchingPolicy,
    private val referenceAccessPolicy: ReferenceAccessPolicy,
    private val repository: EmployeeReferenceRepository,
    private val groupThreshold: Double?
) {

    private val orchestrator =
        FaceMatchingOrchestrator(
            policy = policy,
            referenceAccessPolicy = referenceAccessPolicy,
            repository = repository
        )

    /**
     * matchNow
     *
     * @param liveEmbedding embedding من الكاميرا
     * @param employeeId معرف الموظف (نبحث به عن المرجع)
     */
    fun matchNow(
        liveEmbedding: FloatArray,
        employeeId: String
    ): MatchDecision {

        return orchestrator.performMatch(
            liveEmbedding = liveEmbedding,
            employeeId = employeeId,
            groupThreshold = groupThreshold
        )
    }
}