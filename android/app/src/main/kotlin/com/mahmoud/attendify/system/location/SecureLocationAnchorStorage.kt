package com.mahmoud.attendify.system.location

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureLocationAnchorStorage(context: Context) : LocationAnchorStorage {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_location_anchor",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun hasLastLocation(): Boolean =
        prefs.contains("lat") && prefs.contains("lon") && prefs.contains("elapsed")

    override fun loadLastLocation(): LocationSnapshot {
        return LocationSnapshot(
            latitude = Double.fromBits(prefs.getLong("lat", 0)),
            longitude = Double.fromBits(prefs.getLong("lon", 0)),
            accuracyMeters = prefs.getFloat("acc", 0f),
            provider = prefs.getString("provider", "") ?: "",
            isMock = false,
            elapsedRealtimeMillis = prefs.getLong("elapsed", 0),
            timestampMillis = prefs.getLong("ts", 0)
        )
    }

    override fun saveLastLocation(snapshot: LocationSnapshot) {
        prefs.edit()
            .putLong("lat", snapshot.latitude.toBits())
            .putLong("lon", snapshot.longitude.toBits())
            .putFloat("acc", snapshot.accuracyMeters)
            .putString("provider", snapshot.provider)
            .putLong("elapsed", snapshot.elapsedRealtimeMillis)
            .putLong("ts", snapshot.timestampMillis)
            .apply()
    }

    override fun clear() = prefs.edit().clear().apply()
}