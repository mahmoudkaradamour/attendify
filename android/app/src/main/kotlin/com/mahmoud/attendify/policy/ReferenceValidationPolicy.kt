package com.mahmoud.attendify.policy

/**
 * ReferenceValidationPolicy
 *
 * Arabic:
 * تحدد متى يتم التحقق من الصورة المرجعية للموظف.
 *
 * English:
 * Defines when reference image validation is applied.
 */
enum class ReferenceValidationPolicy {

    /**
     * لا يتم التحقق من الصورة المرجعية إطلاقًا أثناء تسجيل الحضور.
     * يفترض أنه تم اعتمادها مسبقًا عند الإدراج.
     */
    NEVER_VALIDATE_AT_ATTENDANCE,

    /**
     * يتم التحقق من الصورة المرجعية مرة واحدة فقط
     * عند إدراجها في النظام.
     */
    VALIDATE_ONCE_AT_ENROLLMENT,

    /**
     * يتم التحقق من الصورة المرجعية
     * عند كل عملية تسجيل حضور.
     */
    VALIDATE_EVERY_ATTENDANCE
}