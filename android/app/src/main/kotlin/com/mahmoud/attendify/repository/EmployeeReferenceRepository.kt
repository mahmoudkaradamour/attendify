
package com.mahmoud.attendify.repository

import com.mahmoud.attendify.model.EmployeeReference

interface EmployeeReferenceRepository {

    fun getLocalReference(employeeId: String): EmployeeReference?

    fun saveLocalReference(reference: EmployeeReference)

    fun clearLocalReference(employeeId: String)
}
