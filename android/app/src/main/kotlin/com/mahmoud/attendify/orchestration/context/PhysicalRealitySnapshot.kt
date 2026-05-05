package com.mahmoud.attendify.orchestration.context

import android.graphics.Bitmap
import com.mahmoud.attendify.system.time.TimeSnapshot
import com.mahmoud.attendify.system.location.LocationEvidence

/**
 * PhysicalRealitySnapshot
 *
 * Immutable, atomic representation of physical reality at ONE moment.
 *
 * GUARANTEES:
 * - Frame, time, and location belong to the same attempt
 * - No partial construction
 * - No mutation after creation
 */
data class PhysicalRealitySnapshot(
    val frozenFrame: Bitmap,
    val timeSnapshot: TimeSnapshot,
    val locationEvidence: LocationEvidence
)