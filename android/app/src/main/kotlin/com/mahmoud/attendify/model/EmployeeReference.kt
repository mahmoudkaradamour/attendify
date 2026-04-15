package com.mahmoud.attendify.model

/**
 * EmployeeReference
 *
 * Arabic:
 * بيانات الوجه المرجعية لموظف.
 */
data class EmployeeReference(

    /**
     * معرف الموظف
     */
    val employeeId: String,

    /**
     * Embedding الوجه المرجعي (128D غالبًا)
     */
    val embedding: FloatArray,

    /**
     * هل الصورة المرجعية اجتازت فحص الجودة عند الإدراج؟
     */
    val referenceQualityApproved: Boolean,

    /**
     * Threshold مخصص للموظف (اختياري)
     */
    val customThreshold: Float? = null,

    /**
     * معرف المجموعة (اختياري)
     */
    val groupId: String? = null
)