package com.mahmoud.attendify.config

import com.mahmoud.attendify.device.DeviceProfile

data class AdaptiveConfig(
    val processingFps: Int,
    val maxInferencePerSecond: Int,
    val enableLiveness: Boolean
)

object AdaptiveConfigResolver {

    fun resolve(profile: DeviceProfile): AdaptiveConfig {
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