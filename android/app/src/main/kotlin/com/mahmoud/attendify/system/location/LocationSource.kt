package com.mahmoud.attendify.system.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ============================================================================
 * LocationSource — FINAL (Military‑Grade Implementation)
 * ============================================================================
 *
 * ROLE:
 * ----------------------------------------------------------------------------
 * Provides a SINGLE, FRESH, REAL GPS fix under Zero‑Trust assumptions.
 *
 * This class is the ONLY component allowed to:
 *  - Interact directly with Android LocationManager
 *  - Request GPS-level location fixes
 *  - Reject cached or stale location data
 *
 * ============================================================================
 * SECURITY DESIGN PRINCIPLES
 * ============================================================================
 *
 * ✅ NO cached locations (NO getLastKnownLocation)
 * ✅ NO fused provider shortcuts
 * ✅ Forced GPS hardware interaction
 * ✅ Exactly ONE location per request
 * ✅ Deterministic: either success or explicit failure
 *
 * ============================================================================
 * CRITICAL GUARANTEES
 * ============================================================================
 *
 * ✅ Always attempts fresh fix from GPS chip
 * ✅ No reuse of previous session location
 * ✅ Single callback → single result (write-once semantics)
 * ✅ No blocking threads (fully coroutine-based)
 * ✅ Safe cancellation handling
 * ✅ No ghost timeout execution
 *
 * ============================================================================
 */
class LocationSource(
    private val context: Context
) {

    /**
     * Requests a SINGLE fresh GPS fix.
     */
    @SuppressLint("MissingPermission")
    suspend fun awaitFreshLocation(
        timeoutMs: Long
    ): LocationSnapshot? {

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE)
                    as? LocationManager
                ?: return null

        return suspendCancellableCoroutine { continuation ->

            var delivered = false // ✅ single-delivery guard

            val handler = Handler(context.mainLooper)

            val listener = object : LocationListener {

                override fun onLocationChanged(location: Location) {

                    if (!delivered) {
                        delivered = true

                        locationManager.removeUpdates(this)
                        handler.removeCallbacksAndMessages(null) // ✅ prevent timeout firing later

                        val snapshot = buildSnapshot(location)

                        if (continuation.isActive) {
                            continuation.resume(snapshot)
                        }
                    }
                }

                override fun onProviderDisabled(provider: String) {

                    if (!delivered) {
                        delivered = true

                        locationManager.removeUpdates(this)
                        handler.removeCallbacksAndMessages(null)

                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                override fun onProviderEnabled(provider: String) {}

                /**
                 * Deprecated but required by API
                 */
                @Suppress("DEPRECATION")
                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?
                ) {
                    // No logic required
                }
            }

            /* =============================================================
             * 🔐 FORCE REAL GPS FIX
             * ============================================================= */
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener
            )

            /* =============================================================
             * ⏱️ TIMEOUT HANDLER
             * ============================================================= */
            val timeoutRunnable = Runnable {

                if (!delivered) {
                    delivered = true

                    locationManager.removeUpdates(listener)

                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            handler.postDelayed(timeoutRunnable, timeoutMs)

            /* =============================================================
             * 🔄 CANCELLATION SAFETY
             * ============================================================= */
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    /* =========================================================================
     * SNAPSHOT BUILDER
     * ========================================================================= */

    private fun buildSnapshot(location: Location): LocationSnapshot {

        @Suppress("DEPRECATION")
        val isMock = location.isFromMockProvider

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