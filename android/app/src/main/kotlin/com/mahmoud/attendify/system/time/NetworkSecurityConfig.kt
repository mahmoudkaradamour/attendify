package com.mahmoud.attendify.system.time

/**
 * NetworkSecurityConfig
 *
 * Controls whether SSL pinning is enforced.
 * This should be ENABLED in production only.
 */
object NetworkSecurityConfig {

    /**
     * Development: false
     * Production: true
     */
    const val ENABLE_SSL_PINNING = false
}