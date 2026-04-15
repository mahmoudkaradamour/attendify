package com.mahmoud.attendify.liveness.result

/**
 * LivenessResult
 *
 * Arabic:
 * نتيجة فحص الحياة.
 */
sealed class LivenessResult {

    /**
     * الوجه حي – كل الفحوصات المطلوبة نجحت
     */
    object Alive : LivenessResult()

    /**
     * فشل أحد فحوصات الحياة
     */
    object SpoofDetected : LivenessResult()
}