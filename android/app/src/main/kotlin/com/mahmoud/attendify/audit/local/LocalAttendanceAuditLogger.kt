package com.mahmoud.attendify.audit.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mahmoud.attendify.audit.AttendanceAuditLog
import com.mahmoud.attendify.audit.AttendanceAuditLogger
import org.json.JSONArray
import org.json.JSONObject

class LocalEncryptedAttendanceAuditLogger(
    context: Context
) : AttendanceAuditLogger {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "attendance_audit_logs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val KEY = "logs"

    override fun log(entry: AttendanceAuditLog) {
        val current = JSONArray(prefs.getString(KEY, "[]")!!)
        current.put(
            JSONObject().apply {
                put("employeeId", entry.employeeId)
                put("timestampMs", entry.timestampMs)
                put("similarity", entry.similarity)
                put("threshold", entry.threshold)
                put("referenceSource", entry.referenceSource.name)
                put("decision", entry.decision)
            }
        )
        prefs.edit().putString(KEY, current.toString()).apply()
    }

    override fun getPendingLogs(): List<AttendanceAuditLog> {
        val json = JSONArray(prefs.getString(KEY, "[]")!!)
        val result = mutableListOf<AttendanceAuditLog>()

        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            result.add(
                AttendanceAuditLog(
                    employeeId = o.getString("employeeId"),
                    timestampMs = o.getLong("timestampMs"),
                    similarity = if (o.isNull("similarity")) null else o.getDouble("similarity"),
                    threshold  = if (o.isNull("threshold")) null else o.getDouble("threshold"),
                    referenceSource =
                        com.mahmoud.attendify.matching.ReferenceSource.valueOf(
                            o.getString("referenceSource")
                        ),
                    decision = o.getString("decision")
                )
            )
        }
        return result
    }

    override fun clearPendingLogs() {
        prefs.edit().remove(KEY).apply()
    }
}