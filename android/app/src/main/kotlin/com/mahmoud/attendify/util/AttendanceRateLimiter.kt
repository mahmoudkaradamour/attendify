package com.mahmoud.attendify.util

import java.util.concurrent.atomic.AtomicLong

/**
 * =============================================================================
 * 🚦 AttendanceRateLimiter
 * =============================================================================
 *
 * Prevents:
 * ✅ rapid repeated calls
 * ✅ CPU flooding
 * ✅ UI spamming
 */
object AttendanceRateLimiter {

    private const val MIN_INTERVAL_MS = 1500L

    private val lastTime = AtomicLong(0)

    fun allow(): Boolean {
        val now = System.currentTimeMillis()
        val prev = lastTime.get()

        if (now - prev < MIN_INTERVAL_MS) {
            return false
        }

        lastTime.set(now)
        return true
    }
}