package com.mahmoud.attendify.system.time

import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ServerTimeClient
 *
 * Responsible for obtaining a trusted UTC timestamp from the backend
 * and compensating for network latency.
 *
 * SECURITY ROLE:
 * ---------------
 * This class is the ONLY allowed source of initial (authoritative) time.
 * It is used exclusively during the Mandatory Initial Handshake
 * to create the Genesis Anchor (Time Anchoring).
 *
 * IMPORTANT:
 * ----------
 * - Device time is NEVER trusted for initialization.
 * - NTP, system clock, or timezone are ignored here.
 * - Only backend UTC time over HTTPS is accepted.
 */
class ServerTimeClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = createDefaultClient()
) {

    /**
     * Fetches backend UTC time and applies
     * round-trip network latency compensation.
     *
     * Algorithm (recommended by Security Architect):
     * ----------------------------------------------
     * T1 = elapsedRealtime() just before request is sent
     * T2 = elapsedRealtime() immediately after response is received
     *
     * RTT = T2 - T1
     * AdjustedServerTime = ServerUTC + (RTT / 2)
     *
     * This approximates the server time at the exact
     * instant the response reached the device.
     *
     * @return latency-compensated server UTC time (milliseconds)
     *
     * @throws IllegalStateException if server response is invalid
     */
    fun fetchAnchoredUtcTime(): Long {

        // ─────────────────────────────────────────────
        // Capture monotonic time BEFORE network request
        // ─────────────────────────────────────────────
        val t1 = SystemClock.elapsedRealtime()

        val request = Request.Builder()
            .url("$baseUrl/api/time/utc")
            .get()
            .build()

        // Blocking call is intentional here.
        // This method is executed during initialization only,
        // never during real-time attendance flow.
        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Failed to fetch server UTC time (HTTP ${response.code})"
                )
            }

            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty server time response")

            val json = JSONObject(responseBody)

            if (!json.has("utcMillis")) {
                throw IllegalStateException("Missing 'utcMillis' in response")
            }

            val serverUtcMillis = json.getLong("utcMillis")

            // ──────────────────────────────────────────
            // Capture monotonic time AFTER response
            // ──────────────────────────────────────────
            val t2 = SystemClock.elapsedRealtime()

            val roundTripTime = t2 - t1

            // ──────────────────────────────────────────
            // Apply latency compensation (RTT / 2)
            // ──────────────────────────────────────────
            val compensatedUtcTime =
                serverUtcMillis + (roundTripTime / 2)

            return compensatedUtcTime
        }
    }

    companion object {

        /**
         * Creates a hardened OkHttp client for time handshake.
         *
         * Notes:
         * ------
         * - Short timeouts: initialization must fail fast
         * - No retries: repeated attempts are handled at higher level
         * - SSL pinning SHOULD be added in production
         */
        private fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}