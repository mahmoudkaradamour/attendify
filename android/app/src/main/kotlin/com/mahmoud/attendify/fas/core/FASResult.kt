package com.mahmoud.attendify.fas.core

sealed class FASResult {

    data class Real(
        val confidence: Float
    ) : FASResult()

    data class Spoof(
        val confidence: Float
    ) : FASResult()

    data class Inconclusive(
        val reason: String
    ) : FASResult()
}