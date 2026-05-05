package com.mahmoud.attendify.orchestration.context

import java.util.UUID

/**
 * SignedPhysicalRealitySnapshot
 *
 * Cryptographically signed, single‑use snapshot.
 */
data class SignedPhysicalRealitySnapshot(
    val snapshotId: UUID,
    val timestampMillis: Long,
    val payload: PhysicalRealitySnapshot,

    /* Phase 3.6 additions */
    val signature: ByteArray,
    val certificateChain: List<ByteArray>

)