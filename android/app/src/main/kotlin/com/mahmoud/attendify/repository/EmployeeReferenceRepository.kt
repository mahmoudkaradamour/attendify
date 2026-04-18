package com.mahmoud.attendify.repository

import com.mahmoud.attendify.model.EmployeeReference

/**
 * EmployeeReferenceRepository
 *
 * ✅ عقد موحّد للوصول إلى المراجع البيومترية
 * ✅ يدعم Local و Remote (مستقبلاً)
 * ✅ يُستخدم من Matching layer فقط
 */
interface EmployeeReferenceRepository {

    /**
     * قراءة مرجع مخزن محليًا
     */
    fun getLocalReference(
        employeeId: String
    ): EmployeeReference?

    /**
     * حفظ / تحديث مرجع محلي
     */
    fun saveLocalReference(
        reference: EmployeeReference
    )

    /**
     * حذف مرجع محلي
     */
    fun clearLocalReference(
        employeeId: String
    )

    /**
     * ⚠️ Remote غير منفذ بعد
     * وُضع للعقد المستقبلي (HYBRID / REMOTE)
     */
    fun getRemoteReference(
        employeeId: String
    ): EmployeeReference? = null
}