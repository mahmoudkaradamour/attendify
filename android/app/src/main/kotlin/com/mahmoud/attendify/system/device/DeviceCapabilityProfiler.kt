package com.mahmoud.attendify.system.device

import android.content.Context
import com.mahmoud.attendify.device.DeviceProfiler

/**
 * DeviceCapabilityProfiler
 *
 * ============================================================================
 * ROLE (Architectural Responsibility):
 * ============================================================================
 * Produces a **RUNTIME-ADAPTIVE capability profile**
 * that determines HOW the system should behave on this device.
 *
 * This profiler answers:
 * ----------------------
 * "Given this hardware, what is the safest and most reliable
 * way to run biometric processing?"
 *
 * ============================================================================
 * CORE DESIGN RULE:
 * ============================================================================
 * ❌ NO OEM checks
 * ❌ NO brand assumptions
 * ✅ Behavior derived ONLY from capabilities
 */
object DeviceCapabilityProfiler {

    fun profile(context: Context): DeviceCapabilityProfile {

        val staticHw =
            DeviceProfiler.profile(context)

        /**
         * FPS determination.
         *
         * Why this matters:
         * -----------------
         * - Liveness depends on temporal resolution
         * - More FPS ≠ better on weak devices
         */
        val maxStableFps =
            when {
                staticHw.isLowEnd -> 12
                staticHw.memoryClassMB <= 256 -> 15
                else -> 24
            }

        /**
         * Image quality confidence score.
         *
         * Represents how much we trust
         * camera output for texture-sensitive models.
         */
        val imageQualityScore =
            when {
                staticHw.isLowEnd -> 0.72f
                staticHw.memoryClassMB <= 256 -> 0.80f
                else -> 0.92f
            }

        return DeviceCapabilityProfile(
            maxStableFps = maxStableFps,
            supportsHighFrequencyLiveness = maxStableFps >= 18,
            imageQualityScore = imageQualityScore
        )
    }
}

/**
 * DeviceCapabilityProfile
 *
 * Represents **adaptive behavioral limits** of the device.
 *
 * This object directly influences:
 *  - Liveness strategy (blink vs head movement)
 *  - ML thresholds
 *  - Camera frame pacing
 */
data class DeviceCapabilityProfile(
    val maxStableFps: Int,
    val supportsHighFrequencyLiveness: Boolean,
    val imageQualityScore: Float
)