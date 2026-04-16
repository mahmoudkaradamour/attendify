package com.mahmoud.attendify.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceProfile(
    val cpuCores: Int,
    val memoryClassMB: Int,
    val abi: String,
    val isLowEnd: Boolean
)

object DeviceProfiler {

    fun profile(context: Context): DeviceProfile {
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = am.memoryClass
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        val isLowEnd =
            cores <= 4 ||
                    memory <= 192 ||
                    !abi.contains("arm64")

        return DeviceProfile(
            cpuCores = cores,
            memoryClassMB = memory,
            abi = abi,
            isLowEnd = isLowEnd
        )
    }
}