package com.mahmoud.attendify.system.location

/**
 * NetworkContext
 *
 * Represents the connectivity context at the moment
 * of attendance attempt.
 *
 * This is CONTEXT, not TRUST.
 */
enum class NetworkContext {
    WIFI,
    CELLULAR,
    OFFLINE
}