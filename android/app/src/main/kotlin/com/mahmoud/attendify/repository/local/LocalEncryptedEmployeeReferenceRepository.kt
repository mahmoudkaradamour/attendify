package com.mahmoud.attendify.repository.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mahmoud.attendify.model.EmployeeReference
import com.mahmoud.attendify.repository.EmployeeReferenceRepository
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.Base64

class LocalEncryptedEmployeeReferenceRepository(
    context: Context
) : EmployeeReferenceRepository {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "employee_references",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getLocalReference(employeeId: String): EmployeeReference? {
        val json = prefs.getString(employeeId, null) ?: return null
        val o = JSONObject(json)

        val bytes = Base64.getDecoder().decode(o.getString("embedding"))
        val buffer = ByteBuffer.wrap(bytes)
        val embedding = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(embedding)

        return EmployeeReference(
            employeeId = o.getString("employeeId"),
            embedding = embedding,
            referenceQualityApproved = o.getBoolean("approved"),
            customThreshold =
                if (o.has("threshold")) o.getDouble("threshold") else null
        )
    }

    override fun saveLocalReference(reference: EmployeeReference) {
        val buffer = ByteBuffer.allocate(reference.embedding.size * 4)
        buffer.asFloatBuffer().put(reference.embedding)

        val json = JSONObject().apply {
            put("employeeId", reference.employeeId)
            put("embedding", Base64.getEncoder().encodeToString(buffer.array()))
            put("approved", reference.referenceQualityApproved)
            reference.customThreshold?.let { put("threshold", it) }
        }

        prefs.edit().putString(reference.employeeId, json.toString()).apply()
    }

    override fun clearLocalReference(employeeId: String) {
        prefs.edit().remove(employeeId).apply()
    }
}