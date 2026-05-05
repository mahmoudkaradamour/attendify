package com.mahmoud.attendify.security

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ReplayProtectionGuard
 *
 * Prevents snapshot replay attacks
 * by enforcing single‑use snapshot IDs.
 */
object ReplayProtectionGuard {

    private val usedSnapshots =
        ConcurrentHashMap<UUID, Boolean>()

    fun registerOrReject(snapshotId: UUID): Boolean {
        return usedSnapshots.putIfAbsent(snapshotId, true) == null
    }
}