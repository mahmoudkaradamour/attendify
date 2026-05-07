package com.mahmoud.attendify.security.boundary

import java.util.concurrent.atomic.AtomicLong

/**
 * =============================================================================
 * 🛡 UIAccessGuard — Temporal Access Control Gate
 * =============================================================================
 *
 * -----------------------------------------------------------------------------
 * 🧠 MODEL
 * -----------------------------------------------------------------------------
 *
 * This component enforces:
 *
 *   Time-based access control over UI-triggered operations
 *
 * It follows:
 *
 *   allow(t) = (t - lastExecution) ≥ Δ
 *
 * -----------------------------------------------------------------------------
 * 📊 FLOW
 * -----------------------------------------------------------------------------
 *
 * UI Request
 *    │
 *    ▼
 * Check last timestamp
 *    │
 *    ├── Too Early → REJECT
 *    │
 *    └── OK → ALLOW
 *
 * -----------------------------------------------------------------------------
 * 🔐 SECURITY PURPOSE
 * -----------------------------------------------------------------------------
 *
 * ✅ Prevents rapid repeated calls
 * ✅ Mitigates bot/script attacks
 * ✅ Reduces backend pressure
 */
object UIAccessGuard {

    private const val MIN_INTERVAL_MS = 1500L

    private val lastExecution = AtomicLong(0)

    fun allow(): Boolean {

        val now = System.currentTimeMillis()
        val last = lastExecution.get()

        if (now - last < MIN_INTERVAL_MS) {
            return false
        }

        lastExecution.set(now)
        return true
    }
}