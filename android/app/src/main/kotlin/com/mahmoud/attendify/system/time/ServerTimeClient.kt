package com.mahmoud.attendify.system.time

import android.os.SystemClock
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ServerTimeClient
 *
 * PURPOSE:
 * --------
 * This class is the SINGLE authoritative source
 * of trusted (server‑anchored) UTC time.
 *
 * It is used ONLY for:
 * - Initial time anchoring (Genesis Anchor)
 * - Re‑anchoring when required by policy
 *
 * SECURITY GUARANTEES:
 * -------------------
 * - Device time is NEVER trusted
 * - RTT is bounded to defeat packet delay attacks
 * - SSL Pinning is supported and ready for production
 *
 * DESIGN NOTE:
 * ------------
 * This class supports two modes:
 *
 * 1. Development mode:
 *    - No SSL pinning
 *    - Used before domain / server exists
 *
 * 2. Production mode:
 *    - Strict SSL pinning enabled
 *    - Zero tolerance for MITM
 *
 * Switching modes does NOT require code changes,
 * only configuration changes.
 */
class ServerTimeClient(
    private val baseUrl: String
) {

    /**
     * OkHttp client is selected at runtime
     * based on security configuration.
     *
     * This avoids:
     * - fake pins in development
     * - code rewrites before production
     */
    private val httpClient: OkHttpClient =
        if (NetworkSecurityConfig.ENABLE_SSL_PINNING) {
            createPinnedClient()
        } else {
            createDevelopmentClient()
        }

    /**
     * Fetches trusted UTC time from backend,
     * compensating for network latency.
     *
     * ALGORITHM:
     * ----------
     * T1 = elapsedRealtime before request
     * T2 = elapsedRealtime after response
     *
     * RTT = T2 - T1
     * AdjustedTime = ServerUTC + (RTT / 2)
     *
     * This approximates server time at response arrival
     * while remaining resistant to time tampering.
     */
    fun fetchAnchoredUtcTime(): Long {

        // Capture monotonic time before network call
        val elapsedBefore = SystemClock.elapsedRealtime()

        val request = Request.Builder()
            .url("$baseUrl/api/time/utc")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Time API failed with HTTP ${response.code}"
                )
            }

            val body = response.body?.string()
                ?: throw IllegalStateException("Empty server time response")

            val json = JSONObject(body)

            if (!json.has("utcMillis")) {
                throw IllegalStateException("Missing utcMillis field")
            }

            val serverUtcMillis = json.getLong("utcMillis")

            // Capture monotonic time after response
            val elapsedAfter = SystemClock.elapsedRealtime()

            val rtt = elapsedAfter - elapsedBefore

            /**
             * RTT BOUNDING
             *
             * This defeats:
             * - packet hold attacks
             * - artificial delay injection
             */
            if (rtt > 5_000L) {
                throw SecurityException(
                    "Excessive RTT ($rtt ms) — possible MITM or network tampering"
                )
            }

            return serverUtcMillis + (rtt / 2)
        }
    }

    /* ==================================================
     * NETWORK CLIENT BUILDERS
     * ================================================== */

    /**
     * Production client with STRICT SSL PINNING.
     *
     * Enabled only when:
     * - Domain exists
     * - Real certificates exist
     * - Public key pins are configured
     */
    private fun createPinnedClient(): OkHttpClient {

        val certificatePinner = CertificatePinner.Builder().apply {
            NetworkConstants.TIME_API_PINS.forEach { pin ->
                add(NetworkConstants.TIME_API_HOST, pin)
            }
        }.build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Development client WITHOUT SSL pinning.
     *
     * This is REQUIRED because:
     * - No domain exists yet
     * - No production certificate exists
     * - SSL pinning cannot be simulated safely
     *
     * IMPORTANT:
     * ----------
     * This does NOT weaken the architecture.
     * It preserves correctness until real TLS is available.
     */
    private fun createDevelopmentClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}