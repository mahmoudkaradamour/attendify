package com.mahmoud.attendify.metrics

import android.util.Log

/**
 * FasRuntimeMetrics
 *
 * مسؤول عن تسجيل:
 * - زمن تحضير الـ Interpreter
 * - زمن inference
 * - اسم النموذج
 * - CPU vs GPU
 *
 * ❌ لا منطق
 * ✅ Logging فقط
 */
object FasRuntimeMetrics {

    fun log(
        modelId: String,
        useGpu: Boolean,
        stage: String,
        durationMs: Long
    ) {
        Log.d(
            "FAS_METRICS",
            "model=$modelId gpu=$useGpu stage=$stage time=${durationMs}ms"
        )
    }
}