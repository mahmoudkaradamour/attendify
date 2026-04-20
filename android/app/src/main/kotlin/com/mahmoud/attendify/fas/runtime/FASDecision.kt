package com.mahmoud.attendify.fas.runtime

sealed class FASDecision {
    object Passed : FASDecision()
    object Skipped : FASDecision()
    data class Blocked(val reason: String) : FASDecision()
}