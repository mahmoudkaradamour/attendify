package com.mahmoud.attendify.fas.policy

/**
 * PolicyResolution
 *
 * ✅ Responsible ONLY for resolving which policy applies.
 * ✅ Does NOT impose model thresholds.
 * ✅ Leaves threshold decisions to the model itself.
 */
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
                // ✅ Default model is still V2 (safe, fast, proven)
                modelId = "minifasnet_v2_80x80_default",
                // ✅ IMPORTANT: let the model decide its own threshold
                threshold = null,
                useGpu = false
            )
    }
}