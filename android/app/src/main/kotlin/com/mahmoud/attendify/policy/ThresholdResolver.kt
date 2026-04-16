package com.mahmoud.attendify.policy

/**
 * ThresholdResolver
 *
 * Arabic:
 * مسؤول عن تحديد العتبة النهائية للمطابقة
 * وفق التسلسل التالي (من الأعلى أولوية إلى الأدنى):
 *
 * 1️⃣ عتبة الموظف (Employee specific)
 * 2️⃣ عتبة المجموعة (Group specific)
 * 3️⃣ العتبة الافتراضية (System default)
 *
 * جميع القيم من النوع Double.
 */
object ThresholdResolver {

    /**
     * resolve
     *
     * @param employeeThreshold
     * عتبة مخصصة لموظف معين (قد تكون null)
     *
     * @param groupThreshold
     * عتبة مخصصة لمجموعة موظفين (قد تكون null)
     *
     * @param defaultThreshold
     * العتبة الافتراضية للنظام (غير null)
     *
     * @return Double
     * العتبة النهائية التي ستُستخدم في المطابقة
     */
    fun resolve(
        employeeThreshold: Double?,
        groupThreshold: Double?,
        defaultThreshold: Double
    ): Double {

        return employeeThreshold
            ?: groupThreshold
            ?: defaultThreshold
    }
}