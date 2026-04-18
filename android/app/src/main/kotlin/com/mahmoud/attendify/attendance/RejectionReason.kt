package com.mahmoud.attendify.attendance

import com.mahmoud.attendify.matching.MatchDecision

sealed class RejectionReason {

    data class FaceMismatch(
        val matchDecision: MatchDecision
    ) : RejectionReason()

    data class FASRejected(
        val reason: String
    ) : RejectionReason()
}