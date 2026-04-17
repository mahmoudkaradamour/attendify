package com.mahmoud.attendify.ml

/**
 * GpuPolicy
 *
 * سياسة تفعيل GPU القادمة من الإدارة.
 */
enum class GpuPolicy {
    FORCED_ON,    // مفعل إجباريًا
    FORCED_OFF,   // ممنوع إجباريًا
    USER_CHOICE   // القرار بيد المستخدم
}