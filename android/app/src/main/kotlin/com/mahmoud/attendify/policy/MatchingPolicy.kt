package com.mahmoud.attendify.policy

/**
 * MatchingPolicy
 *
 * Arabic:
 * يمثل سياسة المطابقة التي تحددها الإدارة.
 * هذه القيم قادمة من Flutter (إعدادات المدير).
 *
 * English:
 * Represents face matching policy controlled by admin.
 */
data class MatchingPolicy(

    /**
     * Arabic:
     * سياسة التحقق من الصورة المرجعية:
     * - لا تحقق
     * - تحقق مرة واحدة
     * - تحقق دائم
     */
    val referenceValidationPolicy: ReferenceValidationPolicy,

    /**
     * Arabic:
     * القيمة الافتراضية لدرجة المطابقة.
     * تُستخدم إن لم يوجد تخصيص للموظف أو للمجموعة.
     *
     * Example:
     * 0.55 = مرن
     * 0.70 = صارم
     */
    val defaultThreshold: Float,

    /**
     * Arabic:
     * السماح بتخزين البيانات المرجعية محليًا على جهاز الموظف.
     */
    val allowLocalReferenceStorage: Boolean
)