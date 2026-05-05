package com.mahmoud.attendify.config

import com.mahmoud.attendify.device.StaticHardwareProfile

/**
 * AdaptiveConfig
 *
 * ============================================================================
 * ROLE:
 * ============================================================================
 * Encapsulates performance-related configuration derived
 * from STATIC hardware facts.
 *
 * NOTE:
 * - This does NOT affect security.
 * - This is deferred by design in Phase 1.
 */
data class AdaptiveConfig(
    val processingFps: Int,
    val maxInferencePerSecond: Int,
    val enableLiveness: Boolean
)

/**
 * AdaptiveConfigResolver
 *
 * ============================================================================
 * ROLE:
 * ============================================================================
 * Maps immutable hardware facts → performance knobs.
 *
 * Intentionally NOT used yet.
 */
object AdaptiveConfigResolver {

    fun resolve(profile: StaticHardwareProfile): AdaptiveConfig {
        return if (profile.isLowEnd) {
            AdaptiveConfig(
                processingFps = 4,
                maxInferencePerSecond = 1,
                enableLiveness = false
            )
        } else {
            AdaptiveConfig(
                processingFps = 8,
                maxInferencePerSecond = 2,
                enableLiveness = true
            )
        }
    }
}