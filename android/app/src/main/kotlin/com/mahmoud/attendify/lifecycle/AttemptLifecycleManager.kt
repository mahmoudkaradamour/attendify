package com.mahmoud.attendify.attendance.lifecycle

import android.content.Context

class AttemptLifecycleManager(
    private val context: Context
) {

    private val storage = context.getSharedPreferences("attempt_lifecycle", Context.MODE_PRIVATE)

    fun markInitiated(employeeId: String, timestamp: Long) {
        storage.edit()
            .putString("status", AttemptStatus.INITIATED.name)
            .putLong("timestamp", timestamp)
            .putString("employeeId", employeeId)
            .apply()
    }

    fun markFinal(status: AttemptStatus) {
        storage.edit()
            .putString("status", status.name)
            .apply()
    }

    fun detectUnfinishedAttempt(): Boolean {
        val status = storage.getString("status", null)
        return status == AttemptStatus.INITIATED.name
    }

    fun clear() {
        storage.edit().clear().apply()
    }
}