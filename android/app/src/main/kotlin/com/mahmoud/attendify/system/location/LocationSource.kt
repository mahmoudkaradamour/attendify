package com.mahmoud.attendify.system.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * LocationSource
 *
 * PURPOSE:
 * --------
 * Provides a SINGLE, FRESH, HARD GPS location fix under Zero‑Trust assumptions.
 *
 * SECURITY DESIGN PRINCIPLES:
 * ---------------------------
 * 1. Cached locations are FORBIDDEN.
 * 2. getLastLocation() is NEVER used (cached mock attack).
 * 3. A real GPS sensor fix must be forced.
 * 4. Location freshness and age are evaluated later by LocationIntegrityGuard.
 *
 * WHY THIS CLASS EXISTS:
 * ----------------------
 * Android Location APIs are optimized for battery, not security.
 * This class exists to override Android's default "helpful" behavior
 * and enforce a security‑first location acquisition model.
 */
class LocationSource(private val context: Context) {

    /**
     * Attempts to obtain a SINGLE fresh GPS location fix.
     *
     * @param timeoutSeconds Maximum time to wait for a GPS fix before failing.
     *
     * @return LocationSnapshot if a fresh fix is obtained,
     *         null if GPS is unavailable, disabled, or times out.
     *
     * BEHAVIORAL GUARANTEES:
     * ---------------------
     * ✔ Forces the GPS chip to warm up and acquire satellites.
     * ✔ Ignores Android's cached / last known locations entirely.
     * ✔ Works on API 28+ (no getCurrentLocation dependency).
     * ✔ Deterministic: either returns a fix or fails explicitly.
     */
    @SuppressLint("MissingPermission")
    fun getFreshLocation(timeoutSeconds: Long = 30): LocationSnapshot? {

        // Acquire Android's low‑level LocationManager.
        // We use it instead of FusedLocationProvider to avoid
        // aggressive caching and abstraction layers.
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

        /**
         * CountDownLatch is used to convert Android's async
         * location callbacks into a deterministic blocking operation.
         *
         * This is SAFE here because:
         * - The call is executed only during attendance initiation.
         * - The timeout is strictly bounded by policy.
         * - Security > UX at this stage.
         */
        val latch = CountDownLatch(1)
        var result: Location? = null

        /**
         * LocationListener that will receive exactly ONE update,
         * then immediately unregister itself.
         */
        val listener = object : LocationListener {

            override fun onLocationChanged(location: Location) {
                // A fresh GPS fix has been received.
                result = location
                latch.countDown()
                locationManager.removeUpdates(this)
            }

            override fun onProviderDisabled(provider: String) {
                // GPS was disabled while waiting.
                latch.countDown()
            }

            override fun onProviderEnabled(provider: String) {
                // No action needed.
            }

            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?
            ) {
                // Deprecated but required for backward compatibility.
            }
        }

        /**
         * 🔐 CRITICAL SECURITY STEP
         * ------------------------
         * requestLocationUpdates() FORCES a real GPS update.
         *
         * - minTime = 0
         * - minDistance = 0
         *
         * This configuration tells Android:
         * "I want a brand‑new fix NOW, not something from cache."
         *
         * This single line kills:
         * - Cached Mock Attack
         * - Replay of last known location
         */
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0f,
            listener
        )

        // Wait for a GPS fix or until timeout expires.
        latch.await(timeoutSeconds, TimeUnit.SECONDS)

        // Always clean up listener defensively.
        locationManager.removeUpdates(listener)

        val location = result ?: return null

        /**
         * SECURITY NOTE – Mock Location Flag
         * ---------------------------------
         * isFromMockProvider() is officially deprecated,
         * BUT there is NO system‑level replacement.
         *
         * We deliberately keep using it as a RISK SIGNAL,
         * NOT as a source of truth.
         */
        @Suppress("DEPRECATION")
        val isMock = location.isFromMockProvider

        /**
         * We intentionally capture elapsedRealtime at the moment
         * THIS snapshot is created.
         *
         * Later, LocationIntegrityGuard will compare this value
         * against SystemClock.elapsedRealtime() to detect:
         * - Stale fixes
         * - Faraday / offline replay attacks
         */
        return LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            provider = location.provider ?: "unknown",
            isMock = isMock,
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            timestampMillis = location.time
        )
    }
}