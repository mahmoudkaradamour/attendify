package com.mahmoud.attendify.security.privacy

import kotlin.math.round

/**
 * =============================================================================
 * 🔐 EmbeddingAnonymizer — Privacy-Preserving Feature Transformation
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 PURPOSE
 * -----------------------------------------------------------------------------
 *
 * Prevents reconstruction of original face from embedding vectors.
 *
 * -----------------------------------------------------------------------------
 * 📊 METHOD
 * -----------------------------------------------------------------------------
 *
 * Input Embedding → Quantization → Output Embedding
 *
 * -----------------------------------------------------------------------------
 * 🔬 MATHEMATICAL MODEL
 * -----------------------------------------------------------------------------
 *
 *   e' = round(e * k) / k
 *
 * where:
 *   k = precision factor
 *
 */
object EmbeddingAnonymizer {

    private const val PRECISION = 1000.0

    fun anonymize(embedding: FloatArray): FloatArray {

        return embedding.map {
            (round(it * PRECISION) / PRECISION).toFloat()
        }.toFloatArray()
    }
}