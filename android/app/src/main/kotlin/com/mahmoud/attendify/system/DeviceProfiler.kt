package com.mahmoud.attendify.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProfiler {

    enum class DeviceClass {
        WEAK, STANDARD, STRONG
    }

    fun classify(context: Context): DeviceClass {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamGB = memInfo.totalMem / (1024.0 * 1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            totalRamGB < 2 || cores <= 2 -> DeviceClass.WEAK
            totalRamGB < 4 -> DeviceClass.STANDARD
            else -> DeviceClass.STRONG
        }
    }
}