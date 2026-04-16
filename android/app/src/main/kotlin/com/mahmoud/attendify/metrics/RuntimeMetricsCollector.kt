package com.mahmoud.attendify.metrics

object RuntimeMetricsCollector {

    private var processed = 0
    private var dropped = 0
    private var totalInferenceMs = 0L

    fun recordInference(durationMs: Long) {
        processed++
        totalInferenceMs += durationMs
    }

    fun recordDrop() {
        dropped++
    }

    fun snapshot(): Map<String, Any> {
        val avg =
            if (processed > 0)
                totalInferenceMs / processed
            else 0

        return mapOf(
            "processedFrames" to processed,
            "droppedFrames" to dropped,
            "avgInferenceMs" to avg
        )
    }
}