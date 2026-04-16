package com.mahmoud.attendify.matching

import kotlin.math.sqrt

object EmbeddingSimilarity {

    /**
     * Cosine similarity بين embeddingين
     *
     * القيمة:
     * - 1.0  → مطابق جدًا
     * - 0.0  → غير مرتبط
     */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) {
            "Embeddings size mismatch"
        }

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return dot / (sqrt(normA) * sqrt(normB))
    }
}