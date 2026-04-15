package com.mahmoud.attendify.policy

/**
 * ThresholdResolver
 *
 * Arabic:
 * يحدد درجة المطابقة الفعالة لموظف معيّن.
 *
 * English:
 * Resolves effective matching threshold.
 */
object ThresholdResolver {

    /**
     * @param employeeThreshold قيمة مخصّصة لموظف (إن وجدت)
     * @param groupThreshold قيمة مخصّصة لمجموعة (إن وجدت)
     * @param defaultThreshold القيمة الافتراضية للشركة
     */
    fun resolve(
        employeeThreshold: Float?,
        groupThreshold: Float?,
        defaultThreshold: Float
    ): Float {
        return employeeThreshold
            ?: groupThreshold
            ?: defaultThreshold
    }
}