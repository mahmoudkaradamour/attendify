package com.mahmoud.attendify.system.time

/**
 * NetworkConstants
 *
 * Centralized network security constants for SSL pinning.
 *
 * IMPORTANT:
 * ----------
 * - These values are used ONLY when SSL pinning is enabled.
 * - In development mode, this object is NOT enforced.
 * - Pins here are placeholders until a real production
 *   domain and TLS certificate are available.
 *
 * Production transition requires:
 * 1) Real domain
 * 2) Real TLS certificate
 * 3) Real public key pins
 * 4) ENABLE_SSL_PINNING = true
 */
object NetworkConstants {

    /**
     * Backend hostname (NO protocol, NO path)
     */
    const val TIME_API_HOST = "api.attendify.com"

    /**
     * SHA-256 SPKI pins for the backend TLS certificate.
     *
     * Example generation:
     * openssl s_client -connect api.attendify.com:443 -servername api.attendify.com \
     * | openssl x509 -pubkey -noout \
     * | openssl pkey -pubin -outform der \
     * | openssl dgst -sha256 -binary \
     * | base64
     */
    val TIME_API_PINS = listOf(
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    )
}