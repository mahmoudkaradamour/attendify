package com.mahmoud.attendify.matching

/**
 * MatchDecision
 *
 * قرار نهائي قابل للشرح (Explainable Decision)
 * يمكن عرضه للمستخدم أو تسجيله للإدارة.
 */
sealed class MatchDecision {

    data class MatchSuccess(
        val similarity: Double,
        val threshold: Double,
        val referenceSource: ReferenceSource
    ) : MatchDecision()

    data class NoMatch(
        val similarity: Double,
        val threshold: Double,
        val referenceSource: ReferenceSource
    ) : MatchDecision()

    data class ReferenceImageNotApproved(
        val referenceSource: ReferenceSource
    ) : MatchDecision()

    object PolicyBlockedAttendance : MatchDecision()
}

/**
 * مصدر المرجع المستخدم في المطابقة
 */
enum class ReferenceSource {
    LOCAL_ENCRYPTED,
    REMOTE_SERVER
}