package com.mahmoud.attendify.matching

import com.mahmoud.attendify.model.EmployeeReference
import com.mahmoud.attendify.policy.MatchingPolicy
import com.mahmoud.attendify.policy.ThresholdResolver
import com.mahmoud.attendify.policy.ReferenceValidationPolicy

import kotlin.math.sqrt

/**
 * FaceMatchingOrchestrator
 *
 * Arabic:
 * ينفذ المطابقة النهائية مع احترام سياسة الإدارة.
 *
 * English:
 * Executes final face matching with policy integration.
 */
class FaceMatchingOrchestrator(
    private val policy: MatchingPolicy
) {

    /**
     * performMatch
     *
     * @param liveEmbedding embedding من الكاميرا
     * @param reference بيانات الموظف المرجعية
     * @param groupThreshold threshold المجموعة (من Flutter/Server)
     */
    fun performMatch(
        liveEmbedding: FloatArray,
        reference: EmployeeReference,
        groupThreshold: Float?
    ): MatchDecision {

        // ----------------------------
        // 1️⃣ Reference validation policy
        // ----------------------------
        when (policy.referenceValidationPolicy) {

            ReferenceValidationPolicy.NEVER_VALIDATE_AT_ATTENDANCE -> {
                // لا شيء
            }

            ReferenceValidationPolicy.VALIDATE_ONCE_AT_ENROLLMENT -> {
                if (!reference.referenceQualityApproved) {
                    return MatchDecision.ReferenceImageNotApproved
                }
            }

            ReferenceValidationPolicy.VALIDATE_EVERY_ATTENDANCE -> {
                if (!reference.referenceQualityApproved) {
                    return MatchDecision.PolicyBlockedAttendance
                }
            }
        }

        // ----------------------------
        // 2️⃣ Resolve effective threshold
        // ----------------------------
        val effectiveThreshold =
            ThresholdResolver.resolve(
                employeeThreshold = reference.customThreshold,
                groupThreshold = groupThreshold,
                defaultThreshold = policy.defaultThreshold
            )

        // ----------------------------
        // 3️⃣ Compute similarity
        // ----------------------------
        val distance =
            l2Distance(liveEmbedding, reference.embedding)

        // ----------------------------
        // 4️⃣ Final decision
        // ----------------------------
        return if (distance <= effectiveThreshold) {
            MatchDecision.MatchSuccess
        } else {
            MatchDecision.NoMatch
        }
    }

    /**
     * L2 distance between embeddings
     */
    private fun l2Distance(
        a: FloatArray,
        b: FloatArray
    ): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}