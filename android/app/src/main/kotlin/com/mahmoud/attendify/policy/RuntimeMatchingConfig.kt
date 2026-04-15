package com.mahmoud.attendify.policy

/**
 * RuntimeMatchingConfig
 *
 * Arabic:
 * القواعد التي تأتي من Flutter
 * وتحدّد سلوك الحضور في الزمن الحقيقي.
 */
data class RuntimeMatchingConfig(

    /**
     * هل يتم فحص جودة الصورة الحية؟
     */
    val enableLiveImageQualityCheck: Boolean,

    /**
     * سياسة التحقق من الصورة المرجعية.
     */
    val referenceValidationPolicy: ReferenceValidationPolicy,

    /**
     * threshold الافتراضي للشركة.
     */
    val defaultThreshold: Float,

    /**
     * threshold خاص بالمجموعة (اختياري).
     */
    val groupThreshold: Float? = null
)