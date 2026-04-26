package com.mahmoud.attendify.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * DeviceProfiler
 *
 * ============================================================================
 * ROLE (Architectural Responsibility):
 * ============================================================================
 * Collects **STATIC, OBJECTIVE hardware facts** about the device.
 *
 * These are facts that:
 *  - Do NOT change at runtime
 *  - Do NOT depend on user behavior
 *  - Do NOT depend on camera, ML or sensors
 *
 * This profiler answers:
 * ----------------------
 * "What kind of hardware is this device, in absolute terms?"
 *
 * ============================================================================
 * IMPORTANT RULE:
 * ============================================================================
 * This class MUST NOT:
 *  ❌ Decide application behavior
 *  ❌ Tune thresholds
 *  ❌ Contain any ML or camera logic
 *
 * It is a facts provider ONLY.
 */
object DeviceProfiler {

    /**
     * profile
     *
     * Collects immutable hardware-level characteristics.
     */
    fun profile(context: Context): StaticHardwareProfile {

        val cpuCores =
            Runtime.getRuntime().availableProcessors()

        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE)
                    as ActivityManager

        val memoryClassMB =
            activityManager.memoryClass

        val abi =
            Build.SUPPORTED_ABIS.firstOrNull()
                ?: "unknown"

        /**
         * Conservative low-end classification.
         *
         * Rationale:
         * ----------
         * - <= 4 cores          → limited parallelism
         * - <= 192 MB heap      → aggressive GC risk
         * - non-arm64 ABI       → weaker ML performance
         */
        val isLowEnd =
            cpuCores <= 4 ||
                    memoryClassMB <= 192 ||
                    !abi.contains("arm64")

        return StaticHardwareProfile(
            cpuCores = cpuCores,
            memoryClassMB = memoryClassMB,
            abi = abi,
            isLowEnd = isLowEnd
        )
    }
}

/**
 * StaticHardwareProfile
 *
 * Immutable snapshot of device hardware facts.
 *
 * This object is safe to:
 *  - Cache
 *  - Log
 *  - Send to backend (if needed)
 */
data class StaticHardwareProfile(
    val cpuCores: Int,
    val memoryClassMB: Int,
    val abi: String,
    val isLowEnd: Boolean
)