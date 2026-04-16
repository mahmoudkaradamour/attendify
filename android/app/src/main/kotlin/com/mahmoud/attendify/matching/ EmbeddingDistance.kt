package com.mahmoud.attendify.matching

import kotlin.math.sqrt

/**
 * EmbeddingDistance
 *
 * مسؤول فقط عن الحساب الرياضي.
 * لا سياسات، لا قرارات.
 */
object EmbeddingDistance {

    fun l2(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) {
            "Embedding size mismatch"
        }

        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}