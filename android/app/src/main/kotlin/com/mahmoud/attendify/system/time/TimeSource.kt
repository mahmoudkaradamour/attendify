package com.mahmoud.attendify.system.time

import android.os.Build
import android.os.SystemClock
import java.util.TimeZone
import java.util.UUID

object TimeSource {

    fun snapshot(): TimeSnapshot {
        return TimeSnapshot(
            wallClockMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            uptimeMillis = SystemClock.uptimeMillis(),
            bootId = resolveBootId(),
            timeZoneId = TimeZone.getDefault().id
        )
    }

    /**
     * Generates a stable boot identifier.
     *
     * NOTE:
     * This will change after every device reboot.
     */
    private fun resolveBootId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            SystemClock.elapsedRealtime().toString()
        } else {
            UUID.randomUUID().toString()
        }
    }
}
