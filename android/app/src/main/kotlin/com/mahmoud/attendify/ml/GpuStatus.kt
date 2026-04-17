package com.mahmoud.attendify.ml

/**
 * GpuStatus
 *
 * حالة GPU الفعلية أثناء التشغيل.
 */
enum class GpuStatus {
    ENABLED,
    DISABLED,
    FAILED_FALLBACK_CPU
}