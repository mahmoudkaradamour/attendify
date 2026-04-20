package com.mahmoud.attendify.fas.policy

object PolicyResolution {

    fun resolve(
        employeePolicy: FASPolicy?,
        groupPolicy: FASPolicy?,
        orgPolicy: FASPolicy?
    ): FASPolicy {

        return employeePolicy
            ?: groupPolicy
            ?: orgPolicy
            ?: FASPolicy(
                enabled = true,
                modelId = "minifasnet_v2_80x80_default",
                threshold = 0.85f,
                useGpu = false
            )
    }
}