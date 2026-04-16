package com.mahmoud.attendify.policy

/**
 * MatchingPolicy
 *
 * Arabic:
 * سياسة المطابقة المعرفة من قبل الإدارة.
 * تمثل القواعد العامة التي تحكم:
 * - العتبة الافتراضية للمطابقة
 * - سياسة التحقق من المرجع
 *
 * ملاحظة مهمة:
 * جميع القيم العددية هنا من النوع Double
 * لتفادي أي تعارض Float / Double عبر النظام.
 */
data class MatchingPolicy(

    /**
     * defaultThreshold
     *
     * العتبة الافتراضية للمطابقة (L2 Distance).
     * تُستخدم إذا لم يكن هناك:
     * - عتبة خاصة بالموظف
     * - ولا عتبة خاصة بالمجموعة
     *
     * القيمة الأقل = تطابق أقوى
     */
    val defaultThreshold: Double,

    /**
     * referenceValidationPolicy
     *
     * سياسة التحقق من جودة الصورة المرجعية:
     * - NEVER_VALIDATE_AT_ATTENDANCE
     * - VALIDATE_ONCE_AT_ENROLLMENT
     * - VALIDATE_EVERY_ATTENDANCE
     */
    val referenceValidationPolicy: ReferenceValidationPolicy
)