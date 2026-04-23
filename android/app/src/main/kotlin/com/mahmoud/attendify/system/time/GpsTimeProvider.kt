package com.mahmoud.attendify.system.time

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

/**
 * GpsTimeProvider
 *
 * Provides GPS-based UTC time as an integrity witness.
 *
 * IMPORTANT:
 * - GPS time is NEVER used as a sole acceptance factor
 * - It is used only to reinforce trust or forensic evidence
 */
class GpsTimeProvider(
    private val context: Context
) {

    /**
     * Attempts to retrieve GPS UTC timestamp if available.
     *
     * @return UTC millis from GPS, or null if unavailable
     */
    @SuppressLint("MissingPermission")
    fun getGpsUtcTimeIfAvailable(): Long? {

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

        // We do NOT request updates here.
        // We only read last known location to avoid UX and battery cost.
        val location: Location =
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: return null

        val gpsTime = location.time
        return if (gpsTime > 0) gpsTime else null
    }
}
